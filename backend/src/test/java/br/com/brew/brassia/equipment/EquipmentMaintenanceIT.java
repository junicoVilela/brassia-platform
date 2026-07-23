package br.com.brew.brassia.equipment;

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
class EquipmentMaintenanceIT {

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
    void schedulesChecksAvailabilityThenCancelReleases() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m1");

        var scheduled = mockMvc.perform(post(base(equipmentId) + "/maintenance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"kind":"MAINTENANCE","startAt":"2026-08-01T08:00:00Z",
                                 "endAt":"2026-08-01T12:00:00Z","notes":"limpeza CIP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SCHEDULED")))
                .andReturn().getResponse().getContentAsString();
        var maintenanceId = JSON.readTree(scheduled).get("id").asText();

        mockMvc.perform(get(base(equipmentId) + "/maintenance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind", is("MAINTENANCE")));

        // Indisponível dentro da janela; disponível fora.
        mockMvc.perform(get(base(equipmentId) + "/availability").session(session)
                        .param("from", "2026-08-01T09:00:00Z").param("to", "2026-08-01T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available", is(false)));
        mockMvc.perform(get(base(equipmentId) + "/availability").session(session)
                        .param("from", "2026-08-01T13:00:00Z").param("to", "2026-08-01T14:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available", is(true)));

        // Cancelar libera o equipamento.
        mockMvc.perform(post(base(equipmentId) + "/maintenance/" + maintenanceId + "/cancel")
                        .session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(base(equipmentId) + "/availability").session(session)
                        .param("from", "2026-08-01T09:00:00Z").param("to", "2026-08-01T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    void rejectsOverlapAndCalibrationWithoutInstrument() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m2");

        mockMvc.perform(post(base(equipmentId) + "/maintenance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"kind":"MAINTENANCE","startAt":"2026-08-02T08:00:00Z","endAt":"2026-08-02T12:00:00Z"}
                                """))
                .andExpect(status().isCreated());

        // Sobreposição → equipamento indisponível → 409.
        mockMvc.perform(post(base(equipmentId) + "/maintenance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"kind":"MAINTENANCE","startAt":"2026-08-02T11:00:00Z","endAt":"2026-08-02T13:00:00Z"}
                                """))
                .andExpect(status().isConflict());

        // Calibração sem instrumento → 400.
        mockMvc.perform(post(base(equipmentId) + "/maintenance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"kind":"CALIBRATION","startAt":"2026-08-03T08:00:00Z","endAt":"2026-08-03T09:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());

        // Fim não posterior ao início → 400.
        mockMvc.perform(post(base(equipmentId) + "/maintenance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"kind":"MAINTENANCE","startAt":"2026-08-04T10:00:00Z","endAt":"2026-08-04T10:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesScheduleWithoutPermission() throws Exception {
        mockMvc.perform(post(base(UUID.randomUUID()) + "/maintenance")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("equipment.read")))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"kind":"MAINTENANCE","startAt":"2026-08-01T08:00:00Z","endAt":"2026-08-01T09:00:00Z"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesMaintenanceByBrewery() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-m3");
        mockMvc.perform(post(base(equipmentId) + "/maintenance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"kind":"CALIBRATION","instrument":"pHmetro-01","startAt":"2026-08-05T08:00:00Z",
                                 "endAt":"2026-08-05T09:00:00Z"}
                                """))
                .andExpect(status().isCreated());

        var other = principal(UUID.randomUUID(), Set.of("equipment.read"));
        mockMvc.perform(get(base(equipmentId) + "/maintenance").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    private String createEquipment(MockHttpSession session, String code) throws Exception {
        var body = mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String base(Object equipmentId) {
        return "/api/v1/equipment/" + equipmentId;
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
