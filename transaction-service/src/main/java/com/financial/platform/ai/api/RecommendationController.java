package com.financial.platform.ai.api;

import com.financial.platform.ai.infrastructure.RecommendationJdbcRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "AI Recommendations", description = "Endpoints para consulta de recomendaciones de riesgo e IA asociadas a transacciones")
public class RecommendationController {

    private final RecommendationJdbcRepository recommendationRepository;

    public RecommendationController(RecommendationJdbcRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Consultar recomendación de IA y puntaje de riesgo para una transacción")
    public RecommendationResponse getRecommendation(@PathVariable UUID transactionId) {
        return recommendationRepository.findByTransactionId(transactionId)
            .map(RecommendationResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró recomendación de IA para la transacción: " + transactionId));
    }
}
