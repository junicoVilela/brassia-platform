package br.com.brew.brassia.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A fixture compartilhada tem teste próprio (DEB-SAL-003).
 *
 * <p><strong>Por que ela precisa de um.</strong> Uma fixture que constrói errado faz dezenas de testes
 * mentirem juntos — e todos passam. Este teste é o único lugar onde o que ela produz é verificado como
 * resultado, e não usado como pressuposto: se o caminho até o lote acabado mudar, a falha aparece aqui,
 * com nome, em vez de espalhada em quatro classes que não são sobre envase.
 */
@SpringBootTest
@Testcontainers
class BrewScenarioIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;
    BrewScenario scenario;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        scenario = new BrewScenario(mockMvc);
    }

    @Test
    void constroiUmLoteDeProdutoAcabadoDeVerdade() throws Exception {
        // Nove passos, e nenhum dispensável: o sistema recusa cada atalho de propósito.
        var session = scenario.login();
        var lot = scenario.finishedLot(session);

        assertThat(lot.id()).isNotBlank();
        assertThat(lot.code()).isNotBlank();

        mockMvc.perform(get("/api/v1/packaging/finished-lots").param("batchId", lot.batchId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].units", Matchers.is(780)));
    }

    @Test
    void oLoteRecemEnvasadoAindaNaoEVendavel() throws Exception {
        // Falta a assinatura da qualidade. A fixture entrega o lote CRU de propósito: quem precisa dele
        // vendável pede vendável, e quem testa o impedimento precisa do estado anterior.
        var session = scenario.login();
        var lot = scenario.finishedLot(session);

        mockMvc.perform(get("/api/v1/packaging/finished-lots/" + lot.id() + "/sale-status")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellable", Matchers.is(false)))
                .andExpect(jsonPath("$.blocker.code", Matchers.is("not_released")));
    }

    @Test
    void oLoteVendavelPassouPelaLiberacaoEPelaValidade() throws Exception {
        // As três condições da SAL-001-B: liberado, dentro da validade, sem quarentena. A fixture faz as
        // duas primeiras acontecerem pelos endpoints reais — validade desconhecida não é validade em dia.
        var session = scenario.login();
        var lot = scenario.sellableLot(session);

        mockMvc.perform(get("/api/v1/packaging/finished-lots/" + lot.id() + "/sale-status")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellable", Matchers.is(true)))
                .andExpect(jsonPath("$.blocker").doesNotExist())
                .andExpect(jsonPath("$.bestBefore").exists());
    }
}
