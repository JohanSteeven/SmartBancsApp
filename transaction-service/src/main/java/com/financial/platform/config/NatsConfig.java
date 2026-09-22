package com.financial.platform.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class NatsConfig {

    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Bean
    public Connection natsConnection() {
        try {
            log.info("Connecting to NATS JetStream server at {}", natsUrl);
            Options options = new Options.Builder()
                .server(natsUrl)
                .maxReconnects(-1)
                .connectionTimeout(java.time.Duration.ofSeconds(5))
                .build();
            return Nats.connect(options);
        } catch (IOException | InterruptedException e) {
            log.warn("Could not connect to NATS server at {}: {}", natsUrl, e.getMessage());
            return null;
        }
    }

    @Bean
    public JetStream jetStream(Connection connection) {
        if (connection == null) {
            log.warn("NATS connection is null. JetStream bean will be null.");
            return null;
        }
        try {
            return connection.jetStream();
        } catch (IOException e) {
            log.error("Failed to initialize JetStream context: {}", e.getMessage());
            return null;
        }
    }
}
