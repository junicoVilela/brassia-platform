package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Confirma que o Spring Session JDBC expõe o repositório indexado por principal. */
@SpringBootTest
@Testcontainers
class SessionRepositoryProbeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired(required = false)
    FindByIndexNameSessionRepository<? extends Session> sessions;

    @Test
    void indexedSessionRepositoryIsAvailableAndFindsByPrincipal() {
        assertThat(sessions).as("FindByIndexNameSessionRepository bean").isNotNull();

        var session = sessions.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "probe-user");
        save(session);

        var found = sessions.findByPrincipalName("probe-user");
        assertThat(found).containsKey(session.getId());

        sessions.deleteById(session.getId());
        assertThat(sessions.findByPrincipalName("probe-user")).doesNotContainKey(session.getId());
    }

    @SuppressWarnings("unchecked")
    private <S extends Session> void save(Session session) {
        ((FindByIndexNameSessionRepository<S>) sessions).save((S) session);
    }
}
