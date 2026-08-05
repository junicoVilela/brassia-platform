package br.com.brew.brassia.packaging;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@Testcontainers
class LabelIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLANS = "/api/v1/packaging/plans";
    private static final String TEMPLATES = "/api/v1/packaging/label-templates";
    private static final String RULE = "/api/v1/packaging/label-rule";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void previewResolvesEveryFieldFromATraceableSource() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME", "BATCH_CODE", "VOLUME_ML").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME", "BATCH_CODE", "VOLUME_ML", "ABV", "QR_PAYLOAD");
        var planId = executedPlan(session);

        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printable", is(true)))
                .andExpect(jsonPath("$.lines.length()", is(5)))
                // Nada é digitado: cada valor vem de uma origem identificada.
                .andExpect(jsonPath("$.lines[?(@.field=='BATCH_CODE')].source",
                        hasItem(containsString("lote de produção"))))
                .andExpect(jsonPath("$.lines[?(@.field=='VOLUME_ML')].value", hasItem("355")))
                .andExpect(jsonPath("$.lines[?(@.field=='VOLUME_ML')].source",
                        hasItem(containsString("embalagem do plano"))))
                .andExpect(jsonPath("$.lines[?(@.field=='ABV')].source",
                        hasItem(containsString("receita publicada"))))
                .andExpect(jsonPath("$.lines[?(@.field=='QR_PAYLOAD')].value",
                        hasItem(containsString("brassia://lote/"))));
    }

    @Test
    void bestBeforeComesFromTheFreshnessRecordAndSaysWhichSource() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME", "BEST_BEFORE").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME", "BEST_BEFORE");
        var planId = executedPlan(session);

        // Sem controle de frescor não há validade: a prévia barra a impressão.
        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(jsonPath("$.printable", is(false)))
                .andExpect(jsonPath("$.missingRequired", hasItem("BEST_BEFORE")));

        savePolicy(session).andExpect(status().isOk());
        measure(session, planId).andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(jsonPath("$.printable", is(true)))
                .andExpect(jsonPath("$.lines[?(@.field=='BEST_BEFORE')].source",
                        hasItem(containsString("controle de frescor"))));
    }

    @Test
    void overriddenBestBeforeSaysItWasOverriddenAndWhy() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME", "BEST_BEFORE").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME", "BEST_BEFORE");
        var planId = executedPlan(session);
        savePolicy(session).andExpect(status().isOk());
        measure(session, planId).andExpect(status().isOk());

        mockMvc.perform(post(PLANS + "/" + planId + "/freshness/override").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"shelfLifeDays\":200,\"reason\":\"estoque refrigerado\"}"))
                .andExpect(status().isOk());

        // O rótulo não esconde que a data veio de decisão humana.
        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(jsonPath("$.lines[?(@.field=='BEST_BEFORE')].source",
                        hasItem(containsString("sobreposta: estoque refrigerado"))));
    }

    @Test
    void missingRequiredFieldBlocksPrinting() throws Exception {
        var session = login();
        // Cervejaria que não declarou alergênico nenhum (FDS-001): o campo continua sem fonte, e
        // exigi-lo barra a impressão. É o comportamento correto — imprimir isenção que ninguém
        // assinou seria pior do que não imprimir.
        saveRule(session, "BEER_NAME", "ALLERGENS").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME", "ALLERGENS");
        var planId = executedPlan(session);

        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(jsonPath("$.printable", is(false)))
                .andExpect(jsonPath("$.missingRequired", hasItem("ALLERGENS")));

        print(session, planId, template, 800, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("label_not_printable")))
                .andExpect(jsonPath("$.label.missingRequired", hasItem("ALLERGENS")));
    }

    @Test
    void requiredFieldTheLayoutDroppedAlsoBlocksPrinting() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME", "BATCH_CODE").andExpect(status().isOk());
        // Template versão nova que "esqueceu" o código do lote: a regra denuncia.
        var template = saveTemplate(session, "BEER_NAME");
        var planId = executedPlan(session);

        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(jsonPath("$.printable", is(false)))
                .andExpect(jsonPath("$.requiredNotDrawn", hasItem("BATCH_CODE")))
                .andExpect(jsonPath("$.missingRequired", not(hasItem("BATCH_CODE"))));

        print(session, planId, template, 800, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.label.requiredNotDrawn", hasItem("BATCH_CODE")));
    }

    @Test
    void optionalMissingFieldIsOnlyAWarning() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME", "ALLERGENS");
        var planId = executedPlan(session);

        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").session(session)
                        .param("templateId", template))
                .andExpect(jsonPath("$.printable", is(true)))
                .andExpect(jsonPath("$.missingOptional", hasItem("ALLERGENS")))
                .andExpect(jsonPath("$.missingRequired").isEmpty());
    }

    @Test
    void templateIsVersionedAndPreviousVersionsStayReadable() throws Exception {
        var session = login();
        var code = "RTL-" + UUID.randomUUID().toString().substring(0, 8);

        var first = saveTemplateWithCode(session, code, "BEER_NAME");
        var second = saveTemplateWithCode(session, code, "BEER_NAME", "BATCH_CODE");

        mockMvc.perform(get(TEMPLATES + "/" + code + "/versions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].version", is(2)))
                .andExpect(jsonPath("$[1].version", is(1)))
                // A versão anterior segue exatamente como era.
                .andExpect(jsonPath("$[1].fields", is(java.util.List.of("BEER_NAME"))));

        // A listagem mostra só a vigente de cada código.
        mockMvc.perform(get(TEMPLATES).session(session))
                .andExpect(jsonPath("$[?(@.code=='" + code + "')].version", is(java.util.List.of(2))));
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
    }

    @Test
    void templateKeepsTheFieldOrderThatDefinesTheLayout() throws Exception {
        var session = login();
        var code = "RTL-" + UUID.randomUUID().toString().substring(0, 8);
        saveTemplateWithCode(session, code, "QR_PAYLOAD", "BEER_NAME", "ABV");

        mockMvc.perform(get(TEMPLATES + "/" + code + "/versions").session(session))
                .andExpect(status().isOk())
                // A ordem é o layout: não pode ser reordenada na ida e volta do banco.
                .andExpect(jsonPath("$[0].fields",
                        is(java.util.List.of("QR_PAYLOAD", "BEER_NAME", "ABV"))));
    }

    @Test
    void reprintRequiresReasonAndKeepsTheQuantity() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME");
        var planId = executedPlan(session);

        print(session, planId, template, 800, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.reprint", is(false)))
                .andExpect(jsonPath("$.quantity", is(800)));

        // Segunda tiragem é reimpressão por consequência, não por escolha de quem chama.
        print(session, planId, template, 40, null).andExpect(status().isBadRequest());
        print(session, planId, template, 40, "impressora borrou 40 rótulos").andExpect(status().isOk())
                .andExpect(jsonPath("$.reprint", is(true)))
                .andExpect(jsonPath("$.quantity", is(40)));

        mockMvc.perform(get(PLANS + "/" + planId + "/label/prints").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].reprint", is(true)))
                .andExpect(jsonPath("$[0].reason", is("impressora borrou 40 rótulos")))
                .andExpect(jsonPath("$[0].quantity", is(40)))
                .andExpect(jsonPath("$[1].reprint", is(false)));
    }

    @Test
    void printKeepsTheTemplateVersionUsed() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME").andExpect(status().isOk());
        var code = "RTL-" + UUID.randomUUID().toString().substring(0, 8);
        var v1 = saveTemplateWithCode(session, code, "BEER_NAME");
        var planId = executedPlan(session);
        print(session, planId, v1, 800, null).andExpect(status().isOk());

        // O layout muda depois; a impressão antiga continua apontando a versão que usou.
        saveTemplateWithCode(session, code, "BEER_NAME", "BATCH_CODE");

        mockMvc.perform(get(PLANS + "/" + planId + "/label/prints").session(session))
                .andExpect(jsonPath("$[0].templateVersion", is(1)))
                .andExpect(jsonPath("$[0].templateCode", is(code)));
    }

    @Test
    void printingWithoutRegulatoryRuleIsRefused() throws Exception {
        // Cervejaria sem regra configurada: o sistema não decide regulação por ela.
        var fresh = principal(UUID.randomUUID(), Set.of("packaging.plan.read"));

        mockMvc.perform(get(RULE).with(authentication(fresh))).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownFieldEmptyTemplateAndNonPositiveQuantity() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME");
        var planId = executedPlan(session);

        mockMvc.perform(put(TEMPLATES).session(session).with(csrf()).contentType("application/json")
                        .content("{\"code\":\"RTL-X\",\"name\":\"X\",\"fields\":[\"PRECO\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(TEMPLATES).session(session).with(csrf()).contentType("application/json")
                        .content("{\"code\":\"RTL-X\",\"name\":\"X\",\"fields\":[]}"))
                .andExpect(status().isBadRequest());
        print(session, planId, template, 0, null).andExpect(status().isBadRequest());
    }

    @Test
    void deniesTemplateAndRuleWithoutPermission() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME");
        var planId = executedPlan(session);

        mockMvc.perform(post(PLANS + "/" + planId + "/label/prints")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("packaging.plan.read"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"templateId\":\"" + template + "\",\"quantity\":10}"))
                .andExpect(status().isForbidden());
        // A regra regulatória é alçada própria: gerir plano não basta.
        mockMvc.perform(put(RULE)
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("packaging.plan.read", "packaging.plan.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"requiredFields\":[\"BEER_NAME\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        saveRule(session, "BEER_NAME").andExpect(status().isOk());
        var template = saveTemplate(session, "BEER_NAME");
        var planId = executedPlan(session);
        print(session, planId, template, 800, null).andExpect(status().isOk());

        var other = principal(UUID.randomUUID(), Set.of("packaging.plan.read", "packaging.plan.manage"));
        mockMvc.perform(get(TEMPLATES).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(PLANS + "/" + planId + "/label/prints").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(PLANS + "/" + planId + "/label/preview").with(authentication(other))
                        .param("templateId", template))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private ResultActions saveRule(MockHttpSession session, String... fields) throws Exception {
        var list = java.util.Arrays.stream(fields).map(f -> "\"" + f + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return mockMvc.perform(put(RULE).session(session).with(csrf()).contentType("application/json")
                .content("{\"requiredFields\":[" + list + "]}"));
    }

    private String saveTemplate(MockHttpSession session, String... fields) throws Exception {
        return saveTemplateWithCode(session, "RTL-" + UUID.randomUUID().toString().substring(0, 8), fields);
    }

    private String saveTemplateWithCode(MockHttpSession session, String code, String... fields)
            throws Exception {
        var list = java.util.Arrays.stream(fields).map(f -> "\"" + f + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        var body = mockMvc.perform(put(TEMPLATES).session(session).with(csrf()).contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Rótulo " + code + "\","
                                + "\"fields\":[" + list + "]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("templateId").asText();
    }

    private ResultActions print(MockHttpSession session, String planId, String templateId, int quantity,
            String reason) throws Exception {
        return mockMvc.perform(post(PLANS + "/" + planId + "/label/prints").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"templateId\":\"" + templateId + "\",\"quantity\":" + quantity
                        + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}"));
    }

    private ResultActions savePolicy(MockHttpSession session) throws Exception {
        return mockMvc.perform(put("/api/v1/packaging/shelf-life-policy").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"tiers\":[{\"maxTpoPpb\":100,\"shelfLifeDays\":120}],\"fallbackDays\":30}"));
    }

    private ResultActions measure(MockHttpSession session, String planId) throws Exception {
        return mockMvc.perform(put(PLANS + "/" + planId + "/freshness").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"dissolvedOxygenPpb\":30,\"totalPackageOxygenPpb\":80,\"purgeMethod\":\"CO₂\","
                        + "\"purgeVerified\":true,\"sealCheckMethod\":\"recravação\",\"sealCheckPassed\":true}"));
    }

    /** Plano com envase executado: é dele que o rótulo tira lote, volume e validade. */
    private String executedPlan(MockHttpSession session) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveContainers(session, containerId, 1000);
        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);

        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var content = """
                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":800,"lineEquipmentId":"%s",
                 "plannedStart":"2026-08-20T09:00:00Z","plannedEnd":"2026-08-20T15:00:00Z"}
                """.formatted(sfx, batchId, containerId, lineId);
        var body = mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var planId = JSON.readTree(body).get("id").asText();

        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"inputVolumeLiters\":284,\"producedUnits\":780,\"rejectedUnits\":12}"))
                .andExpect(status().isOk());
        return planId;
    }

    private void releaseCleaning(MockHttpSession session, String equipmentId) throws Exception {
        var code = "CIP-" + UUID.randomUUID().toString().substring(0, 8);
        var step = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var procedureId = idOf(mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP linha\",\"steps\":[" + step + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + procedureId + "/publish").session(session)
                        .with(csrf()))
                .andExpect(status().isOk());
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
    }

    private void receiveContainers(MockHttpSession session, String containerId, int quantity) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + containerId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"UNIT\",\"unitCost\":0.9,"
                                + "\"expiryDate\":\"2028-01-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated());
    }

    private String fermentingBatch(MockHttpSession session) throws Exception {
        var batchId = startedBatch(session);
        var fermenter = createEquipment(session);
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
        return batchId;
    }

    private String startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(listBody)) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Label %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        // As métricas calculadas são a fonte do ABV no rótulo.
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
        return orderId;
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
        var code = type.toLowerCase(java.util.Locale.ROOT).charAt(0) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
