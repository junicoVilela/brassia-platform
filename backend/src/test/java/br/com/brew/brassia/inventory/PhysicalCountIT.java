package br.com.brew.brassia.inventory;

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
class PhysicalCountIT {

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
    void approveAdjustsDownAndKeepsCount() throws Exception {
        var session = login();
        var lot = receiveLot(session, "c-a", 25);
        var countId = createCount(session, lot, 20); // contou menos → sobra ajustada para baixo

        // Diferença registrada = 20 − 25 = −5.
        mockMvc.perform(get("/api/v1/inventory/counts/" + countId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].difference", is(-5.0)));

        mockMvc.perform(post("/api/v1/inventory/counts/" + countId + "/approve").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.adjustments", is(1)));

        // Saldo passou a ser o contado (20); a contagem original permanece (20).
        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(20.0)));
        mockMvc.perform(get("/api/v1/inventory/counts/" + countId).session(session))
                .andExpect(jsonPath("$.lines[0].countedQuantity", is(20.0)))
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    void approveAdjustsUp() throws Exception {
        var session = login();
        var lot = receiveLot(session, "c-b", 10);
        var countId = createCount(session, lot, 13); // contou mais → ajusta para cima

        mockMvc.perform(post("/api/v1/inventory/counts/" + countId + "/approve").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(13.0)));
    }

    @Test
    void reapproveIsConflict() throws Exception {
        var session = login();
        var lot = receiveLot(session, "c-c", 10);
        var countId = createCount(session, lot, 10);
        mockMvc.perform(post("/api/v1/inventory/counts/" + countId + "/approve").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/counts/" + countId + "/approve").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void approveRequiresApprovePermission() throws Exception {
        var session = login();
        var lot = receiveLot(session, "c-d", 10);
        var countId = createCount(session, lot, 8);

        // Só count.read/manage, sem count.approve → 403.
        mockMvc.perform(post("/api/v1/inventory/counts/" + countId + "/approve")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("inventory.count.read", "inventory.count.manage")))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIsScopedByBrewery() throws Exception {
        var session = login();
        var lot = receiveLot(session, "c-e", 10);
        createCount(session, lot, 9);

        mockMvc.perform(get("/api/v1/inventory/counts")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("inventory.count.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // --- helpers ---

    private String createCount(MockHttpSession session, String lotId, int counted) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/inventory/counts").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"lines\":[{\"lotId\":\"" + lotId + "\",\"countedQuantity\":" + counted + "}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String receiveLot(MockHttpSession session, String sfx, int qty) throws Exception {
        var ingredientId = idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"MALT\",\"code\":\"m-" + sfx + "\",\"name\":\"m-" + sfx
                                + "\",\"useUnit\":\"KG\",\"purchaseUnit\":\"KG\",\"attributes\":{\"potentialSg\":\"1.037\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + qty + ",\"unit\":\"KG\",\"unitCost\":4.5,\"inspection\":\"APPROVED\"}"))
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
