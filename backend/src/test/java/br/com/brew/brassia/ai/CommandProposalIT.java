package br.com.brew.brassia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.ai.domain.CommandProposal;
import br.com.brew.brassia.ai.domain.ProposedAction;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A decisão humana sobre uma proposta, ponta a ponta (AIA-003).
 *
 * <p><strong>O que este teste existe para provar</strong> é a assimetria que a história inteira produz: a
 * permissão exigida para confirmar é a do <em>comando proposto</em>, conferida no instante do aceite contra
 * quem está confirmando — e {@code ai.command.propose} explicitamente não serve. Isso só é verificável com o
 * contexto de segurança real, o banco real e a linha de auditoria real; nenhum teste de unidade cobre.
 *
 * <p><strong>As propostas são inseridas direto no banco, e a escolha é deliberada.</strong> Chegar a uma
 * proposta pela porta da frente exige um lote completo — insumos, receita publicada, ordem, brassagem,
 * transferência — e uma chamada ao modelo. São cerca de duzentas linhas já escritas em {@code BatchReportIT},
 * e duplicá-las aqui testaria a produção, não a decisão. O caminho de <em>propor</em> é coberto em
 * {@code CommandProposalHandlerTest} com dublês, e o que este IT cobre é o que vem depois da proposta existir:
 * quem pode transformá-la em decisão, e o que fica registrado.
 */
@SpringBootTest
@Testcontainers
class CommandProposalIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROPOSALS = "/api/v1/ai/proposals";

    /** A permissão do comando que a proposta de fechar custo pretende disparar. */
    private static final String COMANDO = "costing.cost.close";

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mockMvc;
    UUID brewery;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        brewery = breweryOfBootstrapAdmin();
    }

    /**
     * O teste central da história.
     *
     * <p>Quem pede a proposta tem {@code ai.command.propose}. Isso não é alçada para fechar custo, e passar
     * pela IA não pode convertê-la em uma — é o caminho lateral que a separação fecha.
     */
    @Test
    @DisplayName("quem só pode propor não pode confirmar: a alçada exigida é a do comando")
    void proporNaoDaDireitoDeConfirmar() throws Exception {
        var id = pendingProposal(Instant.now().plus(Duration.ofHours(6)));
        var proponente = principal(brewery, Set.of("ai.command.propose", "ai.command.read"));

        mockMvc.perform(post(PROPOSALS + "/" + id + "/acceptance").with(csrf())
                        .with(authentication(proponente))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        // E a proposta continua pendente: uma tentativa negada não consome a proposta.
        assertThat(statusOf(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("ACEITAR A PROPOSTA ABRE A NC DE VERDADE, com lote, número e prazos da política")
    void aceiteAbreNaoConformidade() throws Exception {
        // O DEB-AIA-003 fechando: a ação era a única das três que não executava ao ser aceita. As duas
        // barreiras eram a falta de vínculo com lote e a falta de origem para os três prazos.
        var lote = seedBatch();
        seedCapaPolicy();
        var id = pendingNonConformityProposal(lote);
        var confirmador = new SecurityPrincipal(UUID.randomUUID(), brewery, "Qualidade",
                Set.of("quality.nc.manage", "ai.command.read"));

        mockMvc.perform(post(PROPOSALS + "/" + id + "/acceptance").with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(confirmador, "n/a",
                                Set.of())))
                        .contentType("application/json")
                        .content("{\"note\":\"Confirmado com a qualidade.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", Matchers.is("ACCEPTED")))
                .andExpect(jsonPath("$.executedOnConfirm", Matchers.is(true)));

        var nc = jdbc.sql("""
                        SELECT code, batch_id::text AS lote, severity, description,
                               containment_due_on, investigation_due_on, verification_due_on
                        FROM quality_non_conformity WHERE brewery_id = :brewery AND batch_id = :batch
                        """)
                .param("brewery", brewery).param("batch", lote)
                .query((rs, row) -> new String[] {rs.getString("code"), rs.getString("lote"),
                        rs.getString("severity"), rs.getString("description"),
                        rs.getString("containment_due_on"), rs.getString("investigation_due_on"),
                        rs.getString("verification_due_on")})
                .list();

        assertThat(nc).as("a NC foi aberta de verdade, não só marcada como aceita").hasSize(1);
        // Numerada pelo sistema: não havia quem digitasse o código.
        assertThat(nc.get(0)[0]).matches("NC-\\d{4}-\\d{4}");
        assertThat(nc.get(0)[1]).isEqualTo(lote.toString());
        assertThat(nc.get(0)[2]).isEqualTo("MAJOR");
        // A descrição diz de onde veio: meses depois, "quem abriu isto?" tem como resposta um copiloto.
        assertThat(nc.get(0)[3]).contains("copiloto");
        // Os três prazos vieram da política da casa, e nenhum foi inventado aqui.
        assertThat(nc.get(0)[4]).isNotNull();
        assertThat(nc.get(0)[5]).isNotNull();
        assertThat(nc.get(0)[6]).isNotNull();
    }

    @Test
    @DisplayName("SEM POLÍTICA DE PRAZOS, O ACEITE FALHA — a IA não inventa prazo de contenção")
    void semPoliticaNaoAbre() throws Exception {
        // É a regra que sobreviveu ao débito: prazo de contenção é decisão da cervejaria com quem a
        // audita. Um default embutido pareceria conveniência e viraria o prazo que ninguém escolheu.
        var lote = seedBatch();
        // Limpa a política: o outro teste desta classe a semeia, e o banco é o mesmo. Sem isto, este
        // teste passaria ou falharia conforme a ordem de execução — que é a pior forma de um teste mentir.
        jdbc.sql("DELETE FROM quality_capa_policy WHERE brewery_id = :brewery")
                .param("brewery", brewery).update();
        var id = pendingNonConformityProposal(lote);
        var confirmador = new SecurityPrincipal(UUID.randomUUID(), brewery, "Qualidade",
                Set.of("quality.nc.manage", "ai.command.read"));

        mockMvc.perform(post(PROPOSALS + "/" + id + "/acceptance").with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(confirmador, "n/a",
                                Set.of())))
                        .contentType("application/json").content("{\"note\":\"ok\"}"))
                .andExpect(status().isBadRequest());

        // E a proposta continua pendente: o aceite e a execução caem juntos, então não sobra uma proposta
        // marcada como aceita sem a NC que ela afirma ter aberto.
        assertThat(statusOf(id)).isEqualTo("PENDING");
        var abertas = jdbc.sql("SELECT count(*) FROM quality_non_conformity WHERE batch_id = :batch")
                .param("batch", lote).query(Integer.class).single();
        assertThat(abertas).isZero();
    }

    @Test
    @DisplayName("com a alçada do comando, o aceite registra quem consentiu e fica auditado")
    void aceiteRegistraQuemConsentiu() throws Exception {
        var id = pendingProposal(Instant.now().plus(Duration.ofHours(6)));
        var confirmadorId = UUID.randomUUID();
        var confirmador = new SecurityPrincipal(confirmadorId, brewery, "Gerente",
                Set.of(COMANDO, "ai.command.read"));

        mockMvc.perform(post(PROPOSALS + "/" + id + "/acceptance").with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(confirmador, "n/a",
                                Set.of())))
                        .contentType("application/json")
                        .content("{\"note\":\"Conferi as parcelas do lote.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", Matchers.is("ACCEPTED")))
                .andExpect(jsonPath("$.decidedBy", Matchers.is(confirmadorId.toString())))
                .andExpect(jsonPath("$.decisionNote", Matchers.is("Conferi as parcelas do lote.")))
                .andExpect(jsonPath("$.requiredPermission", Matchers.is(COMANDO)))
                // Desde DEB-AIA-002 o aceite EXECUTA o comando na mesma transação; a rota deixou de ser
                // "onde praticar o ato" e passou a ser "onde ver o resultado".
                .andExpect(jsonPath("$.executionRoute", Matchers.is("/costing/batches")))
                .andExpect(jsonPath("$.executedOnConfirm", Matchers.is(true)));

        // A linha de auditoria é o produto da história: sem ela, "a IA fez" seria a única explicação
        // possível para o custo ter sido fechado.
        var auditoria = jdbc.sql("""
                        SELECT actor_id::text AS actor, change_summary::text AS resumo FROM audit_event
                        WHERE action = 'ai.command.accept' AND target_id = :id
                        """)
                .param("id", id.toString())
                .query((rs, row) -> new String[] {rs.getString("actor"), rs.getString("resumo")})
                .list();

        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)[0]).isEqualTo(confirmadorId.toString());
        var resumo = JSON.readTree(auditoria.get(0)[1]);
        assertThat(resumo.get("proposalAction").asText()).isEqualTo("CLOSE_BATCH_COST");
        assertThat(resumo.get("requiredPermission").asText()).isEqualTo(COMANDO);
    }

    /**
     * Dois cliques em "confirmar".
     *
     * <p>É o caso real: a tela não respondeu de imediato e a pessoa clicou de novo. O segundo aceite tem de
     * descobrir que já houve decisão, e não sobrescrevê-la — quem clicou por último acreditaria que a decisão
     * registrada é a dele.
     */
    @Test
    @DisplayName("segunda confirmação da mesma proposta é recusada com 409")
    void segundaConfirmacaoEhRecusada() throws Exception {
        var id = pendingProposal(Instant.now().plus(Duration.ofHours(6)));
        var confirmador = principal(brewery, Set.of(COMANDO, "ai.command.read"));

        mockMvc.perform(accept(id, confirmador)).andExpect(status().isOk());

        mockMvc.perform(accept(id, confirmador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", Matchers.is("proposal_not_pending")))
                .andExpect(jsonPath("$.status", Matchers.is("ACCEPTED")));
    }

    /**
     * Proposta vencida.
     *
     * <p>410 e não 409: a proposta existiu e não existe mais como oferta. Tentar de novo a mesma proposta é
     * justamente o que não se deve fazer — os fatos que a motivaram envelheceram, e o retrato antigo é
     * convincente porque parece atual.
     */
    @Test
    @DisplayName("proposta vencida não é confirmável nem por quem tem alçada")
    void vencidaNaoEhConfirmavel() throws Exception {
        var id = pendingProposal(Instant.now().minus(Duration.ofHours(1)));
        var confirmador = principal(brewery, Set.of(COMANDO, "ai.command.read"));

        mockMvc.perform(accept(id, confirmador))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", Matchers.is("proposal_expired")));

        assertThat(statusOf(id)).isEqualTo("PENDING");
    }

    /**
     * Recusar não exige a alçada do comando.
     *
     * <p>Dizer "não" a uma sugestão não altera nada no sistema. Se exigisse alçada, propostas pendentes
     * acumulariam até vencer — e uma tela cheia de pendências treina quem a lê a ignorar a tela inteira.
     */
    @Test
    @DisplayName("descartar exige só poder ver, e vale para proposta vencida também")
    void descartarExigeSoPoderVer() throws Exception {
        var vigente = pendingProposal(Instant.now().plus(Duration.ofHours(6)));
        var vencida = pendingProposal(Instant.now().minus(Duration.ofHours(1)));
        var leitor = principal(brewery, Set.of("ai.command.read"));

        mockMvc.perform(reject(vigente, leitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", Matchers.is("REJECTED")));
        mockMvc.perform(reject(vencida, leitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", Matchers.is("REJECTED")));
    }

    @Test
    @DisplayName("proposta de outra cervejaria não existe: 404, não 403")
    void propostaDeOutraCervejariaNaoExiste() throws Exception {
        var id = pendingProposal(Instant.now().plus(Duration.ofHours(6)));
        // Mesmo usuário, mesma alçada, outra cervejaria ativa. A resposta é a mesma de "não existe" —
        // distinguir as duas contaria que a proposta existe em algum lugar.
        var outra = principal(UUID.randomUUID(), Set.of(COMANDO, "ai.command.read"));

        mockMvc.perform(accept(id, outra))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", Matchers.is("unknown_proposal")));
        assertThat(statusOf(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a listagem só mostra as propostas da cervejaria e diz qual alçada falta")
    void listagemDizQualAlcadaFalta() throws Exception {
        var id = pendingProposal(Instant.now().plus(Duration.ofHours(6)));
        var semAlcada = principal(brewery, Set.of("ai.command.read"));

        var corpo = mockMvc.perform(get(PROPOSALS).with(authentication(semAlcada)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Escopo por identificador: o banco é compartilhado entre testes, então afirmar sobre a lista
        // inteira testaria a ordem dos testes e não a listagem.
        var vista = JSON.readTree(corpo).valueStream()
                .filter(node -> node.path("id").asText().equals(id.toString())).findFirst().orElseThrow();
        // Não pode confirmar, e a tela sabe dizer por quê — desabilitar o botão sem nomear a permissão
        // deixaria quem lê sem o que fazer a respeito.
        assertThat(vista.path("canConfirm").asBoolean()).isFalse();
        assertThat(vista.path("requiredPermission").asText()).isEqualTo(COMANDO);
        assertThat(vista.path("expired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("sem ai.command.read não se lista proposta nenhuma")
    void listarExigePermissao() throws Exception {
        var estranho = principal(brewery, Set.of("ai.answer.ask"));

        mockMvc.perform(get(PROPOSALS).with(authentication(estranho)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("pedir proposta para lote inexistente é 404 e não chega a gastar")
    void loteInexistenteNaoPropoe() throws Exception {
        var pedinte = principal(brewery, Set.of("ai.command.propose"));

        mockMvc.perform(post(PROPOSALS + "/batches/" + UUID.randomUUID()).with(csrf())
                        .with(authentication(pedinte)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", Matchers.is("unknown_batch")));
    }

    @Test
    @DisplayName("propor é alçada própria: quem só lê não pede proposta")
    void proporExigeAlcadaPropria() throws Exception {
        var leitor = principal(brewery, Set.of("ai.command.read"));

        mockMvc.perform(post(PROPOSALS + "/batches/" + UUID.randomUUID()).with(csrf())
                        .with(authentication(leitor)))
                .andExpect(status().isForbidden());
    }

    // --- apoio ---------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder accept(UUID id,
            Authentication who) {
        return post(PROPOSALS + "/" + id + "/acceptance").with(csrf()).with(authentication(who))
                .contentType("application/json").content("{}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reject(UUID id,
            Authentication who) {
        return post(PROPOSALS + "/" + id + "/rejection").with(csrf()).with(authentication(who))
                .contentType("application/json").content("{\"note\":\"Não se aplica.\"}");
    }

    /**
     * Insere uma proposta pendente com o prazo escolhido.
     *
     * <p>{@code expiresAt} é parâmetro em vez de derivado de {@link CommandProposal#VALIDITY} porque é o
     * único jeito de testar o vencimento sem esperar doze horas nem injetar relógio no contexto inteiro.
     */
    /**
     * Uma proposta pendente sobre um lote QUE EXISTE.
     *
     * <p>Antes de `DEB-AIA-002` o parâmetro era um UUID aleatório e passava, porque o aceite não executava
     * nada. Assim que ele passou a fechar o custo de verdade, o lote inventado virou 404 — e o teste que
     * afirmava "o aceite registra a decisão" estava, na prática, afirmando isso sobre um lote inexistente.
     * Um fixture que só era válido porque nada acontecia.
     */
    /** Lote mínimo para o custeio conseguir montar e fechar. Sem chave estrangeira para ordem ou receita. */
    private UUID seedBatch() {
        var batchId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO production_batch (id, brewery_id, order_id, code, recipe_id, recipe_version,
                        recipe_name, volume_liters, status, started_at, started_by)
                VALUES (:id, :brewery, :order, :code, :recipe, 1, 'Receita de teste', 1000, 'COMPLETED',
                        now(), :by)
                """)
                .param("id", batchId).param("brewery", brewery).param("order", UUID.randomUUID())
                .param("code", "LOTE-" + batchId.toString().substring(0, 8))
                .param("recipe", UUID.randomUUID()).param("by", UUID.randomUUID())
                .update();
        return batchId;
    }

    private UUID pendingProposal(Instant expiresAt) {
        var id = UUID.randomUUID();
        var proposedAt = expiresAt.minus(CommandProposal.VALIDITY);
        jdbc.sql("""
                INSERT INTO ai_command_proposal (id, brewery_id, action, parameters, rationale, proposed_by,
                        proposed_at, expires_at, status)
                VALUES (:id, :brewery, :action, :parameters::jsonb, :rationale, :by, :at, :expires, 'PENDING')
                """)
                .param("id", id)
                .param("brewery", brewery)
                .param("action", ProposedAction.CLOSE_BATCH_COST.name())
                .param("parameters", "{\"batchId\":\"" + seedBatch() + "\"}")
                .param("rationale", "O lote terminou e o custo segue derivado.")
                .param("by", UUID.randomUUID())
                .param("at", Timestamp.from(proposedAt))
                .param("expires", Timestamp.from(expiresAt))
                .update();
        return id;
    }

    /**
     * A política de prazos da casa (PRM-001).
     *
     * <p>Semeada porque é dela que os três prazos saem. Sem ela a abertura é recusada, e isso é regra e
     * não lacuna — prazo de contenção é decisão da cervejaria com quem a audita, não padrão técnico.
     */
    private void seedCapaPolicy() {
        for (var severidade : new String[] {"MINOR", "MAJOR", "CRITICAL"}) {
            jdbc.sql("""
                    INSERT INTO quality_capa_policy (brewery_id, severity, containment_days,
                            investigation_days, verification_days)
                    VALUES (:brewery, :severity, 2, 10, 30)
                    ON CONFLICT (brewery_id, severity) DO NOTHING
                    """)
                    .param("brewery", brewery).param("severity", severidade)
                    .update();
        }
    }

    /** Proposta de abrir NC para um lote semeado (DEB-AIA-003). */
    private UUID pendingNonConformityProposal(UUID batchId) {
        var id = UUID.randomUUID();
        var proposedAt = Instant.now();
        jdbc.sql("""
                INSERT INTO ai_command_proposal (id, brewery_id, action, parameters, rationale, proposed_by,
                        proposed_at, expires_at, status)
                VALUES (:id, :brewery, :action, :parameters::jsonb, :rationale, :by, :at, :expires, 'PENDING')
                """)
                .param("id", id)
                .param("brewery", brewery)
                .param("action", ProposedAction.OPEN_NON_CONFORMITY.name())
                .param("parameters", "{\"batchId\":\"" + batchId + "\",\"title\":\"FG fora da faixa\","
                        + "\"severity\":\"MAJOR\"}")
                .param("rationale", "FG acima do teto da especificação sem NC aberta.")
                .param("by", UUID.randomUUID())
                .param("at", Timestamp.from(proposedAt))
                .param("expires", Timestamp.from(proposedAt.plus(Duration.ofHours(6))))
                .update();
        return id;
    }

    private String statusOf(UUID id) {
        return jdbc.sql("SELECT status FROM ai_command_proposal WHERE id = :id")
                .param("id", id).query(String.class).single();
    }

    /** A cervejaria ativa do admin de bootstrap — a única que existe num banco recém-migrado. */
    private UUID breweryOfBootstrapAdmin() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        var ctx = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        return ((SecurityPrincipal) ctx.getAuthentication().getPrincipal()).requireBrewery();
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
