package com.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Java Orchestrator service.
 *
 * On startup, Spring Boot will:
 *  1. Connect to PostgreSQL (application.yml → spring.datasource)
 *  2. Start the REST API on port 8080
 *  3. Start the gRPC server on port 50052 (OrchestratorService)
 *  4. Initialize the gRPC client channel to the C++ engine on port 50051
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
