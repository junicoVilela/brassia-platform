package br.com.brew.brassia.foodsafety;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
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
 * Matriz de alergênicos de ponta a ponta (FDS-001): o vocabulário da casa, as três declarações que
 * ele cruza e as duas consequências que a história promete — a troca de produto recusada sem POP
 * compatível e o rótulo que finalmente tem fonte para o campo de alergênicos (PKG-004-A).
 */
@SpringBootTest
@Testcontainers
class FoodSafetyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    /**
     * Janela do envase ancorada em AGORA, e não numa data fixa.
     *
     * <p>A linha limpa exige liberação <strong>anterior</strong> ao início planejado
     * ({@code LineCleanliness}). Com data fixa, o dia em que ela passa inverte a ordem e todo envase
     * destes testes passa a ser recusado com {@code line_not_clean} — uma falha datada, que aparece sem
     * ninguém ter mexido em nada.
     */
    private static final String PLANNED_START = Instant.now().plus(Duration.ofHours(1)).toString();
    private static final String PLANNED_END = Instant.now().plus(Duration.ofHours(7)).toString();

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ALLERGENS = "/api/v1/food-safety/allergens";
    private static final String MATRIX = "/api/v1/food-safety/matrix";
    private static final String CHANGEOVER = "/api/v1/food-safety/changeover";
    private static final String PLANS = "/api/v1/packaging/plans";

    /** Uso anterior no passado e envase no futuro: a liberação de limpeza de hoje cai entre os dois. */
    private static final String PREVIOUS_USE = "2026-01-05T08:00:00Z";
    private static final String LATER = "2026-12-01T09:00:00Z";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("o vocabulário é da casa, e declarar código fora dele é recusado")
    void vocabularioEhDaCasa() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");

        mockMvc.perform(get(ALLERGENS).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='GLUTEN')].name", hasItem("Glúten")));

        // Cadastrar duas vezes o mesmo código conflita: dois "GLUTEN" partiriam a matriz em duas.
        registerAllergen(session, "gluten", "Glúten de novo").andExpect(status().isConflict());

        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        declareIngredient(session, maltId, "SOJA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("unknown_allergen")))
                .andExpect(jsonPath("$.allergen", is("SOJA")));
    }

    @Test
    @DisplayName("perfil do lote distingue declarado isento de não declarado")
    void perfilDistingueIsentoDeNaoDeclarado() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        var scene = brewedBatch(session);

        // Nada declarado: o perfil não afirma isenção, ele declara a lacuna.
        mockMvc.perform(get("/api/v1/food-safety/batches/" + scene.batchId + "/allergens").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete", is(false)))
                .andExpect(jsonPath("$.allergens").isEmpty())
                .andExpect(jsonPath("$.gaps.length()", is(3)));

        declareIngredient(session, scene.maltId, "GLUTEN").andExpect(status().isOk());
        declareIngredient(session, scene.hopId).andExpect(status().isOk());
        declareIngredient(session, scene.yeastId).andExpect(status().isOk());

        // Agora sim: dois isentos e um com glúten, e o perfil pode ser afirmado.
        mockMvc.perform(get("/api/v1/food-safety/batches/" + scene.batchId + "/allergens").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete", is(true)))
                .andExpect(jsonPath("$.allergens", is(java.util.List.of("GLUTEN"))))
                .andExpect(jsonPath("$.gaps").isEmpty());
    }

    @Test
    @DisplayName("a troca de produto exige POP que declare remover o alergênico que ficou")
    void trocaExigePopCompativel() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        var comGluten = declaredBatch(session, "GLUTEN");
        var semGluten = declaredBatch(session);
        var line = createEquipment(session);
        var cip = releaseCleaning(session, line);

        // O POP liberado limpa sujidade e não responde por alergênico: a troca não passa.
        changeover(session, line, semGluten.batchId, comGluten.batchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.code", is("allergen_changeover_required")))
                .andExpect(jsonPath("$.allergens", is(java.util.List.of("GLUTEN"))));

        declareProcedure(session, cip, "GLUTEN").andExpect(status().isOk());

        changeover(session, line, semGluten.batchId, comGluten.batchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.outcome", is("CLEANED")))
                .andExpect(jsonPath("$.detail", containsString(cip)));

        // Na direção oposta não há troca: o glúten que entra já estava lá, e POP exigido sem
        // motivo é POP que se aprende a ignorar.
        changeover(session, line, comGluten.batchId, semGluten.batchId)
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.outcome", is("CLEAR")));
    }

    @Test
    @DisplayName("lacuna de declaração bloqueia a troca: 'não sei' não vale 'não tem'")
    void lacunaBloqueiaTroca() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        var declarado = declaredBatch(session, "GLUTEN");
        var semDeclaracao = brewedBatch(session);
        var line = createEquipment(session);
        releaseCleaning(session, line);

        changeover(session, line, semDeclaracao.batchId, declarado.batchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.code", is("allergen_undeclared")))
                .andExpect(jsonPath("$.gaps.length()", is(3)));
    }

    @Test
    @DisplayName("linha dedicada recusa o lote que traz alergênico, e o envase não reserva")
    void linhaDedicadaRecusaEnvase() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        var scene = declaredBatch(session, "GLUTEN");
        var line = createEquipment(session);
        releaseCleaning(session, line);
        // Dedicação sem alergênico nenhum: a linha livre de alergênicos.
        dedicate(session, line).andExpect(status().isOk());

        var planId = plannedPlan(session, scene.batchId, line);
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("packaging_blocked")))
                .andExpect(jsonPath("$.blockers[*].code", hasItem("allergen_dedication_violated")));

        // Devolver a linha ao compartilhado remove o impedimento — não há uso anterior a trocar.
        mockMvc.perform(delete("/api/v1/food-safety/equipment/" + line + "/dedication").session(session)
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("o rótulo ganha fonte para alergênicos — fecha PKG-004-A")
    void rotuloGanhaFonteDeAlergenicos() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        saveRule(session, "BEER_NAME", "ALLERGENS").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME", "ALLERGENS");
        var scene = brewedBatch(session);
        var planId = reservedPlan(session, scene.batchId);

        // Sem declaração o campo continua ausente, e ausente obrigatório barra a impressão.
        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printable", is(false)))
                .andExpect(jsonPath("$.missingRequired", hasItem("ALLERGENS")));

        declareIngredient(session, scene.maltId, "GLUTEN").andExpect(status().isOk());
        declareIngredient(session, scene.hopId).andExpect(status().isOk());
        declareIngredient(session, scene.yeastId).andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printable", is(true)))
                .andExpect(jsonPath("$.lines[?(@.field=='ALLERGENS')].value", hasItem("Contém: Glúten")))
                .andExpect(jsonPath("$.lines[?(@.field=='ALLERGENS')].source",
                        hasItem(containsString("matriz de alergênicos"))));
    }

    @Test
    @DisplayName("declarar de novo substitui a declaração inteira, e repetir não muda nada")
    void declararEhRespostaCompleta() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        ensureAllergen(session, "LACTOSE", "Lactose");
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");

        declareIngredient(session, maltId, "GLUTEN", "LACTOSE").andExpect(status().isOk());
        declareIngredient(session, maltId, "GLUTEN", "LACTOSE").andExpect(status().isOk());
        assertIngredientAllergens(session, maltId, "GLUTEN", "LACTOSE");

        // Tirar a lactose é uma declaração nova, não um acréscimo: a matriz não só cresce.
        declareIngredient(session, maltId, "GLUTEN").andExpect(status().isOk());
        assertIngredientAllergens(session, maltId, "GLUTEN");

        // Declarado isento continua sendo declaração — some o alergênico, não a linha.
        declareIngredient(session, maltId).andExpect(status().isOk());
        assertIngredientAllergens(session, maltId);
        assertDeclared(session, maltId, true);
    }

    @Test
    @DisplayName("ler a matriz não basta para declarar")
    void semPermissaoNaoDeclara() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var reader = principal(UUID.randomUUID(), Set.of("foodsafety.allergen.read"));

        mockMvc.perform(put("/api/v1/food-safety/ingredients/" + maltId + "/allergens")
                        .with(authentication(reader)).with(csrf()).contentType("application/json")
                        .content("{\"allergens\":[\"GLUTEN\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ALLERGENS).with(authentication(reader)).with(csrf())
                        .contentType("application/json").content("{\"code\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(MATRIX).with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a matriz de uma cervejaria não aparece na outra")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        ensureAllergen(session, "GLUTEN", "Glúten");
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        declareIngredient(session, maltId, "GLUTEN").andExpect(status().isOk());

        var other = principal(UUID.randomUUID(),
                Set.of("foodsafety.allergen.read", "foodsafety.allergen.write"));
        mockMvc.perform(get(MATRIX).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergens").isEmpty())
                .andExpect(jsonPath("$.ingredients").isEmpty());
        // Nem sequer declarar sobre o ingrediente alheio: para essa cervejaria ele não existe.
        mockMvc.perform(put("/api/v1/food-safety/ingredients/" + maltId + "/allergens")
                        .with(authentication(other)).with(csrf()).contentType("application/json")
                        .content("{\"allergens\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // --- cenário ---

    private record Scene(String batchId, String maltId, String hopId, String yeastId) {}

    /** Lote com receita publicada de três ingredientes, sem nenhuma declaração de alergênico. */
    private Scene brewedBatch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"FDS %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var batchId = batchOfOrder(session, orderId);
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + createEquipment(session)
                                + "\",\"volumeLiters\":390,\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
        return new Scene(batchId, maltId, hopId, yeastId);
    }

    /** O mesmo lote, com os três ingredientes declarados; o malte recebe os alergênicos dados. */
    private Scene declaredBatch(MockHttpSession session, String... maltAllergens) throws Exception {
        var scene = brewedBatch(session);
        declareIngredient(session, scene.maltId, maltAllergens).andExpect(status().isOk());
        declareIngredient(session, scene.hopId).andExpect(status().isOk());
        declareIngredient(session, scene.yeastId).andExpect(status().isOk());
        return scene;
    }

    private String plannedPlan(MockHttpSession session, String batchId, String lineId) throws Exception {
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveLot(session, containerId, 2000, "UNIT");
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var planId = idOf(mockMvc.perform(post(PLANS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":800,
                                 "lineEquipmentId":"%s","plannedStart":"%s",
                                 "plannedEnd":"%s"}
                                """.formatted(sfx, batchId, containerId, lineId, PLANNED_START, PLANNED_END)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        return planId;
    }

    private String reservedPlan(MockHttpSession session, String batchId) throws Exception {
        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);
        var planId = plannedPlan(session, batchId, lineId);
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        return planId;
    }

    // --- helpers ---

    /** O vocabulário é da cervejaria e sobrevive entre os testes da classe: cadastrar de novo conflita. */
    private void ensureAllergen(MockHttpSession session, String code, String name) throws Exception {
        registerAllergen(session, code, name);
    }

    private ResultActions registerAllergen(MockHttpSession session, String code, String name) throws Exception {
        return mockMvc.perform(post(ALLERGENS).session(session).with(csrf()).contentType("application/json")
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"));
    }

    private ResultActions declareIngredient(MockHttpSession session, String ingredientId, String... codes)
            throws Exception {
        return mockMvc.perform(put("/api/v1/food-safety/ingredients/" + ingredientId + "/allergens")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"allergens\":[" + quoted(codes) + "]}"));
    }

    private ResultActions declareProcedure(MockHttpSession session, String procedureCode, String... codes)
            throws Exception {
        return mockMvc.perform(put("/api/v1/food-safety/procedures/" + procedureCode + "/allergens")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"allergens\":[" + quoted(codes) + "]}"));
    }

    private ResultActions dedicate(MockHttpSession session, String equipmentId, String... codes)
            throws Exception {
        return mockMvc.perform(put("/api/v1/food-safety/equipment/" + equipmentId + "/dedication")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"allergens\":[" + quoted(codes) + "]}"));
    }

    private ResultActions changeover(MockHttpSession session, String equipmentId, String incomingBatchId,
            String previousBatchId) throws Exception {
        return mockMvc.perform(get(CHANGEOVER).session(session)
                .param("equipmentId", equipmentId)
                .param("incomingBatchId", incomingBatchId)
                .param("previousBatchId", previousBatchId)
                .param("previousUseAt", PREVIOUS_USE)
                .param("at", LATER));
    }

    private void assertIngredientAllergens(MockHttpSession session, String ingredientId, String... expected)
            throws Exception {
        var row = ingredientRow(session, ingredientId);
        var codes = new java.util.ArrayList<String>();
        row.get("allergens").forEach(node -> codes.add(node.asText()));
        org.assertj.core.api.Assertions.assertThat(codes)
                .containsExactlyInAnyOrder(expected);
    }

    private void assertDeclared(MockHttpSession session, String ingredientId, boolean declared) throws Exception {
        org.assertj.core.api.Assertions.assertThat(ingredientRow(session, ingredientId).get("declared").asBoolean())
                .isEqualTo(declared);
    }

    private JsonNode ingredientRow(MockHttpSession session, String ingredientId) throws Exception {
        var body = mockMvc.perform(get(MATRIX).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode row : JSON.readTree(body).get("ingredients")) {
            if (row.get("ingredientId").asText().equals(ingredientId)) {
                return row;
            }
        }
        throw new AssertionError("ingrediente ausente da matriz: " + ingredientId);
    }

    private ResultActions saveRule(MockHttpSession session, String... fields) throws Exception {
        return mockMvc.perform(put("/api/v1/packaging/label-rule").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"requiredFields\":[" + quoted(fields) + "]}"));
    }

    private String saveTemplate(MockHttpSession session, String... fields) throws Exception {
        var code = "RTL-" + UUID.randomUUID().toString().substring(0, 8);
        var body = mockMvc.perform(put("/api/v1/packaging/label-templates").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Rótulo " + code + "\","
                                + "\"fields\":[" + quoted(fields) + "]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("templateId").asText();
    }

    private String batchOfOrder(MockHttpSession session, String orderId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(body).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String receiveLot(MockHttpSession session, String ingredientId, int quantity, String unit)
            throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"" + unit + "\",\"unitCost\":1.5,"
                                + "\"supplierLotCode\":\"F-" + sfx + "\","
                                + "\"expiryDate\":\"2028-01-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** Publica um POP, roda o ciclo e libera o equipamento; devolve o código do POP. */
    private String releaseCleaning(MockHttpSession session, String equipmentId) throws Exception {
        var code = "CIP-" + UUID.randomUUID().toString().substring(0, 8);
        var step = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var procedureId = idOf(mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP linha\",\"steps\":[" + step + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + procedureId + "/publish").session(session)
                        .with(csrf())).andExpect(status().isOk());
        var cycleId = idOf(mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/steps").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,"
                                + "\"measuredTimeMinutes\":20}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/complete").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,"
                                + "\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        return code;
    }

    private String createEquipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Linha\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String unit, String attributes)
            throws Exception {
        var code = type.toLowerCase(Locale.ROOT).charAt(0) + "-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String quoted(String... values) {
        return java.util.Arrays.stream(values).map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
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
