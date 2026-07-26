package br.com.brew.brassia.planning;

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
class MaterialRequirementIT {

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
    void explodesPublishedRecipeAdHoc() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m1");
        var recipeId = publishedRecipe(session, equipmentId, "Materiais Ad-hoc"); // batch 400 L, grão 20 KG

        // Volume 800 L = 2× a batelada → grão 40 KG (escala por volume).
        mockMvc.perform(post("/api/v1/planning/material-requirement").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[*].requiredQuantity", hasItem(40.0000)))
                .andExpect(jsonPath("$[*].unit", hasItem("KG")));
    }

    @Test
    void appliesLossPercent() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m2");
        var recipeId = publishedRecipe(session, equipmentId, "Materiais Perda");

        // 400 L (mesma batelada) + 10% de perda → grão 22 KG.
        mockMvc.perform(post("/api/v1/planning/material-requirement").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400,\"lossPercent\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].requiredQuantity", hasItem(22.0000)));
    }

    @Test
    void materialsForScheduleEntryUsePlannedVolume() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m3");
        var recipeId = publishedRecipe(session, equipmentId, "Materiais Agenda");
        var entryId = idOf(mockMvc.perform(post("/api/v1/planning/schedule").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"equipmentId\":\"" + equipmentId
                                + "\",\"assignedUserId\":\"" + UUID.randomUUID() + "\",\"plannedVolumeLiters\":400,"
                                + "\"scheduledStart\":\"2026-09-01T08:00:00Z\",\"scheduledEnd\":\"2026-09-01T14:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // Volume planejado = 400 L → grão 20 KG.
        mockMvc.perform(get("/api/v1/planning/schedule/" + entryId + "/materials").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[*].requiredQuantity", hasItem(20.0000)));
    }

    @Test
    void rejectsUnpublishedRecipe() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m4");
        var draftId = createRecipe(session, equipmentId, "Rascunho Mat");

        mockMvc.perform(post("/api/v1/planning/material-requirement").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + draftId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesWithoutPermissionAndAcrossBrewery() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m5");
        var recipeId = publishedRecipe(session, equipmentId, "Materiais Tenant");

        // Sem permissão → 403.
        mockMvc.perform(post("/api/v1/planning/material-requirement")
                        .with(authentication(principal(UUID.randomUUID(), Set.of()))).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isForbidden());

        // Outra cervejaria não enxerga a receita publicada → 400 (inexistente naquele tenant).
        mockMvc.perform(post("/api/v1/planning/material-requirement")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.schedule.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String publishedRecipe(MockHttpSession session, String equipmentId, String name) throws Exception {
        var recipeId = createRecipe(session, equipmentId, name);
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return recipeId;
    }

    private String createEquipment(MockHttpSession session, String code) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"BH\",\"capacityLiters\":900,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createRecipe(MockHttpSession session, String equipmentId, String name) throws Exception {
        var content = """
                {"name":"%s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":30,"unit":"G","timingMinutes":60}]}
                """.formatted(name, equipmentId, UUID.randomUUID(), UUID.randomUUID());
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

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
