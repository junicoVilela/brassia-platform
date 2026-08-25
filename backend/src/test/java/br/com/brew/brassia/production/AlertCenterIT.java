package br.com.brew.brassia.production;

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
class AlertCenterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createsListsAndConfirmsIdempotently() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();

        var alertId = idOf(mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/alerts")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"kind\":\"DECISION\",\"message\":\"Decidir sobre correção de OG\","
                                + "\"plannedAt\":\"2026-07-27T12:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // Persistido: aparece na timeline como PENDING.
        mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/alerts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind", is("DECISION")))
                .andExpect(jsonPath("$[0].status", is("PENDING")));

        // Confirmar → CONFIRMED.
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/alerts/" + alertId + "/confirm")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        // Confirmar de novo é idempotente (segue CONFIRMED, sem erro).
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/alerts/" + alertId + "/confirm")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    void doesNotLeakAlertsAcrossBrewery() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/alerts").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"ADDITION\",\"message\":\"Adicionar lúpulo de aroma\"}"))
                .andExpect(status().isCreated());

        // Outra cervejaria não enxerga sequer o lote → 404 `unknown_batch` (DEB-PRD-002). Era 400 antes,
        // o que mandava conferir um pedido que estava correto. O que não muda, e é o que importa, é a
        // resposta não carregar nada do lote.
        mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/alerts")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("production.batch.read")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_batch")));
    }

    @Test
    void alertaDeOutroLoteRespondeComoAlertaQueNaoExiste() throws Exception {
        // DEB-PRD-002: os dois casos respondem igual, e de propósito. O endereço é o par lote+alerta;
        // dizer "existe, mas é de outro lote" confirmaria a existência do alerta para quem só tem o id.
        var session = login();
        var meu = startedBatch(session).get("id").asText();
        var outro = startedBatch(session).get("id").asText();
        var alertaDoOutro = idOf(mockMvc.perform(post("/api/v1/production/batches/" + outro + "/alerts")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"kind\":\"ADDITION\",\"message\":\"Lúpulo de aroma\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/production/batches/" + meu + "/alerts/" + alertaDoOutro + "/confirm")
                        .session(session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_alert")));

        mockMvc.perform(post("/api/v1/production/batches/" + meu + "/alerts/" + UUID.randomUUID() + "/confirm")
                        .session(session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_alert")));
    }

    @Test
    void deniesCreateWithoutManagePermission() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/alerts")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("production.batch.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"DECISION\",\"message\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private JsonNode startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String batchId = null;
        // A listagem passou a ser paginada (REL-002): o array vem em `content`.
        for (var node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                batchId = node.get("id").asText();
            }
        }
        var detail = mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(detail);
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
                {"name":"Al %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
