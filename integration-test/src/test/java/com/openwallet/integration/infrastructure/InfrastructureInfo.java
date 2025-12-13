package com.openwallet.integration.infrastructure;

/**
 * Minimal view of infrastructure connection details so other helpers
 * (e.g., ServiceManager) don't need direct container references.
 */
public interface InfrastructureInfo {
    String getPostgresJdbcUrl();
    String getPostgresUsername();
    String getPostgresPassword();
    String getKafkaBootstrapServers();
    String getKeycloakBaseUrl();
    String getKeycloakRealm();
}

