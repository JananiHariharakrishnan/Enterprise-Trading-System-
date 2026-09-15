package com.tradingsystem.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Simple Kafka connection debugger that verifies connectivity on startup.
 */
@Component
public class KafkaConnectionDebugger {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConnectionDebugger.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyKafkaConnection() {
        logger.info("Verifying Kafka connection to: {}", bootstrapServers);

        try {
            Map<String, Object> props = new HashMap<>();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
            props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);

            try (AdminClient adminClient = AdminClient.create(props)) {
                var clusterId = adminClient.describeCluster().clusterId().get(10, TimeUnit.SECONDS);
                logger.info("✓ SUCCESS: Kafka connection verified - Cluster ID: {}", clusterId);
            }
        } catch (TimeoutException e) {
            logger.error("✗ FAILED: Kafka connection timeout - Broker at {} is not reachable within 10 seconds", bootstrapServers);
            logger.error("  Check: 1) Is Kafka broker running? 2) Is firewall blocking? 3) Is IP:Port correct?");
        } catch (ExecutionException e) {
            logger.error("✗ FAILED: Kafka connection error - {}", e.getCause().getMessage());
        } catch (InterruptedException e) {
            logger.error("✗ FAILED: Kafka connection interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("✗ FAILED: Kafka connection error - {}", e.getMessage());
        }
    }
}


