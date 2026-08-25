package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Alertas de segurança: listar e resolver (DEB-INT-003).
 *
 * <p><strong>Este IT existe porque o endpoint de resolver nunca funcionou e ninguém sabia.</strong> O
 * {@code findById} do repositório filtrava por {@code brewery_id = :brewery} e o método nem recebia a
 * cervejaria, então toda tentativa de mudar o status de um alerta terminava em 500. O caminho não tinha
 * teste nenhum — nem de unidade, nem de integração —, e a varredura da {@code BoundParametersTest} foi
 * quem o encontrou.
 *
 * <p>A lição é a mesma que o outbox de webhooks deu no mesmo dia: <strong>SQL só falha quando roda</strong>,
 * e o SQL que ninguém executa num teste é exatamente onde o parâmetro esquecido se esconde.
 */
@SpringBootTest
@Testcontainers
class SecurityAlertIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired SecurityAlertRepository alerts;
    @Autowired JdbcClient jdbc;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("RESOLVER UM ALERTA FUNCIONA — e o status muda no banco")
    void resolverUmAlertaFunciona() throws Exception {
        var brewery = cervejaria("DONA");
        var alertId = alerts.create(brewery, usuario(), "BRUTE_FORCE", "HIGH",
                Map.of("tentativas", 12));

        mockMvc.perform(patch("/api/v1/security/alerts/" + alertId)
                        .with(authentication(quem(brewery, "security.alert.manage"))).with(csrf())
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        // No banco, e não no que a resposta diz: o 200 de um endpoint `void` não prova gravação nenhuma.
        assertThat(statusOf(alertId)).isEqualTo("RESOLVED");
        assertThat(resolvedAtOf(alertId)).as("resolver carimba quando").isNotNull();
    }

    @Test
    @DisplayName("alerta de outra cervejaria responde como alerta que não existe")
    void alertaDeOutraCervejariaNaoSeResolve() throws Exception {
        var dona = cervejaria("DONA");
        var forasteira = cervejaria("FORA");
        var alertId = alerts.create(dona, usuario(), "BRUTE_FORCE", "HIGH", Map.of());

        // Mesma recusa que um id sorteado: distinguir as duas contaria a quem tem o identificador que o
        // alerta existe em algum lugar.
        mockMvc.perform(patch("/api/v1/security/alerts/" + alertId)
                        .with(authentication(quem(forasteira, "security.alert.manage"))).with(csrf())
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/security/alerts/" + UUID.randomUUID())
                        .with(authentication(quem(forasteira, "security.alert.manage"))).with(csrf())
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());

        // E o contraponto que impede o teste de passar por acidente: o alerta continua aberto, e não
        // "resolvido em silêncio pela cervejaria errada".
        assertThat(statusOf(alertId)).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("a listagem é da cervejaria de quem pergunta")
    void aListagemEDaCervejariaDeQuemPergunta() throws Exception {
        var dona = cervejaria("DONA");
        var vizinha = cervejaria("VIZ");
        var alertId = alerts.create(dona, usuario(), "IMPOSSIBLE_TRAVEL", "MEDIUM", Map.of());

        var minha = mockMvc.perform(get("/api/v1/security/alerts")
                        .with(authentication(quem(dona, "security.alert.read"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(minha).contains(alertId.toString());

        var alheia = mockMvc.perform(get("/api/v1/security/alerts")
                        .with(authentication(quem(vizinha, "security.alert.read"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(alheia).doesNotContain(alertId.toString());
    }

    @Test
    @DisplayName("resolver tem alçada própria, separada da de ler")
    void resolverTemAlcadaPropria() throws Exception {
        var brewery = cervejaria("DONA");
        var alertId = alerts.create(brewery, usuario(), "BRUTE_FORCE", "LOW", Map.of());

        mockMvc.perform(patch("/api/v1/security/alerts/" + alertId)
                        .with(authentication(quem(brewery, "security.alert.read"))).with(csrf())
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());

        assertThat(statusOf(alertId)).isEqualTo("OPEN");
    }

    /**
     * Uma pessoa de verdade: {@code security_alert.user_id} e {@code resolved_by} têm chave estrangeira
     * para {@code security_user}, então o principal do teste precisa existir no banco — carimbar quem
     * resolveu é parte do que o endpoint faz.
     */
    private UUID usuario() {
        var id = UUID.randomUUID();
        var email = "alerta-" + id.toString().substring(0, 8) + "@brassia.test";
        jdbc.sql("INSERT INTO security_user (id, email, normalized_email, display_name, status) "
                        + "VALUES (:id, :email, :email, 'Tester', 'ACTIVE')")
                .param("id", id).param("email", email)
                .update();
        return id;
    }

    /**
     * Uma cervejaria de verdade: {@code security_alert.brewery_id} tem chave estrangeira, então um UUID
     * sorteado — o atalho usual dos ITs de isolamento — é recusado pelo banco antes de o teste começar.
     */
    private UUID cervejaria(String codigo) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO brewery (id, code, name, timezone) "
                        + "VALUES (:id, :code, :name, 'America/Sao_Paulo')")
                .param("id", id)
                .param("code", codigo + "-" + id.toString().substring(0, 8))
                .param("name", codigo)
                .update();
        return id;
    }

    private String statusOf(UUID alertId) {
        return jdbc.sql("SELECT status FROM security_alert WHERE id = :id")
                .param("id", alertId).query(String.class).single();
    }

    private Object resolvedAtOf(UUID alertId) {
        return jdbc.sql("SELECT resolved_at FROM security_alert WHERE id = :id")
                .param("id", alertId).query(java.sql.Timestamp.class).optional().orElse(null);
    }

    private Authentication quem(UUID breweryId, String... permissions) {
        var principal = new SecurityPrincipal(usuario(), breweryId, "Tester", Set.of(permissions));
        return new UsernamePasswordAuthenticationToken(principal, "n/a", Set.of());
    }
}
