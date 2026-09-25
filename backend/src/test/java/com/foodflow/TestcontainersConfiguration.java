package com.foodflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one throwaway PostgreSQL 17 container per Spring test context.
 *
 * <p>{@code @ServiceConnection} replaces spring.datasource.url/username/password with the
 * container's random port and credentials, so tests never touch the development database.
 * Spring caches the test context, so all test classes with the same configuration share
 * one container instead of starting a new one each time.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        // Same image as docker-compose.yml: tests run against the production database engine.
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
