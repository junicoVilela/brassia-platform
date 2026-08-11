package br.com.brew.brassia.equipment;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Estado de limpeza do equipamento (CLN-004-A).
 *
 * <p>O débito estava aberto desde a sprint 08: o evento de liberação era publicado e ninguém escutava.
 * Estes testes exercitam a volta inteira — usar suja, sujo recusa cerveja, ciclo liberado limpa — porque é
 * só com a volta fechada que a plataforma passa a cumprir o que já afirmava.
 */
@SpringBootTest
@Testcontainers
class EquipmentCleanlinessIT {

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
    @DisplayName("equipamento recém-cadastrado é limpo")
    void nasceLimpo() throws Exception {
        // Exigir um ciclo antes do primeiro uso obrigaria a registrar a limpeza de um tanque que acabou de
        // chegar — e é assim que se ensina alguém a burlar a regra.
        var session = login();
        var tanque = criarEquipamento(session);

        assertLimpeza(session, tanque, "CLEAN");
    }

    @Test
    @DisplayName("TRANSFERIR CERVEJA SUJA O TANQUE")
    void transferirSuja() throws Exception {
        var session = login();
        var tanque = criarEquipamento(session);
        var lote = criarLote(session);

        transferir(session, lote, tanque).andExpect(status().isCreated());

        assertLimpeza(session, tanque, "DIRTY");
    }

    @Test
    @DisplayName("O TANQUE SUJO RECUSA A PRÓXIMA CERVEJA, e diz desde quando está sujo")
    void sujoRecusa() throws Exception {
        // É a metade que faltava: sem ela o estado seria decorativo, e o fermentador receberia um lote
        // logo depois de esvaziar o anterior sem ninguém perguntar nada.
        var session = login();
        var tanque = criarEquipamento(session);
        transferir(session, criarLote(session), tanque).andExpect(status().isCreated());

        transferir(session, criarLote(session), tanque)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("equipment_not_clean")))
                .andExpect(jsonPath("$.soiledSince", notNullValue()));
    }

    @Test
    @DisplayName("O CICLO LIBERADO LIMPA, e a cerveja volta a entrar")
    void cicloLiberadoLimpa() throws Exception {
        // O consumidor que faltava desde a sprint 08. Sem ele, um tanque sujo nunca mais receberia nada.
        var session = login();
        var tanque = criarEquipamento(session);
        transferir(session, criarLote(session), tanque).andExpect(status().isCreated());
        assertLimpeza(session, tanque, "DIRTY");

        liberarLimpeza(session, tanque);

        assertLimpeza(session, tanque, "CLEAN");
        transferir(session, criarLote(session), tanque).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ciclo reprovado NÃO limpa: a liberação é que limpa, não a execução")
    void cicloReprovadoNaoLimpa() throws Exception {
        // Concluir o ciclo é ter feito a limpeza; liberar é ter conferido que funcionou. Se concluir
        // limpasse, um ATP acima do limite deixaria o tanque "limpo" com sujeira medida.
        var session = login();
        var tanque = criarEquipamento(session);
        transferir(session, criarLote(session), tanque).andExpect(status().isCreated());

        var ciclo = cicloConcluido(session, tanque);
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + ciclo + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":150,\"atpThreshold\":100,"
                                + "\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + ciclo + "/release").session(session).with(csrf()))
                .andExpect(status().isConflict());

        assertLimpeza(session, tanque, "DIRTY");
    }

    // --- helpers ---

    private void assertLimpeza(MockHttpSession session, String equipmentId, String esperado) throws Exception {
        var body = mockMvc.perform(get("/api/v1/equipment?page=0&size=200").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(body).get("content")) {
            if (node.get("id").asText().equals(equipmentId)) {
                org.assertj.core.api.Assertions.assertThat(node.get("cleanliness").asText())
                        .as("estado de limpeza do equipamento")
                        .isEqualTo(esperado);
                return;
            }
        }
        throw new AssertionError("equipamento não encontrado na listagem: " + equipmentId);
    }

    private org.springframework.test.web.servlet.ResultActions transferir(MockHttpSession session, String batchId,
            String equipmentId) throws Exception {
        return mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session)
                .with(csrf()).contentType("application/json")
                .content("{\"destinationEquipmentId\":\"" + equipmentId + "\",\"volumeLiters\":300,"
                        + "\"ogSg\":1.052,\"lossesLiters\":8}"));
    }

    private void liberarLimpeza(MockHttpSession session, String equipmentId) throws Exception {
        var ciclo = cicloConcluido(session, equipmentId);
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + ciclo + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,"
                                + "\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + ciclo + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private String cicloConcluido(MockHttpSession session, String equipmentId) throws Exception {
        var code = publicarPop(session);
        var body = mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(body).get("id").asText();
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + id + "/steps").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,"
                                + "\"measuredTimeMinutes\":20}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + id + "/complete").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        return id;
    }

    private String publicarPop(MockHttpSession session) throws Exception {
        var code = "CIP-" + UUID.randomUUID().toString().substring(0, 8);
        var step = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var created = mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP\",\"steps\":[" + step + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + JSON.readTree(created).get("id").asText()
                        + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return code;
    }

    private String criarEquipamento(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        var created = mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Tanque\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72.5,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(created).get("id").asText();
    }

    /** Um lote iniciado, com receita e ordem próprias. */
    private String criarLote(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var brassagem = criarEquipamento(session);
        var malte = criarIngrediente(session, "MALT", "m-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var lupulo = criarIngrediente(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var levedura = criarIngrediente(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var receita = """
                {"name":"CLN %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, brassagem, malte, lupulo, levedura);
        var criada = mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(receita))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var recipeId = JSON.readTree(criada).get("id").asText();
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()));

        var ordem = mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var orderId = JSON.readTree(ordem).get("id").asText();
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"));
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var lista = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(lista).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String criarIngrediente(MockHttpSession session, String type, String code, String unit,
            String attributes) throws Exception {
        var body = "{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code + "\","
                + "\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit + "\",\"attributes\":"
                + attributes + "}";
        var created = mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(created).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
