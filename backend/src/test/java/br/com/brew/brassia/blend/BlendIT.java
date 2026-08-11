package br.com.brew.brassia.blend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * União e divisão de ponta a ponta (BLD-001).
 *
 * <p>O que só aparece aqui: <strong>a genealogia atravessando de verdade</strong>. O teste executa uma
 * união e depois consulta o serviço de rastreabilidade — que não sabe que blend existe — para ver o lote
 * de destino apontando para as origens. É o que sustenta "recall recalculado": ninguém dispara o
 * recálculo, ele é consequência da aresta existir.
 */
@SpringBootTest
@Testcontainers
class BlendIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BLENDS = "/api/v1/blends";
    private static final String GENEALOGY = "/api/v1/traceability/genealogy";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("simular uma união com o balanço fechado devolve 201")
    void simularUniao() throws Exception {
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var destino = batch(session);

        simulate(session, "MERGE", movements(a, "400", b, "200"), movement(destino, "588"), "12")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SIMULATED"))
                .andExpect(jsonPath("$.inputLiters").value(600))
                .andExpect(jsonPath("$.outputLiters").value(588))
                .andExpect(jsonPath("$.declaredLossLiters").value(12))
                // Ainda não pesa na genealogia: nenhuma cerveja se tocou.
                .andExpect(jsonPath("$.contributesLineage").value(false));
    }

    @Test
    @DisplayName("BALANÇO QUE NÃO FECHA: 422 dizendo quanto falta e de que lado")
    void balancoNaoFecha() throws Exception {
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var destino = batch(session);

        simulate(session, "MERGE", movements(a, "400", b, "200"), movement(destino, "500"), "0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("unbalanced_blend"))
                .andExpect(jsonPath("$.difference").value(100))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("100")));
    }

    @Test
    @DisplayName("volume aparecendo na saída também é recusado")
    void volumeAparecendo() throws Exception {
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var destino = batch(session);

        simulate(session, "MERGE", movements(a, "300", b, "200"), movement(destino, "560"), "0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.difference").value(-60));
    }

    @Test
    @DisplayName("A GENEALOGIA ATRAVESSA: executada a união, o destino aponta para as origens")
    void genealogiaAtravessa() throws Exception {
        // Este é o teste da história. A rastreabilidade não sabe que blend existe; ela percorre os
        // LineageSource. Recall recalculado não é um passo que alguém dispara — é a aresta existir.
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var destino = batch(session);

        var id = idOf(simulate(session, "MERGE", movements(a, "400", b, "200"),
                movement(destino, "600"), "0").andExpect(status().isCreated()));

        // Antes de executar, a genealogia do destino não conhece as origens.
        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", destino)
                        .param("direction", "BACKWARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..id", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(a))));

        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contributesLineage").value(true));

        // Depois de executar, os dois lotes de origem aparecem como ancestrais do destino.
        var ancestrais = mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", destino)
                        .param("direction", "BACKWARD"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(ancestrais).contains(a).contains(b);

        // E o caminho inverso também: a origem sabe para onde foi.
        var descendentes = mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", a)
                        .param("direction", "FORWARD"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(descendentes).contains(destino);
    }

    @Test
    @DisplayName("divisão executada liga o lote de origem aos dois destinos")
    void divisaoAtravessa() throws Exception {
        var session = login();
        var origem = batch(session);
        var d1 = batch(session);
        var d2 = batch(session);

        var body = """
                {"kind":"SPLIT","inputs":[{"batchId":"%s","liters":400}],
                 "outputs":[{"batchId":"%s","liters":200},{"batchId":"%s","liters":195}],
                 "declaredLossLiters":5,"reason":"Separar para dry hopping distinto"}
                """.formatted(origem, d1, d2);
        var id = idOf(mockMvc.perform(post(BLENDS).session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()));

        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()));
        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isOk());

        var descendentes = mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", origem)
                        .param("direction", "FORWARD"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(descendentes).contains(d1).contains(d2);
    }

    @Test
    @DisplayName("não se executa o que não foi aprovado")
    void executarSemAprovar() throws Exception {
        var session = login();
        var id = simulatedId(session);

        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("illegal_blend_transition"))
                .andExpect(jsonPath("$.currentStatus").value("SIMULATED"));
    }

    @Test
    @DisplayName("executar duas vezes responde 409 — a cerveja não se mistura duas vezes")
    void executarDuasVezes() throws Exception {
        var session = login();
        var id = simulatedId(session);
        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()));
        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("EXECUTED"));
    }

    @Test
    @DisplayName("executada não se descarta")
    void executadaNaoDescarta() throws Exception {
        var session = login();
        var id = simulatedId(session);
        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()));
        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()));

        mockMvc.perform(post(BLENDS + "/" + id + "/discard").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("lote de outra cervejaria não entra na operação")
    void loteDeOutraCervejaria() throws Exception {
        var session = login();
        var a = batch(session);
        var b = batch(session);

        simulate(session, "MERGE", movements(a, "300", b, "300"),
                movement(UUID.randomUUID().toString(), "600"), "0")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("unknown_blend_batch"));
    }

    @Test
    @DisplayName("operação de outra cervejaria não é visível nem operável")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var id = simulatedId(session);
        var outra = principal(UUID.randomUUID(), Set.of("blend.operation.read",
                "blend.operation.approve", "blend.operation.execute"));

        mockMvc.perform(get(BLENDS + "/" + id).with(authentication(outra)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BLENDS + "/" + id + "/approval").with(csrf()).with(authentication(outra)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("APROVAR E EXECUTAR SÃO ALÇADAS SEPARADAS de simular")
    void permissoesSeparadas() throws Exception {
        // Depois de misturadas, duas cervejas não se separam. Quem simula não necessariamente autoriza.
        var session = login();
        var id = simulatedId(session);
        var soSimula = principal(breweryOf(session), Set.of("blend.operation.read",
                "blend.operation.simulate"));

        mockMvc.perform(post(BLENDS + "/" + id + "/approval").with(csrf())
                        .with(authentication(soSimula)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BLENDS + "/" + id + "/execution").with(csrf())
                        .with(authentication(soSimula)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissao() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(BLENDS).with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("motivo vazio é recusado no contrato")
    void motivoVazio() throws Exception {
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var destino = batch(session);
        var body = """
                {"kind":"MERGE","inputs":[{"batchId":"%s","liters":300},{"batchId":"%s","liters":300}],
                 "outputs":[{"batchId":"%s","liters":600}],"declaredLossLiters":0,"reason":""}
                """.formatted(a, b, destino);

        mockMvc.perform(post(BLENDS).session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }


    @Test
    @DisplayName("A UNIÃO PRODUZ UM LOTE NOVO: em fermentação, no tanque declarado, sem ordem")
    void uniaoProduzLoteNovo() throws Exception {
        // A decisão de negócio da DEC-BLD-003. O lote nasce FERMENTING porque o envase recusa lote fora de
        // fermentação — em brassa, ele nunca poderia ser envasado, o que anularia a razão de criá-lo.
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var receita = publishedRecipe(session);
        var tanque = equipment(session);

        var resposta = simulateWithResult(session, movements(a, "400", b, "200"), receita, tanque, "588", "12")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var operacao = JSON.readTree(resposta);
        var id = operacao.get("id").asText();
        // Antes de executar, a saída existe no plano e o lote não: nenhuma cerveja se tocou.
        org.assertj.core.api.Assertions.assertThat(operacao.get("results").get(0).get("batchId").isNull())
                .isTrue();

        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()));
        var executada = mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var novoLote = JSON.readTree(executada).get("results").get(0).get("batchId").asText();
        var detalhe = batchDetail(session, novoLote);
        org.assertj.core.api.Assertions.assertThat(detalhe.get("status").asText()).isEqualTo("FERMENTING");
        org.assertj.core.api.Assertions.assertThat(detalhe.get("orderId").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(detalhe.get("code").asText()).startsWith("BLD-");
        org.assertj.core.api.Assertions.assertThat(detalhe.get("volumeLiters").asDouble()).isEqualTo(588.0);
    }

    @Test
    @DisplayName("O VOLUME SAI DA ORIGEM, e a origem que zera é encerrada")
    void volumeSaiDaOrigem() throws Exception {
        // Sem isto, o sistema passaria a ter o dobro da cerveja que existe: lote de resultado cheio e
        // origens intactas.
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var receita = publishedRecipe(session);
        var tanque = equipment(session);

        var id = idOf(simulateWithResult(session, movements(a, "400", b, "200"), receita, tanque, "588", "12")
                .andExpect(status().isCreated()));
        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()));
        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isOk());

        // `a` cedeu os 400 L inteiros: não sobrou cerveja, e lote vazio em aberto continuaria aparecendo
        // como disponível para envase.
        org.assertj.core.api.Assertions.assertThat(batchDetail(session, a).get("status").asText())
                .isEqualTo("COMPLETED");
        // `b` cedeu 200 dos 400 e continua vivo com o saldo.
        org.assertj.core.api.Assertions.assertThat(batchDetail(session, b).get("status").asText())
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("TIRAR MAIS DO QUE O LOTE TEM É RECUSADO, com quanto existe e quanto foi pedido")
    void volumeInsuficiente() throws Exception {
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var receita = publishedRecipe(session);
        var tanque = equipment(session);

        // 900 L de dois lotes de 400: a conta fecha com a saída, mas a cerveja não existe no tanque.
        var id = idOf(simulateWithResult(session, movements(a, "500", b, "400"), receita, tanque, "900", "0")
                .andExpect(status().isCreated()));
        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()));

        mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("insufficient_batch_volume"))
                .andExpect(jsonPath("$.availableLiters").exists())
                .andExpect(jsonPath("$.requestedLiters").exists());
    }

    @Test
    @DisplayName("tanque ocupado recusa o resultado e nomeia quem está lá")
    void tanqueOcupado() throws Exception {
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var receita = publishedRecipe(session);
        var tanque = equipment(session);

        var primeiro = idOf(simulateWithResult(session, movements(a, "200", b, "200"), receita, tanque,
                "400", "0").andExpect(status().isCreated()));
        mockMvc.perform(post(BLENDS + "/" + primeiro + "/approval").session(session).with(csrf()));
        mockMvc.perform(post(BLENDS + "/" + primeiro + "/execution").session(session).with(csrf()))
                .andExpect(status().isOk());

        var c = batch(session);
        var d = batch(session);
        var segundo = idOf(simulateWithResult(session, movements(c, "200", d, "200"), receita, tanque,
                "400", "0").andExpect(status().isCreated()));
        mockMvc.perform(post(BLENDS + "/" + segundo + "/approval").session(session).with(csrf()));

        mockMvc.perform(post(BLENDS + "/" + segundo + "/execution").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("vessel_occupied"))
                .andExpect(jsonPath("$.occupiedBy").exists());
    }

    @Test
    @DisplayName("o lote de resultado entra na genealogia como destino")
    void resultadoNaGenealogia() throws Exception {
        // Para quem investiga um recall, lote de resultado não é diferente de um destino que já existia.
        var session = login();
        var a = batch(session);
        var b = batch(session);
        var receita = publishedRecipe(session);
        var tanque = equipment(session);

        var id = idOf(simulateWithResult(session, movements(a, "300", b, "300"), receita, tanque, "600", "0")
                .andExpect(status().isCreated()));
        mockMvc.perform(post(BLENDS + "/" + id + "/approval").session(session).with(csrf()));
        var executada = mockMvc.perform(post(BLENDS + "/" + id + "/execution").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var novoLote = JSON.readTree(executada).get("results").get(0).get("batchId").asText();

        var descendentes = mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", a)
                        .param("direction", "FORWARD"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(descendentes).contains(novoLote);
    }

    // --- infraestrutura ---

    /** Equipamento livre, para receber o lote que o blend produz. */
    private String equipment(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"tk-" + sfx + "\",\"name\":\"Tanque\",\"capacityLiters\":1000,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()));
    }

    /** Receita publicada, para declarar o que o resultado do blend é. */
    private String publishedRecipe(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = equipment(session);
        var maltId = createIngredient(session, "MALT", "rm-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "rh-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "ry-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"RES %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()));
        return recipeId;
    }

    private ResultActions simulateWithResult(MockHttpSession session, String inputs, String recipeId,
            String equipmentId, String liters, String loss) throws Exception {
        var body = """
                {"kind":"MERGE","inputs":[%s],
                 "results":[{"recipeId":"%s","equipmentId":"%s","liters":%s}],
                 "declaredLossLiters":%s,"reason":"União para lote novo"}
                """.formatted(inputs, recipeId, equipmentId, liters, loss);
        return mockMvc.perform(post(BLENDS).session(session).with(csrf())
                .contentType("application/json").content(body));
    }

    private com.fasterxml.jackson.databind.JsonNode batchDetail(MockHttpSession session, String batchId)
            throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private ResultActions simulate(MockHttpSession session, String kind, String inputs, String outputs,
            String loss) throws Exception {
        var body = """
                {"kind":"%s","inputs":[%s],"outputs":[%s],"declaredLossLiters":%s,
                 "reason":"Aproveitamento de sobra de tanque"}
                """.formatted(kind, inputs, outputs, loss);
        return mockMvc.perform(post(BLENDS).session(session).with(csrf())
                .contentType("application/json").content(body));
    }

    private static String movement(String batchId, String liters) {
        return "{\"batchId\":\"" + batchId + "\",\"liters\":" + liters + "}";
    }

    private static String movements(String first, String firstLiters, String second,
            String secondLiters) {
        return movement(first, firstLiters) + "," + movement(second, secondLiters);
    }

    private String simulatedId(MockHttpSession session) throws Exception {
        var a = batch(session);
        var b = batch(session);
        var destino = batch(session);
        return idOf(simulate(session, "MERGE", movements(a, "300", b, "300"),
                movement(destino, "600"), "0").andExpect(status().isCreated()));
    }

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(body).get("activeBrewery").get("id").asText());
    }

    /** Um lote iniciado. Cada chamada produz uma receita e uma ordem próprias. */
    private String batch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bld-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()));
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"BLD %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()));
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"));
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // A listagem passou a ser paginada (REL-002): o array vem em `content`.
        for (var node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("lote não encontrado para a ordem " + orderId);
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit,
            String attributes) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()));
    }

    private static String idOf(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asText();
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
