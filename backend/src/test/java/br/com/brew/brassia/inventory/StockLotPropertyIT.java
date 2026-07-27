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
class StockLotPropertyIT {

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
    void recordsAndListsLotProperties() throws Exception {
        var session = login();
        var lot = receiveLot(session, "prop-a");

        mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/properties").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"properties\":[{\"property\":\"alfaAcido\",\"value\":12.5,\"unit\":\"%\","
                                + "\"source\":\"MANUAL\",\"confidence\":\"HIGH\"}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/properties").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].property", is("alfaAcido")))
                .andExpect(jsonPath("$[0].value", is(12.5)))
                .andExpect(jsonPath("$[0].unit", is("%")))
                .andExpect(jsonPath("$[0].source", is("MANUAL")))
                .andExpect(jsonPath("$[0].confidence", is("HIGH")));
    }

    @Test
    void writeOnceRejectsSameProperty() throws Exception {
        var session = login();
        var lot = receiveLot(session, "prop-b");
        var body = "{\"properties\":[{\"property\":\"extrato\",\"value\":80,\"unit\":\"%\","
                + "\"source\":\"IMPORTED\",\"confidence\":\"MEDIUM\"}]}";

        mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/properties").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        // Regravar a mesma propriedade → conflito (write-once).
        mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/properties").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void receivesLotWithInlineProperties() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "prop-c");
        var supplierId = createSupplier(session, "prop-c");
        var lot = idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":10,\"unit\":\"KG\",\"unitCost\":4.5,\"inspection\":\"APPROVED\","
                                + "\"properties\":[{\"property\":\"umidade\",\"value\":4.2,\"unit\":\"%\","
                                + "\"source\":\"MANUAL\",\"confidence\":\"HIGH\"}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/properties").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].property", hasItem("umidade")));
    }

    @Test
    void missingLotIsBadRequest() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/inventory/lots/" + UUID.randomUUID() + "/properties").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"properties\":[{\"property\":\"x\",\"value\":1,\"source\":\"MANUAL\","
                                + "\"confidence\":\"LOW\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        var session = login();
        var lot = receiveLot(session, "prop-d");

        mockMvc.perform(post("/api/v1/inventory/lots/" + lot + "/properties")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("inventory.lot.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"properties\":[{\"property\":\"x\",\"value\":1,\"source\":\"MANUAL\","
                                + "\"confidence\":\"LOW\"}]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/properties")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("inventory.lot.manage")))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String receiveLot(MockHttpSession session, String sfx) throws Exception {
        var ingredientId = createIngredient(session, sfx);
        var supplierId = createSupplier(session, sfx);
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":10,\"unit\":\"KG\",\"unitCost\":4.5,\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createSupplier(MockHttpSession session, String sfx) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String sfx) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"MALT\",\"code\":\"m-" + sfx + "\",\"name\":\"m-" + sfx
                                + "\",\"useUnit\":\"KG\",\"purchaseUnit\":\"KG\",\"attributes\":{\"potentialSg\":\"1.037\"}}"))
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
