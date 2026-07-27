package br.com.brew.brassia.inventory;

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
import org.hamcrest.Matchers;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class NegativeStockOverrideIT {

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
    void authorizedOverrideAllowsNegativeBalance() throws Exception {
        var session = login();
        var lot = receiveLot(session, "ovr-a", 10);

        // Admin tem inventory.stock.override → consumir além do saldo com allowNegative deixa on_hand negativo.
        mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/movements").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"CONSUMPTION\",\"quantity\":15,\"allowNegative\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand", Matchers.is(-5.0)));

        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", Matchers.is(-5.0)));
    }

    @Test
    void overrideWithoutPermissionIsForbidden() throws Exception {
        var session = login();
        var lot = receiveLot(session, "ovr-b", 10);
        var brewery = breweryOf(session);

        mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/movements")
                        .with(authentication(principal(brewery, Set.of("inventory.lot.manage")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"CONSUMPTION\",\"quantity\":15,\"allowNegative\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void negativeWithoutOverrideStillConflicts() throws Exception {
        var session = login();
        var lot = receiveLot(session, "ovr-c", 10);

        mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/movements").session(session).with(csrf())
                        .contentType("application/json").content("{\"type\":\"CONSUMPTION\",\"quantity\":15}"))
                .andExpect(status().isConflict());
    }

    // --- helpers ---

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

    private UUID breweryOf(MockHttpSession session) {
        var ctx = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return ((SecurityPrincipal) ctx.getAuthentication().getPrincipal()).breweryId();
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
