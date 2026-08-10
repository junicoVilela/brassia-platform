package br.com.brew.brassia.fermentation;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ReadingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String READINGS = "/api/v1/fermentation/readings";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recordsSeriesAndDistinguishesManualFromSensor() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        record(session, batchId, "TEMPERATURE", "SENSOR", "18.5", "C", "2026-07-31T10:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valid", is(true)));
        record(session, batchId, "DENSITY", "MANUAL", "1.048", "sg", "2026-07-31T11:00:00Z")
                .andExpect(status().isCreated());
        record(session, batchId, "PH", "MANUAL", "4.4", "PH", "2026-07-31T12:00:00Z")
                .andExpect(status().isCreated());

        // Série completa do lote, ordenada por instante, com a origem preservada.
        mockMvc.perform(get(READINGS).param("batchId", batchId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)))
                .andExpect(jsonPath("$[0].kind", is("TEMPERATURE")))
                .andExpect(jsonPath("$[0].source", is("SENSOR")))
                .andExpect(jsonPath("$[1].source", is("MANUAL")))
                .andExpect(jsonPath("$[2].kind", is("PH")));

        // Filtro por grandeza para a curva.
        mockMvc.perform(get(READINGS).param("batchId", batchId).param("kind", "density").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].unit", is("SG")));
    }

    @Test
    void flagsImplausibleReadingWithoutRejectingIt() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        record(session, batchId, "TEMPERATURE", "SENSOR", "150", "C", "2026-07-31T10:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.invalidReason").isNotEmpty());

        mockMvc.perform(get(READINGS).param("batchId", batchId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].valid", is(false)));
    }

    @Test
    void repeatedIngestionIsIdempotent() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        var first = record(session, batchId, "TEMPERATURE", "SENSOR", "18.5", "C", "2026-07-31T10:00:00Z")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // Reenvio do sensor com a mesma chave natural: 200, mesmo id, série sem duplicata.
        record(session, batchId, "TEMPERATURE", "SENSOR", "18.5", "C", "2026-07-31T10:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(JSON.readTree(first).get("id").asText())));

        mockMvc.perform(get(READINGS).param("batchId", batchId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));
    }

    @Test
    void rejectsUnitIncompatibleWithKind() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        record(session, batchId, "DENSITY", "MANUAL", "1.048", "C", "2026-07-31T10:00:00Z")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownBatch() throws Exception {
        var session = login();

        record(session, UUID.randomUUID().toString(), "PH", "MANUAL", "4.4", "PH", "2026-07-31T10:00:00Z")
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesRecordWithoutPermission() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        mockMvc.perform(post(READINGS)
                        .with(authentication(principal(UUID.randomUUID(), Set.of("fermentation.reading.read"))))
                        .with(csrf()).contentType("application/json")
                        .content(body(batchId, "PH", "MANUAL", "4.4", "PH", "2026-07-31T10:00:00Z")))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        record(session, batchId, "PH", "MANUAL", "4.4", "PH", "2026-07-31T10:00:00Z")
                .andExpect(status().isCreated());

        var other = principal(UUID.randomUUID(), Set.of("fermentation.reading.read", "fermentation.reading.record"));
        // Outra cervejaria não enxerga a série...
        mockMvc.perform(get(READINGS).param("batchId", batchId).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        // ...nem consegue anexar leitura ao lote alheio.
        mockMvc.perform(post(READINGS).with(authentication(other)).with(csrf()).contentType("application/json")
                        .content(body(batchId, "PH", "MANUAL", "4.5", "PH", "2026-07-31T13:00:00Z")))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions record(MockHttpSession session, String batchId,
            String kind, String source, String value, String unit, String at) throws Exception {
        return mockMvc.perform(post(READINGS).session(session).with(csrf()).contentType("application/json")
                .content(body(batchId, kind, source, value, unit, at)));
    }

    private static String body(String batchId, String kind, String source, String value, String unit, String at) {
        return "{\"batchId\":\"" + batchId + "\",\"kind\":\"" + kind + "\",\"source\":\"" + source + "\",\"value\":"
                + value + ",\"unit\":\"" + unit + "\",\"measuredAt\":\"" + at + "\"}";
    }

    private String startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Ferm %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
        return orderId;
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit, String attributes)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit + "\",\"attributes\":"
                                + attributes + "}"))
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
