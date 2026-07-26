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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hamcrest.Matchers;
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
class StockReservationIT {

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
    void allocatesFefoAcrossLots() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "fefo-a");
        var supplierId = createSupplier(session, "fa");
        // Lote que vence antes deve ser consumido primeiro.
        var soon = receiveLot(session, ingredientId, supplierId, 10, "2026-10-01", "APPROVED");
        var later = receiveLot(session, ingredientId, supplierId, 10, "2027-10-01", "APPROVED");

        mockMvc.perform(post("/api/v1/inventory/reservations").session(session).with(csrf())
                        .contentType("application/json")
                        .content(reserveBody(ingredientId, 15, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.length()", Matchers.is(2)))
                .andExpect(jsonPath("$.allocations[0].lotId", Matchers.is(soon)))
                .andExpect(jsonPath("$.allocations[0].quantity", Matchers.is(10.0)))
                .andExpect(jsonPath("$.allocations[1].lotId", Matchers.is(later)))
                .andExpect(jsonPath("$.allocations[1].quantity", Matchers.is(5.0)));

        // O disponível dos lotes caiu (reservado).
        mockMvc.perform(get("/api/v1/inventory/lots/" + soon + "/balance").session(session))
                .andExpect(jsonPath("$.available", Matchers.is(0.0)))
                .andExpect(jsonPath("$.reserved", Matchers.is(10.0)));
    }

    @Test
    void skipsExpiredAndBlockedLots() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "fefo-b");
        var supplierId = createSupplier(session, "fb");
        receiveLot(session, ingredientId, supplierId, 10, "2020-01-01", "APPROVED"); // vencido
        receiveLot(session, ingredientId, supplierId, 10, "2027-10-01", "BLOCKED");  // bloqueado
        var ok = receiveLot(session, ingredientId, supplierId, 8, "2027-10-01", "APPROVED");

        // Só o lote válido (8) está disponível → reservar 8 aloca só nele; 9 estoura.
        mockMvc.perform(post("/api/v1/inventory/reservations").session(session).with(csrf())
                        .contentType("application/json").content(reserveBody(ingredientId, 8, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.length()", Matchers.is(1)))
                .andExpect(jsonPath("$.allocations[0].lotId", Matchers.is(ok)));

        mockMvc.perform(post("/api/v1/inventory/reservations").session(session).with(csrf())
                        .contentType("application/json").content(reserveBody(ingredientId, 9, UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void releaseFreesReservations() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "fefo-c");
        var supplierId = createSupplier(session, "fc");
        var lot = receiveLot(session, ingredientId, supplierId, 10, "2027-10-01", "APPROVED");
        var orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/reservations").session(session).with(csrf())
                        .contentType("application/json").content(reserveBody(ingredientId, 10, orderId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/balance").session(session))
                .andExpect(jsonPath("$.available", Matchers.is(0.0)));

        mockMvc.perform(post("/api/v1/inventory/reservations/release").session(session).with(csrf())
                        .contentType("application/json").content("{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/balance").session(session))
                .andExpect(jsonPath("$.available", Matchers.is(10.0)))
                .andExpect(jsonPath("$.reserved", Matchers.is(0.0)));
    }

    @Test
    void concurrentReservationsDoNotDoubleSpend() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "fefo-d");
        var supplierId = createSupplier(session, "fd");
        var lot = receiveLot(session, ingredientId, supplierId, 10, "2027-10-01", "APPROVED");

        Callable<Integer> reserveAll = () -> mockMvc.perform(post("/api/v1/inventory/reservations")
                        .session(session).with(csrf())
                        .contentType("application/json").content(reserveBody(ingredientId, 10, UUID.randomUUID())))
                .andReturn().getResponse().getStatus();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(reserveAll);
            Future<Integer> b = pool.submit(reserveAll);
            var statuses = List.of(a.get(), b.get());
            org.assertj.core.api.Assertions.assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // Reservado nunca ultrapassa o disponível.
        mockMvc.perform(get("/api/v1/inventory/lots/" + lot + "/balance").session(session))
                .andExpect(jsonPath("$.reserved", Matchers.is(10.0)))
                .andExpect(jsonPath("$.available", Matchers.is(0.0)));
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "fefo-e");
        var supplierId = createSupplier(session, "fe");
        receiveLot(session, ingredientId, supplierId, 10, "2027-10-01", "APPROVED");

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .with(authentication(principal(UUID.randomUUID(), java.util.Set.of("inventory.lot.read"))))
                        .with(csrf()).contentType("application/json")
                        .content(reserveBody(ingredientId, 1, UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private static String reserveBody(String ingredientId, int qty, UUID orderId) {
        return "{\"ingredientId\":\"" + ingredientId + "\",\"quantity\":" + qty + ",\"unit\":\"KG\",\"orderId\":\""
                + orderId + "\"}";
    }

    private String receiveLot(MockHttpSession session, String ingredientId, String supplierId, int qty,
            String expiry, String inspection) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + qty + ",\"unit\":\"KG\",\"unitCost\":4.5,\"expiryDate\":\""
                                + expiry + "\",\"inspection\":\"" + inspection + "\"}"))
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

    private Authentication principal(UUID breweryId, java.util.Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", java.util.Set.of());
    }
}
