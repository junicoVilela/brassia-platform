package br.com.brew.brassia.equipment;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class EquipmentIT {

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
    void createsUpdatesKeepsRevisionAndEnforcesOptimisticLock() throws Exception {
        var session = login();

        var created = mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"bh-500","name":"Brewhouse 500L","capacityLiters":500,
                                 "deadSpaceLiters":20,"mashEfficiencyPercent":72.5,"boilOffLitersPerHour":8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("BH-500")))
                .andExpect(jsonPath("$.version", is(0)))
                .andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/equipment").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code", hasItem("BH-500")));

        mockMvc.perform(put("/api/v1/equipment/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Brewhouse 500L v2","capacityLiters":480,"deadSpaceLiters":30,
                                 "mashEfficiencyPercent":70,"boilOffLitersPerHour":9,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityLiters", is(480.0)))
                .andExpect(jsonPath("$.version", is(1)));

        // Revisão 0 preserva os valores originais (histórico imutável).
        mockMvc.perform(get("/api/v1/equipment/" + id + "/revisions/0").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityLiters", is(500.0)))
                .andExpect(jsonPath("$.version", is(0)));

        // Versão stale → 409.
        mockMvc.perform(put("/api/v1/equipment/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"X","capacityLiters":500,"deadSpaceLiters":10,
                                 "mashEfficiencyPercent":70,"boilOffLitersPerHour":8,"version":0}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDeadSpaceAboveCapacityAndDuplicateCode() throws Exception {
        var session = login();

        // Perda acima da capacidade → 400.
        mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"kettle","name":"Kettle","capacityLiters":100,"deadSpaceLiters":150,
                                 "mashEfficiencyPercent":70,"boilOffLitersPerHour":8}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"ferm-1","name":"Fermentador 1","capacityLiters":600,"deadSpaceLiters":10,
                                 "mashEfficiencyPercent":75,"boilOffLitersPerHour":0}
                                """))
                .andExpect(status().isCreated());

        // Código repetido na mesma cervejaria → 409.
        mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"FERM-1","name":"Outro","capacityLiters":600,"deadSpaceLiters":10,
                                 "mashEfficiencyPercent":75,"boilOffLitersPerHour":0}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/equipment")
                        .with(authentication(principal(UUID.randomUUID(), Set.of()))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"x","name":"X","capacityLiters":10,"deadSpaceLiters":1,
                                 "mashEfficiencyPercent":70,"boilOffLitersPerHour":0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesEquipmentByBrewery() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"hlt-1","name":"HLT","capacityLiters":300,"deadSpaceLiters":5,
                                 "mashEfficiencyPercent":70,"boilOffLitersPerHour":0}
                                """))
                .andExpect(status().isCreated());

        var other = principal(UUID.randomUUID(), Set.of("equipment.read"));
        mockMvc.perform(get("/api/v1/equipment").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
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
