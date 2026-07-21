package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase.Command;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração do convite de usuário contra PostgreSQL 18 real (Testcontainers):
 * persistência + unicidade e autorização do endpoint (403 sem permissão, 201 com).
 */
@SpringBootTest
@Testcontainers
class InviteUserIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired InviteUserUseCase inviteUser;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void persistsInvitedUserAndTokenThenRejectsDuplicate() {
        var command = new Command(UUID.randomUUID(), UUID.randomUUID(), "it-user@example.com", "IT User");

        var result = inviteUser.handle(command);

        assertThat(result.status()).isEqualTo("INVITED");
        assertThat(count("security_user", "normalized_email = 'it-user@example.com' AND status = 'INVITED'")).isEqualTo(1);
        assertThat(count("account_token", "user_id = '" + result.userId() + "' AND token_type = 'INVITATION'")).isEqualTo(1);

        assertThatThrownBy(() -> inviteUser.handle(command)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void endpointDeniesWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/security/users")
                        .with(authentication(principal(Set.of())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"denied@example.com\",\"displayName\":\"Denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        assertThat(count("security_user", "normalized_email = 'denied@example.com'")).isZero();
    }

    @Test
    void endpointInvitesWithPermission() throws Exception {
        mockMvc.perform(post("/api/v1/security/users")
                        .with(authentication(principal(Set.of("security.user.invite"))))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"allowed@example.com\",\"displayName\":\"Allowed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.email").value("allowed@example.com"));

        assertThat(count("security_user", "normalized_email = 'allowed@example.com' AND status = 'INVITED'")).isEqualTo(1);
    }

    private Authentication principal(Set<String> permissions) {
        var securityPrincipal = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "Admin", permissions);
        return new UsernamePasswordAuthenticationToken(securityPrincipal, "n/a", Set.of());
    }

    private int count(String table, String where) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Integer.class);
    }
}
