package com.example.ledgerbank.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.stream.Stream;

@ActiveProfiles("integration")
abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("ledgerbank_test")
            .withUsername("ledgerbank")
            .withPassword("ledgerbank")
            .withStartupTimeout(Duration.ofMinutes(2));

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no")
            .withStartupTimeout(Duration.ofMinutes(1));

    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine")
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        Startables.deepStart(Stream.of(POSTGRES, REDIS, RABBITMQ)).join();
    }
}
