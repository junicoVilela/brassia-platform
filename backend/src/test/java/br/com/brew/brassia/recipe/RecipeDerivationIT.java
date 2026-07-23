package br.com.brew.brassia.recipe;

import static org.hamcrest.Matchers.greaterThan;
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
class RecipeDerivationIT {

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
    void clonesScalesAndCompares() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-da"); // capacidade 500
        var baseId = createRecipe(session, equipmentId, "Base Beer", 400);

        // Clonar → novo rascunho independente.
        var clone = mockMvc.perform(post("/api/v1/recipes/" + baseId + "/clone").session(session).with(csrf())
                        .contentType("application/json").content("{\"name\":\"Base Beer Clone\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString();
        var cloneId = JSON.readTree(clone).get("id").asText();

        // Escalar para 500 L (dentro da capacidade).
        var scaled = mockMvc.perform(post("/api/v1/recipes/" + baseId + "/scale").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Base Beer 500\",\"batchVolumeLiters\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString();
        var scaledId = JSON.readTree(scaled).get("id").asText();

        // Comparar base × escalada → diferenças por campo (nome, volume, item de mostura).
        mockMvc.perform(get("/api/v1/recipes/" + baseId + "/compare").param("other", scaledId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.differences[*].field", hasItem("batchVolumeLiters")))
                .andExpect(jsonPath("$.differences.length()", greaterThan(0)));

        // Comparar clone × base (mesma fórmula, só o nome muda) → apenas 'name'.
        mockMvc.perform(get("/api/v1/recipes/" + cloneId + "/compare").param("other", baseId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.differences.length()", is(1)))
                .andExpect(jsonPath("$.differences[0].field", is("name")));
    }

    @Test
    void scaleRejectsAboveCapacityAndDeniesWithoutPermission() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-db");
        var baseId = createRecipe(session, equipmentId, "Cap Beer", 400);

        // Escalar acima da capacidade (600 > 500) → 400.
        mockMvc.perform(post("/api/v1/recipes/" + baseId + "/scale").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Cap Beer Big\",\"batchVolumeLiters\":600}"))
                .andExpect(status().isBadRequest());

        // Clonar sem permissão → 403.
        mockMvc.perform(post("/api/v1/recipes/" + baseId + "/clone")
                        .with(authentication(principal(Set.of("recipe.read")))).with(csrf())
                        .contentType("application/json").content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    private String createEquipment(MockHttpSession session, String code) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createRecipe(MockHttpSession session, String equipmentId, String name, int batch)
            throws Exception {
        var content = """
                {"name":"%s","equipmentId":"%s","batchVolumeLiters":%d,"boilTimeMinutes":60,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":30,"unit":"G","timingMinutes":60}]}
                """.formatted(name, equipmentId, batch, UUID.randomUUID(), UUID.randomUUID());
        return idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
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

    private Authentication principal(Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
