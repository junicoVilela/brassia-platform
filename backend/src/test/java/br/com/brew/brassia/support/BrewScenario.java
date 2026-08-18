package br.com.brew.brassia.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A máquina que constrói um lote de cerveja de verdade, pela API (DEB-SAL-003).
 *
 * <p><strong>Por que existe.</strong> Chegar a um lote de produto acabado exige receita publicada, ordem
 * liberada, brassa iniciada, transferência ao fermentador, embalagem no estoque, linha limpa, plano com
 * checklist e reserva — nove passos, nenhum deles dispensável, porque o sistema recusa cada atalho de
 * propósito. Isso morava dentro do {@code PackagingRunIT} e o inchou até 1.200 linhas; qualquer outro
 * teste que precisasse de um lote acabado tinha duas saídas ruins: duplicar tudo, ou dublar o lote.
 *
 * <p><strong>É construída por API, e não por SQL.</strong> Inserir as linhas direto no banco seria mais
 * curto e produziria um lote que nenhum caminho do sistema consegue produzir — a fixture pararia de
 * quebrar quando uma regra mudasse, que é exatamente quando ela precisa quebrar.
 *
 * <p>Cada método devolve o identificador do que criou, e não a cena inteira: quem precisa de um lote
 * fermentando não paga pelo envase, e quem precisa do envase não repete a receita.
 */
public final class BrewScenario {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLANS = "/api/v1/packaging/plans";
    /**
     * Relativas a agora, e não datas fixas.
     *
     * <p>Uma âncora fixa faz a limpeza da linha — que é liberada no instante do teste — cair depois do
     * início planejado, e o envase é recusado por linha suja. O sintoma aparece meses depois de escrito,
     * quando o calendário passa da data escolhida.
     */
    private static final String PLANNED_START = java.time.Instant.now()
            .plus(java.time.Duration.ofHours(1)).toString();
    private static final String PLANNED_END = java.time.Instant.now()
            .plus(java.time.Duration.ofHours(7)).toString();

    private final MockMvc mockMvc;

    public BrewScenario(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** A sessão do administrador local — o mesmo login que todos os testes usam. */
    public MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    public String equipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + suffix();
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Linha\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    public String ingredient(MockHttpSession session, String type, String unit, String attributes)
            throws Exception {
        var code = type.toLowerCase(Locale.ROOT).charAt(0) + "-" + suffix();
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** Lata de 355 ml: a embalagem padrão dos cenários. */
    public String canContainer(MockHttpSession session) throws Exception {
        return ingredient(session, "PACKAGING", "UNIT", "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
    }

    public String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = suffix();
        var equipmentId = equipment(session);
        var maltId = ingredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = ingredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = ingredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Run %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        return orderId;
    }

    public String startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    /** Lote transferido ao fermentador: é o estado mínimo para se planejar um envase. */
    public String fermentingBatch(MockHttpSession session) throws Exception {
        var batchId = startedBatch(session);
        var fermenter = equipment(session);
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
        return batchId;
    }

    /** Ciclo de limpeza completo e liberado — sem ele a linha não recebe plano. */
    public void releaseCleaning(MockHttpSession session, String equipmentId) throws Exception {
        var code = "CIP-" + suffix();
        var step = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var procedureId = idOf(mockMvc.perform(post("/api/v1/sanitation/procedures").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP linha\",\"steps\":[" + step
                                + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + procedureId + "/publish").session(session)
                        .with(csrf()))
                .andExpect(status().isOk());
        var cycleId = idOf(mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId
                                + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/steps").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,"
                                + "\"measuredTimeMinutes\":20}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/complete").session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/verification").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,"
                                + "\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/release").session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    /** Embalagem recebida no estoque, com fornecedor e inspeção aprovada. */
    public String receiveContainers(MockHttpSession session, String containerId, int quantity)
            throws Exception {
        var sfx = suffix();
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + containerId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"UNIT\",\"unitCost\":0.9,"
                                + "\"expiryDate\":\"2028-01-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    public String plan(MockHttpSession session, String batchId, String containerId, String lineId,
            int units) throws Exception {
        var content = """
                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":%d,
                 "lineEquipmentId":"%s","plannedStart":"%s","plannedEnd":"%s"}
                """.formatted(suffix(), batchId, containerId, units, lineId, PLANNED_START, PLANNED_END);
        var body = mockMvc.perform(post(PLANS).session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    /** Plano criado, checklist confirmado, linha limpa e embalagem reservada. */
    public String reservedPlan(MockHttpSession session, String batchId, String containerId, int units)
            throws Exception {
        var lineId = equipment(session);
        releaseCleaning(session, lineId);
        var planId = plan(session, batchId, containerId, lineId, units);
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        return planId;
    }

    /**
     * Um lote de produto acabado, do zero: nove passos, e o identificador no fim.
     *
     * <p>É o que outros módulos precisam quando dizem "dado um lote acabado…" — e o que antes só existia
     * dentro do {@code PackagingRunIT}.
     */
    public FinishedLot finishedLot(MockHttpSession session) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = canContainer(session);
        receiveContainers(session, containerId, 1000);
        var planId = reservedPlan(session, batchId, containerId, 800);
        var body = mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"inputVolumeLiters\":284,\"producedUnits\":780,\"rejectedUnits\":12}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var code = JSON.readTree(body).get("finishedLotCode").asText();
        var lot = finishedLotOf(session, batchId, code);
        return new FinishedLot(lot.get("id").asText(), code, batchId, planId, containerId);
    }

    /**
     * Evidência de oxigênio e a validade que sai dela (FSL-001).
     *
     * <p>Dois passos porque o sistema recusa inventar prazo: ou há política de vida útil que sustente a
     * recomendação, ou alguém assume por escrito. Sem isso o lote fica com validade desconhecida — e
     * validade desconhecida não é validade em dia.
     */
    public void recordFreshness(MockHttpSession session, String planId) throws Exception {
        mockMvc.perform(put(PLANS + "/" + planId + "/freshness").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"dissolvedOxygenPpb":30,"totalPackageOxygenPpb":50,
                                 "purgeMethod":"CO2 counter-pressure","purgeVerified":true,
                                 "sealCheckMethod":"torque","sealCheckPassed":true}
                                """))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(PLANS + "/" + planId + "/freshness/override").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"shelfLifeDays\":180,\"reason\":\"validade definida no teste\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    /** Liberação pela qualidade: o ato assinado que a SAL-001-B exige (DEC-SAL-002). */
    public void releaseLot(MockHttpSession session, String lotId) throws Exception {
        mockMvc.perform(post("/api/v1/packaging/finished-lots/" + lotId + "/release").session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    /** Um lote pronto para vender: liberado pela qualidade e com validade apurada. */
    public FinishedLot sellableLot(MockHttpSession session) throws Exception {
        var lot = finishedLot(session);
        recordFreshness(session, lot.planId());
        releaseLot(session, lot.id());
        return lot;
    }

    public JsonNode finishedLotOf(MockHttpSession session, String batchId, String code) throws Exception {
        var body = mockMvc.perform(get("/api/v1/packaging/finished-lots").param("batchId", batchId)
                        .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode lot : JSON.readTree(body)) {
            if (lot.get("code").asText().equals(code)) {
                return lot;
            }
        }
        throw new AssertionError("lote de produto acabado ausente: " + code);
    }

    public String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    public static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * A cena comercial completa: lote vendável, produto, canal e preço.
     *
     * <p>Ela mora aqui pelo mesmo motivo do lote acabado: montar um produto vendável exige envase,
     * liberação, validade, catálogo e lista de preço — e quatro classes de teste precisavam disso.
     */
    public SalesScene sellableProduct(MockHttpSession session) throws Exception {
        var lot = sellableLot(session);
        var recipeId = recipeOfBatch(session, lot.batchId());
        var productId = product(session, recipeId, lot.containerId());
        var channelId = channel(session);
        mockMvc.perform(post("/api/v1/sales/products/" + productId + "/prices").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"channelId\":\"" + channelId + "\",\"amount\":12.00,"
                                + "\"currency\":\"BRL\",\"taxIncluded\":false,"
                                + "\"validFrom\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isNoContent());
        return new SalesScene(productId, channelId, customer(session), lot.code(), lot.id(),
                lot.batchId(), lot.planId());
    }

    public String recipeOfBatch(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("recipeId").asText();
    }

    public String product(MockHttpSession session, String recipeId, String containerId)
            throws Exception {
        var body = mockMvc.perform(post("/api/v1/sales/products").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sku\":\"SKU-" + suffix() + "\",\"name\":\"Produto de teste\","
                                + "\"recipeId\":\"" + recipeId + "\","
                                + "\"containerId\":\"" + containerId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    public String channel(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/sales/channels").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"CH-" + suffix() + "\",\"name\":\"Canal de teste\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    public String customer(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/crm/customers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"legalName\":\"Cliente de teste\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    /** O corpo de um pedido daquela cena — a forma que quatro classes repetiam. */
    public String orderBody(SalesScene scene, int quantity, LocalDate promisedFor) {
        return orderBody(scene, scene.channelId(), quantity, promisedFor);
    }

    /**
     * O mesmo pedido, num canal <strong>diferente</strong> do da cena.
     *
     * <p>Existe para o teste do canal sem preço: "ainda não precificado" e "de graça" são coisas opostas,
     * e sem poder trocar o canal não há como exercitar a recusa.
     */
    public String orderBody(SalesScene scene, String channelId, int quantity, LocalDate promisedFor) {
        return "{\"code\":\"PED-" + suffix() + "\",\"customerId\":\"" + scene.customerId() + "\","
                + "\"channelId\":\"" + channelId + "\","
                + (promisedFor == null ? "" : "\"promisedFor\":\"" + promisedFor + "\",")
                + "\"items\":[{\"productId\":\"" + scene.productId() + "\",\"quantity\":"
                + quantity + "}]}";
    }

    /** @param lotCode o código do lote vendável, que aparece na reserva e no webhook */
    public record SalesScene(String productId, String channelId, String customerId, String lotCode,
            String finishedLotId, String batchId, String planId) {}

    /**
     * @param planId serve para registrar frescor e liberar; guardá-lo evita quem usa a fixture ter de
     *               reconsultar o plano pelo lote
     */
    public record FinishedLot(String id, String code, String batchId, String planId,
            String containerId) {}
}
