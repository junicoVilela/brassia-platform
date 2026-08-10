package br.com.brew.brassia.production;

import java.util.Optional;
import java.util.UUID;

/**
 * Qual lote ocupa um fermentador agora (PRD-005), publicado para outros módulos.
 *
 * <p><strong>Existe porque um sensor conhece o tanque, não o lote.</strong> O dispositivo é parafusado no
 * fermentador e fica lá; o lote entra, fermenta e sai. Quem ingere telemetria tem em mãos o equipamento e
 * precisa da ponta que falta para ligar a medição à cerveja — e essa ponta é do módulo de produção, porque
 * é a transferência (PRD-005) que coloca o lote no tanque.
 *
 * <p><strong>A ocupação é derivada, não armazenada.</strong> Não há coluna "lote atual" no equipamento:
 * ela seria um segundo lugar dizendo o que a transferência e o estado do lote já dizem, e os dois
 * divergiriam no primeiro lote que fosse cancelado sem alguém lembrar de limpar o campo. Aqui a resposta é
 * calculada de onde o fato mora — a transferência que apontou para este equipamento, de um lote que ainda
 * está fermentando.
 *
 * <p><strong>Vazio é resposta legítima e frequente.</strong> Tanque limpo entre lotes, lote já envasado,
 * dispositivo cadastrado sem equipamento: nenhum desses é erro, e tratar como erro faria a ingestão de
 * telemetria falhar em condição normal de operação.
 */
public interface VesselOccupancyLookup {

    /**
     * O lote que está fermentando neste equipamento, se houver.
     *
     * <p><strong>O banco não garante unicidade aqui, e vale dizer o que ele garante.</strong>
     * {@code uq_production_transfer_batch} impede duas transferências do <em>mesmo lote</em> — não impede
     * dois lotes transferidos para o <em>mesmo tanque</em>. Na operação isso é anomalia (fermentador tem um
     * lote por vez), mas nada no esquema a barra hoje.
     *
     * <p>Quando acontece, esta consulta devolve o da transferência <strong>mais recente</strong>. É a
     * escolha menos errada: creditar telemetria ao lote que entrou por último acerta no caso em que o
     * anterior ficou sem baixa por engano, que é a origem provável da anomalia. Recusar a responder faria
     * o sensor parar de alimentar a curva por um problema de cadastro em outro lote.
     */
    Optional<UUID> fermentingBatchOf(UUID breweryId, UUID equipmentId);
}
