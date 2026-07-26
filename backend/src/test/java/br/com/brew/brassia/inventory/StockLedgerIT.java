package br.com.brew.brassia.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class StockLedgerIT {

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
    void receiveCreatesEntryAndBalance() throws Exception {
        var session = login();
        var lotId = receiveLot(session, "led-a", 25);

        mockMvc.perform(get("/api/v1/inventory/lots/" + lotId + "/balance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand", Matchers.is(25.0)))
                .andExpect(jsonPath("$.available", Matchers.is(25.0)));

        // O ledger tem a ENTRY do recebimento.
        mockMvc.perform(get("/api/v1/inventory/lots/" + lotId + "/movements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", Matchers.hasItem("ENTRY")));
    }

    @Test
    void consumptionReducesBalanceAndRejectsNegative() throws Exception {
        var session = login();
        var lotId = receiveLot(session, "led-b", 25);

        mockMvc.perform(post("/api/v1/inventory/lots/" + lotId + "/movements").session(session).with(csrf())
                        .contentType("application/json").content("{\"type\":\"CONSUMPTION\",\"quantity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand", Matchers.is(15.0)));

        // Consumir além do saldo → 409.
        mockMvc.perform(post("/api/v1/inventory/lots/" + lotId + "/movements").session(session).with(csrf())
                        .contentType("application/json").content("{\"type\":\"CONSUMPTION\",\"quantity\":20}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adjustmentRequiresReason() throws Exception {
        var session = login();
        var lotId = receiveLot(session, "led-c", 25);

        mockMvc.perform(post("/api/v1/inventory/lots/" + lotId + "/movements").session(session).with(csrf())
                        .contentType("application/json").content("{\"type\":\"ADJUSTMENT_OUT\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsReservationViaManualEndpoint() throws Exception {
        var session = login();
        var lotId = receiveLot(session, "led-d", 25);
        // RESERVATION é do fluxo FEFO (STK-003), não é permitida no endpoint manual.
        mockMvc.perform(post("/api/v1/inventory/lots/" + lotId + "/movements").session(session).with(csrf())
                        .contentType("application/json").content("{\"type\":\"RESERVATION\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doubleSpendIsPreventedUnderConcurrency() throws Exception {
        var session = login();
        var lotId = receiveLot(session, "led-e", 10);

        // Dois consumos concorrentes do saldo total → só um vence (lock pessimista).
        Callable<Integer> consumeAll = () -> mockMvc.perform(post("/api/v1/inventory/lots/" + lotId + "/movements")
                        .session(session).with(csrf())
                        .contentType("application/json").content("{\"type\":\"CONSUMPTION\",\"quantity\":10}"))
                .andReturn().getResponse().getStatus();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(consumeAll);
            Future<Integer> b = pool.submit(consumeAll);
            var statuses = List.of(a.get(), b.get());
            long ok = statuses.stream().filter(s -> s == 200).count();
            long conflict = statuses.stream().filter(s -> s == 409).count();
            org.assertj.core.api.Assertions.assertThat(ok).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(conflict).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // Saldo final = 0 (nunca negativo).
        mockMvc.perform(get("/api/v1/inventory/lots/" + lotId + "/balance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand", Matchers.is(0.0)));
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
}
