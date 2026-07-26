package br.com.brew.brassia.planning;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.planning.application.port.outbound.ScheduleEntryRepository;
import br.com.brew.brassia.planning.domain.ScheduleEntry;
import br.com.brew.brassia.planning.domain.ScheduleWindow;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
class ScheduleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String START = "2026-08-01T08:00:00Z";
    private static final String END = "2026-08-01T14:00:00Z";
    private static final String OVERLAP_START = "2026-08-01T12:00:00Z";
    private static final String OVERLAP_END = "2026-08-01T16:00:00Z";
    private static final String RANGE_FROM = "2026-08-01T00:00:00Z";
    private static final String RANGE_TO = "2026-08-02T00:00:00Z";

    @Autowired WebApplicationContext context;
    @Autowired ScheduleEntryRepository scheduleRepository;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void plansListsAndBlocksEquipmentConflict() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-s1");
        var recipeId = publishedRecipe(session, equipmentId, "Agendável");

        // Cria a entrada da agenda.
        mockMvc.perform(post("/api/v1/planning/schedule").session(session).with(csrf())
                        .contentType("application/json").content(scheduleBody(recipeId, equipmentId, START, END)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PLANNED")));

        // Aparece na listagem do período.
        mockMvc.perform(get("/api/v1/planning/schedule").session(session)
                        .param("from", RANGE_FROM).param("to", RANGE_TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].equipmentId", is(equipmentId)));

        // Janela sobreposta no mesmo equipamento → 409 (também cobre repetição).
        mockMvc.perform(post("/api/v1/planning/schedule").session(session).with(csrf())
                        .contentType("application/json")
                        .content(scheduleBody(recipeId, equipmentId, OVERLAP_START, OVERLAP_END)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyPublishedRecipeCanBeScheduled() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-s2");
        var draftId = createRecipe(session, equipmentId, "Rascunho"); // não publicada

        mockMvc.perform(post("/api/v1/planning/schedule").session(session).with(csrf())
                        .contentType("application/json").content(scheduleBody(draftId, equipmentId, START, END)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simulateDetectsConflictWithoutPersisting() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-s3");
        var recipeId = publishedRecipe(session, equipmentId, "Simulável");
        // Dia próprio (03/08) para isolar a contagem na cervejaria compartilhada do admin.
        var start = "2026-08-03T08:00:00Z";
        var end = "2026-08-03T14:00:00Z";
        var overlapStart = "2026-08-03T12:00:00Z";
        var overlapEnd = "2026-08-03T16:00:00Z";
        var from = "2026-08-03T00:00:00Z";
        var to = "2026-08-04T00:00:00Z";

        mockMvc.perform(post("/api/v1/planning/schedule").session(session).with(csrf())
                        .contentType("application/json").content(scheduleBody(recipeId, equipmentId, start, end)))
                .andExpect(status().isCreated());

        // Simulação com sobreposição → hasConflict=true.
        mockMvc.perform(post("/api/v1/planning/schedule/simulate").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"equipmentId\":\"" + equipmentId + "\",\"scheduledStart\":\"" + overlapStart
                                + "\",\"scheduledEnd\":\"" + overlapEnd + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict", is(true)))
                .andExpect(jsonPath("$.conflicts.length()", is(1)));

        // A simulação não persistiu nada: continua 1 entrada no dia.
        mockMvc.perform(get("/api/v1/planning/schedule").session(session)
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));
    }

    @Test
    void rejectsInvalidWindow() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-s4");
        var recipeId = publishedRecipe(session, equipmentId, "Janela ruim");

        // fim antes do início → 400.
        mockMvc.perform(post("/api/v1/planning/schedule").session(session).with(csrf())
                        .contentType("application/json").content(scheduleBody(recipeId, equipmentId, END, START)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesWithoutManagePermission() throws Exception {
        // Principal só com leitura → criar agenda dá 403.
        mockMvc.perform(post("/api/v1/planning/schedule")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.schedule.read"))))
                        .with(csrf()).contentType("application/json")
                        .content(scheduleBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), START, END)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingIsScopedByBrewery() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-s5");
        var recipeId = publishedRecipe(session, equipmentId, "Só minha cervejaria");
        var start = "2026-08-05T08:00:00Z";
        var end = "2026-08-05T14:00:00Z";
        var from = "2026-08-05T00:00:00Z";
        var to = "2026-08-06T00:00:00Z";
        mockMvc.perform(post("/api/v1/planning/schedule").session(session).with(csrf())
                        .contentType("application/json").content(scheduleBody(recipeId, equipmentId, start, end)))
                .andExpect(status().isCreated());

        // Outra cervejaria (principal com brewery diferente) não enxerga a entrada.
        mockMvc.perform(get("/api/v1/planning/schedule")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.schedule.read"))))
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void databaseBackstopRejectsConcurrentOverlap() {
        // Simula a corrida check-then-insert: dois inserts diretos com janelas
        // sobrepostas no mesmo equipamento. A exclusion constraint rejeita o 2º.
        var brewery = UUID.randomUUID();
        var equipment = UUID.randomUUID();
        var first = ScheduleEntry.plan(brewery, UUID.randomUUID(), equipment, UUID.randomUUID(),
                new BigDecimal("40"), new BigDecimal("50"),
                new ScheduleWindow(Instant.parse(START), Instant.parse(END)));
        var overlapping = ScheduleEntry.plan(brewery, UUID.randomUUID(), equipment, UUID.randomUUID(),
                new BigDecimal("40"), new BigDecimal("50"),
                new ScheduleWindow(Instant.parse(OVERLAP_START), Instant.parse(OVERLAP_END)));

        scheduleRepository.insert(first);
        // Insert direto (sem o pré-check do caso de uso): a exclusion constraint do banco
        // rejeita a sobreposição — é o backstop que fecha a corrida de concorrência.
        assertThatThrownBy(() -> scheduleRepository.insert(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- helpers ---

    private static String scheduleBody(String recipeId, String equipmentId, String start, String end) {
        return "{\"recipeId\":\"" + recipeId + "\",\"equipmentId\":\"" + equipmentId
                + "\",\"assignedUserId\":\"" + UUID.randomUUID() + "\",\"plannedVolumeLiters\":400,"
                + "\"scheduledStart\":\"" + start + "\",\"scheduledEnd\":\"" + end + "\"}";
    }

    private String publishedRecipe(MockHttpSession session, String equipmentId, String name) throws Exception {
        var recipeId = createRecipe(session, equipmentId, name);
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return recipeId;
    }

    private String createEquipment(MockHttpSession session, String code) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"BH\",\"capacityLiters\":500,"
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
