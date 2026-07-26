package br.com.brew.brassia.inventory;

import static org.hamcrest.Matchers.hasItem;
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
class StockLotIT {

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
    void receivesApprovedLotAsAvailable() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "MALT", "pil-a", "{\"potentialSg\":\"1.037\"}");
        var supplierId = createSupplier(session, "Maltaria Sul", "MSUL-A");

        var body = mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json").content(lotBody(ingredientId, supplierId, 25, "APPROVED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available", is(true)))
                .andExpect(jsonPath("$.inspection", is("APPROVED")))
                .andReturn().getResponse().getContentAsString();
        JSON.readTree(body);

        // A cervejaria do admin é compartilhada entre os testes; basta o lote aparecer disponível.
        mockMvc.perform(get("/api/v1/inventory/lots").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].available", hasItem(true)))
                .andExpect(jsonPath("$[*].inspection", hasItem("APPROVED")));
    }

    @Test
    void blockedLotIsNotAvailable() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "MALT", "pil-b", "{\"potentialSg\":\"1.037\"}");
        var supplierId = createSupplier(session, "Maltaria B", "MSUL-B");

        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json").content(lotBody(ingredientId, supplierId, 25, "BLOCKED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available", is(false)));
    }

    @Test
    void rejectsNonPositiveQuantity() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "MALT", "pil-c", "{\"potentialSg\":\"1.037\"}");
        var supplierId = createSupplier(session, "Maltaria C", "MSUL-C");

        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json").content(lotBody(ingredientId, supplierId, 0, "APPROVED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownSupplierOrIngredient() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "MALT", "pil-d", "{\"potentialSg\":\"1.037\"}");
        var supplierId = createSupplier(session, "Maltaria D", "MSUL-D");

        // Fornecedor inexistente.
        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json").content(lotBody(ingredientId, UUID.randomUUID(), 10, "APPROVED")))
                .andExpect(status().isBadRequest());
        // Ingrediente inexistente.
        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json").content(lotBody(UUID.randomUUID(), supplierId, 10, "APPROVED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesWithoutPermissionAndScopesByBrewery() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "MALT", "pil-e", "{\"potentialSg\":\"1.037\"}");
        var supplierId = createSupplier(session, "Maltaria E", "MSUL-E");
        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json").content(lotBody(ingredientId, supplierId, 10, "APPROVED")))
                .andExpect(status().isCreated());

        // Sem permissão → 403.
        mockMvc.perform(post("/api/v1/inventory/lots")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("inventory.lot.read")))).with(csrf())
                        .contentType("application/json").content(lotBody(ingredientId, supplierId, 10, "APPROVED")))
                .andExpect(status().isForbidden());

        // Outra cervejaria não enxerga os lotes.
        mockMvc.perform(get("/api/v1/inventory/lots")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("inventory.lot.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // --- helpers ---

    private static String lotBody(Object ingredientId, Object supplierId, int qty, String inspection) {
        return "{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                + "\",\"supplierLotCode\":\"L-1\",\"quantity\":" + qty + ",\"unit\":\"KG\",\"unitCost\":4.5,"
                + "\"expiryDate\":\"2027-09-01\",\"inspection\":\"" + inspection + "\"}";
    }

    private String createSupplier(MockHttpSession session, String name, String code) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String code, String attributes)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"KG\",\"purchaseUnit\":\"KG\",\"attributes\":" + attributes + "}"))
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
