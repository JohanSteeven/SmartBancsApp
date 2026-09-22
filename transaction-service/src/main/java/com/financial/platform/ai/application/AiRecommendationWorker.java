package com.financial.platform.ai.application;

import com.financial.platform.shared.observability.TransactionMetrics;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AiRecommendationWorker {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationWorker.class);

    private static final String NATS_SUBJECT_TRANSACTION_COMPLETED = "transaction.completed";
    private static final String NATS_SUBJECT_AI_COMPLETED = "ai.recommendation.completed";

    @Value("${ai.service.url:http://ai-service:8000/api/v1/risk-assessments}")
    private String aiServiceUrl;

    private final Connection natsConnection;
    private final RecommendationJdbcRepository recommendationRepository;
    private final RestTemplate restTemplate;
    private final TransactionMetrics metrics;

    public AiRecommendationWorker(
        @Autowired(required = false) Connection natsConnection,
        RecommendationJdbcRepository recommendationRepository,
        TransactionMetrics metrics
    ) {
        this.natsConnection = natsConnection;
        this.recommendationRepository = recommendationRepository;
        this.restTemplate = new RestTemplate();
        this.metrics = metrics;
    }

    @PostConstruct
    public void startListener() {
        if (natsConnection == null) {
            log.warn("Conexión NATS nula. AiRecommendationWorker no iniciará suscripción.");
            return;
        }

        try {
            Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
                String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                log.info("AiRecommendationWorker recibió evento de NATS: {}", payload);
                processTransactionEvent(payload);
            });
            dispatcher.subscribe(NATS_SUBJECT_TRANSACTION_COMPLETED);
            log.info("AiRecommendationWorker suscrito exitosamente al tema NATS '{}'", NATS_SUBJECT_TRANSACTION_COMPLETED);
        } catch (Exception e) {
            log.error("Error al suscribir AiRecommendationWorker a NATS: {}", e.getMessage(), e);
        }
    }

    public void processTransactionEvent(String payloadJson) {
        try {
            String txIdStr = extractJsonField(payloadJson, "transactionId");
            String sourceStr = extractJsonField(payloadJson, "sourceAccountId");
            String destStr = extractJsonField(payloadJson, "destinationAccountId");
            String amountStr = extractJsonField(payloadJson, "amount");
            String currencyStr = extractJsonField(payloadJson, "currency");

            UUID transactionId = UUID.fromString(txIdStr);

            // Invocación HTTP al servicio de IA en Python FastAPI
            Map<String, Object> aiRequest = new HashMap<>();
            aiRequest.put("transaction_id", txIdStr);
            aiRequest.put("source_account_id", sourceStr);
            aiRequest.put("destination_account_id", destStr);
            aiRequest.put("amount", Double.parseDouble(amountStr));
            aiRequest.put("currency", currencyStr);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(aiRequest, headers);

            String targetUrl = resolveAiServiceUrl();
            log.info("Invocando AI Service en {} para transacción {}", targetUrl, transactionId);
            Map<?, ?> response = restTemplate.postForObject(targetUrl, requestEntity, Map.class);

            if (response != null) {
                Object riskScoreObj = response.get("risk_score");
                BigDecimal riskScore = new BigDecimal(riskScoreObj.toString());
                String recommendation = (String) response.get("recommendation");
                String modelVersion = (String) response.get("model_version");

                recommendationRepository.save(transactionId, riskScore, recommendation, modelVersion);
                metrics.registerAiEvaluation(recommendation, modelVersion);
                log.info("Recomendación de IA guardada en PostgreSQL: TransactionId={}, Score={}, Rec={}, Model={}",
                    transactionId, riskScore, recommendation, modelVersion);

                // Publicación de evento de cierre en NATS
                if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
                    String completionPayload = String.format(
                        "{\"transactionId\":\"%s\",\"riskScore\":%s,\"recommendation\":\"%s\",\"modelVersion\":\"%s\"}",
                        transactionId, riskScore.toPlainString(), recommendation, modelVersion
                    );
                    natsConnection.publish(NATS_SUBJECT_AI_COMPLETED, completionPayload.getBytes(StandardCharsets.UTF_8));
                    log.info("Evento final publicado en NATS subject '{}'", NATS_SUBJECT_AI_COMPLETED);
                }
            }
        } catch (Exception e) {
            log.error("Error al procesar evaluación de riesgo de IA para evento: {}", e.getMessage(), e);
        }
    }

    private String resolveAiServiceUrl() {
        if (aiServiceUrl.contains("ai-service:8000") && !isRunningInDocker()) {
            return aiServiceUrl.replace("ai-service:8000", "localhost:8000");
        }
        return aiServiceUrl;
    }

    private boolean isRunningInDocker() {
        return new java.io.File("/.dockerenv").exists();
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) return "";
        int start = idx + key.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
            start++;
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }
}
