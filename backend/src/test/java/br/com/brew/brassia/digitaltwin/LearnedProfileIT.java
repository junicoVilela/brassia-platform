package br.com.brew.brassia.digitaltwin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.digitaltwin.application.port.outbound.LearnedProfileRepository;
import br.com.brew.brassia.digitaltwin.domain.Confidence;
import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import br.com.brew.brassia.digitaltwin.domain.ProfileMetric;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O perfil aprendido de ponta a ponta (DTW-001).
 *
 * <p>O que só aparece aqui: o perfil <strong>sobrevive à ida e volta do banco com a amostra intacta</strong>
 * — é ela que torna o número reproduzível —, os `CHECK` de coerência entre média e confiança valem de
 * verdade, e o versionamento não sobrescreve.
 *
 * <p><strong>O que este IT não cobre:</strong> o cálculo a partir de lotes reais. Montar um lote produzido
 * e transferido são ~200 linhas já escritas em `BatchReportIT`, e duplicá-las aqui testaria a produção e
 * não o aprendizado. Os perfis são gravados pelo repositório e o cálculo é coberto por unidade com dublês
 * das mesmas consultas publicadas, cujo comportamento real é testado no IT do módulo que as publica — a
 * mesma decisão declarada em `BatchAssessmentIT` na Sprint 14.
 */
@SpringBootTest
@Testcontainers
class LearnedProfileIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROFILES = "/api/v1/digital-twin/profiles";
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    @Autowired WebApplicationContext context;
    @Autowired LearnedProfileRepository repository;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("o perfil sobrevive ao banco com a amostra intacta — é ela que torna o número reproduzível")
    void amostraSobreviveAoBanco() throws Exception {
        var session = login();
        var brewery = breweryOf(session);
        var recipe = UUID.randomUUID();
        var lotes = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        repository.insert(LearnedProfile.compute(brewery, recipe, 1, Map.of(
                ProfileMetric.VOLUME_YIELD_PERCENT, valores("92", "94", "90")),
                lotes, UUID.randomUUID(), AGORA));

        var body = read(mockMvc.perform(get(PROFILES + "/" + recipe).session(session))
                .andExpect(status().isOk()));

        assertThat(body.get("observedBatchIds")).hasSize(3);
        assertThat(body.get("version").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a média nunca viaja sozinha: amostra, faixa e confiança vêm junto")
    void mediaNaoViajaSozinha() throws Exception {
        // É o critério da história no contrato: quem consome a API não consegue pegar o número sem ver
        // sobre quantas observações ele foi construído.
        var session = login();
        var brewery = breweryOf(session);
        var recipe = UUID.randomUUID();
        repository.insert(LearnedProfile.compute(brewery, recipe, 1, Map.of(
                ProfileMetric.VOLUME_YIELD_PERCENT, valores("92", "94", "90")),
                List.of(UUID.randomUUID()), UUID.randomUUID(), AGORA));

        var estimativa = estimativaDe(read(mockMvc.perform(get(PROFILES + "/" + recipe).session(session))
                .andExpect(status().isOk())), "VOLUME_YIELD_PERCENT");

        assertThat(estimativa.get("mean").decimalValue()).isEqualByComparingTo("92");
        assertThat(estimativa.get("sampleSize").asInt()).isEqualTo(3);
        assertThat(estimativa.get("confidence").asText()).isEqualTo("LOW");
        assertThat(estimativa.get("lowerBound").decimalValue())
                .isLessThan(estimativa.get("upperBound").decimalValue());
    }

    @Test
    @DisplayName("métrica sem amostra suficiente é gravada como INSUFFICIENT, não some")
    void insuficientePersisteComoAusenciaDeclarada() throws Exception {
        // Sem a linha, quem lê concluiria que a perda é zero em vez de que ela não foi estimada. O CHECK
        // de coerência do banco garante que média nula e INSUFFICIENT andam juntas.
        var session = login();
        var brewery = breweryOf(session);
        var recipe = UUID.randomUUID();
        repository.insert(LearnedProfile.compute(brewery, recipe, 1, Map.of(
                ProfileMetric.VOLUME_YIELD_PERCENT, valores("92", "94")),
                List.of(UUID.randomUUID()), UUID.randomUUID(), AGORA));

        var perda = estimativaDe(read(mockMvc.perform(get(PROFILES + "/" + recipe).session(session))
                .andExpect(status().isOk())), "TRANSFER_LOSS_LITERS");

        assertThat(perda.get("confidence").asText()).isEqualTo("INSUFFICIENT");
        assertThat(perda.get("mean").isNull()).isTrue();
        assertThat(perda.get("usable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("VERSIONADO, NUNCA SOBRESCRITO: a versão anterior continua consultável")
    void versionadoNaoSobrescreve() throws Exception {
        // Um perfil calculado em maio guiou decisões em maio.
        var session = login();
        var brewery = breweryOf(session);
        var recipe = UUID.randomUUID();
        repository.insert(LearnedProfile.compute(brewery, recipe, 1,
                Map.of(ProfileMetric.VOLUME_YIELD_PERCENT, valores("80", "82")),
                List.of(UUID.randomUUID()), UUID.randomUUID(), AGORA));
        repository.insert(LearnedProfile.compute(brewery, recipe, 2,
                Map.of(ProfileMetric.VOLUME_YIELD_PERCENT, valores("92", "94")),
                List.of(UUID.randomUUID()), UUID.randomUUID(), AGORA));

        // A vigente é a mais recente…
        var atual = read(mockMvc.perform(get(PROFILES + "/" + recipe).session(session))
                .andExpect(status().isOk()));
        assertThat(atual.get("version").asInt()).isEqualTo(2);

        // …e a anterior continua no histórico, com o número que valia então.
        var historico = read(mockMvc.perform(get(PROFILES + "/" + recipe + "/history").session(session))
                .andExpect(status().isOk()));
        assertThat(historico).hasSize(2);
        assertThat(estimativaDe(historico.get(1), "VOLUME_YIELD_PERCENT").get("mean").decimalValue())
                .isEqualByComparingTo("81");
    }

    @Test
    @DisplayName("a versão é derivada do que já existe")
    void versaoDerivada() throws Exception {
        var session = login();
        var brewery = breweryOf(session);
        var recipe = UUID.randomUUID();

        assertThat(repository.highestVersionOf(brewery, recipe)).isZero();
        repository.insert(LearnedProfile.compute(brewery, recipe, 1, Map.of(),
                List.of(UUID.randomUUID()), UUID.randomUUID(), AGORA));
        assertThat(repository.highestVersionOf(brewery, recipe)).isEqualTo(1);
    }

    @Test
    @DisplayName("receita sem perfil responde 204 — diferente de analisada e sem resultado")
    void semPerfilResponde204() throws Exception {
        var session = login();

        mockMvc.perform(get(PROFILES + "/" + UUID.randomUUID()).session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("amostra em que nenhum lote serve responde 422 dizendo o que fazer")
    void amostraVaziaResponde422() throws Exception {
        // Os lotes informados não existem nesta cervejaria; nenhum serve.
        var session = login();

        mockMvc.perform(post(PROFILES).with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + UUID.randomUUID() + "\",\"batchIds\":[\""
                                + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("empty_learning_sample"));
    }

    @Test
    @DisplayName("perfil de outra cervejaria não é visível")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var brewery = breweryOf(session);
        var recipe = UUID.randomUUID();
        repository.insert(LearnedProfile.compute(brewery, recipe, 1,
                Map.of(ProfileMetric.VOLUME_YIELD_PERCENT, valores("92", "94")),
                List.of(UUID.randomUUID()), UUID.randomUUID(), AGORA));

        var outra = principal(UUID.randomUUID(),
                Set.of("digitaltwin.profile.read", "digitaltwin.profile.compute"));

        mockMvc.perform(get(PROFILES + "/" + recipe).with(authentication(outra)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("calcular é alçada própria: quem só lê não escolhe a amostra")
    void calcularExigeAlcadaPropria() throws Exception {
        // Quem escolhe quais lotes entram no perfil decide o número que vai guiar o planejamento.
        var brewery = breweryOf(login());
        var leitor = principal(brewery, Set.of("digitaltwin.profile.read"));

        mockMvc.perform(get(PROFILES + "/" + UUID.randomUUID()).with(authentication(leitor)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(PROFILES).with(csrf()).with(authentication(leitor))
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + UUID.randomUUID() + "\",\"batchIds\":[\""
                                + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissaoNadaResponde() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(PROFILES + "/" + UUID.randomUUID()).with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lista de lotes vazia é recusada no contrato")
    void listaVaziaERecusadaNoContrato() throws Exception {
        var session = login();

        mockMvc.perform(post(PROFILES).with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + UUID.randomUUID() + "\",\"batchIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    // --- infraestrutura ---

    private static List<BigDecimal> valores(String... v) {
        return java.util.Arrays.stream(v).map(BigDecimal::new).toList();
    }

    private static JsonNode estimativaDe(JsonNode profile, String metric) {
        for (var estimate : profile.get("estimates")) {
            if (metric.equals(estimate.get("metric").asText())) {
                return estimate;
            }
        }
        throw new AssertionError("métrica ausente do perfil: " + metric);
    }

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = read(mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()));
        return UUID.fromString(body.get("activeBrewery").get("id").asText());
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
