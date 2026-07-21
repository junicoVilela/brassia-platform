package br.com.brew.brassia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração que sobe o contexto completo contra um PostgreSQL 18 real
 * (Testcontainers). Valida, de uma vez, que as migrations Flyway aplicam em banco
 * limpo, que o mapeamento JPA passa no {@code ddl-auto: validate} e que toda a
 * fiação de beans (segurança, sessão JDBC, auditoria, recipe) inicializa.
 */
@SpringBootTest
@Testcontainers
class ApplicationContextIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Test
    void contextLoadsAndMigrationsApply() {
        // Falha se o contexto não subir (migrations, validate ou wiring quebrados).
    }
}
