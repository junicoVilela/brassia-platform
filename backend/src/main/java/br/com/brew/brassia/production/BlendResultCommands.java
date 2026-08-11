package br.com.brew.brassia.production;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Escrita publicada para o resultado de um blend (BLD-001, DEC-BLD-003).
 *
 * <p><strong>Por que existe.</strong> O blend precisa criar um lote e mover volume entre lotes, e nenhuma
 * dessas duas coisas ele pode fazer sozinho: a tabela de produção é da produção. A porta troca o acesso
 * direto por um contrato — a produção continua dona das suas invariantes (lote de blend não tem ordem,
 * origem zerada encerra) mesmo quando quem pede é outro módulo.
 *
 * <p><strong>Por que não é um evento.</strong> O blend precisa do identificador do lote criado para
 * gravar o vínculo com a saída planejada, e precisa que a falha desfaça a execução inteira. Evento
 * assíncrono devolveria o controle antes de existir lote, e uma falha depois deixaria a operação marcada
 * como executada sem o lote que ela afirma ter produzido.
 */
public interface BlendResultCommands {

    /**
     * Abre o lote que o blend produziu.
     *
     * <p>Nasce <strong>em fermentação</strong>, e não em brassa: ele é cerveja num tanque, não um dia de
     * brassa a executar. Não é detalhe de estado — o envase recusa lote fora de fermentação, e um lote de
     * blend em brassa jamais poderia ser envasado, o que anularia a razão de criá-lo.
     *
     * <p>O tanque é declarado junto com o volume, e o enchimento é gravado como transferência: é ela que
     * responde onde o lote está desde a PRD-005, e uma segunda tabela para o mesmo dado divergiria dela.
     * Tanque já ocupado é recusado — duas cervejas no mesmo vaso não é um estado que o sistema deva saber
     * representar.
     *
     * @param recipeId receita publicada que o resultado passa a ser, declarada por quem planejou
     * @return o lote criado
     * @throws VesselOccupiedException quando o tanque já tem um lote fermentando
     */
    UUID openBlendBatch(OpenBlendBatch command);

    /**
     * Move volume envasável de um lote que já existe.
     *
     * <p>Negativo tira, positivo põe. O lote que fica sem volume nenhum é encerrado: cerveja que saiu
     * inteira para outro lote não está mais lá, e um lote vazio em aberto continuaria aparecendo como
     * disponível para envase.
     *
     * @throws InsufficientBatchVolumeException quando sai mais cerveja do que o lote tem
     */
    void adjustVolume(AdjustBatchVolume command);

    /**
     * @param liters volume do resultado; vira o volume do lote, e não há transferência que o corrija
     *               depois — a cerveja já está no tanque quando a operação executa
     */
    record OpenBlendBatch(UUID breweryId, UUID actorId, UUID recipeId, UUID equipmentId, BigDecimal liters,
            UUID blendOperationId, int outputSeq, Instant occurredAt) {}

    /**
     * @param deltaLiters negativo para o que sai, positivo para o que entra
     * @param blendOperationId com {@code batchId}, é a chave que torna a execução repetível sem ser
     *                         cumulativa: quem decide que já houve ajuste é o banco
     */
    record AdjustBatchVolume(UUID breweryId, UUID actorId, UUID batchId, BigDecimal deltaLiters,
            UUID blendOperationId, Instant occurredAt) {}

    /** O tanque de destino já tem cerveja. */
    final class VesselOccupiedException extends RuntimeException {

        private final UUID equipmentId;
        private final UUID occupiedBy;

        public VesselOccupiedException(UUID equipmentId, UUID occupiedBy) {
            super("o tanque já tem o lote " + occupiedBy);
            this.equipmentId = equipmentId;
            this.occupiedBy = occupiedBy;
        }

        public UUID equipmentId() {
            return equipmentId;
        }

        public UUID occupiedBy() {
            return occupiedBy;
        }
    }

    /** Sai mais cerveja do que o lote tem. */
    final class InsufficientBatchVolumeException extends RuntimeException {

        private final UUID batchId;
        private final BigDecimal availableLiters;
        private final BigDecimal requestedLiters;

        public InsufficientBatchVolumeException(UUID batchId, BigDecimal availableLiters,
                BigDecimal requestedLiters) {
            super("o lote tem " + availableLiters + " L e a operação pede " + requestedLiters + " L");
            this.batchId = batchId;
            this.availableLiters = availableLiters;
            this.requestedLiters = requestedLiters;
        }

        public UUID batchId() {
            return batchId;
        }

        public BigDecimal availableLiters() {
            return availableLiters;
        }

        public BigDecimal requestedLiters() {
            return requestedLiters;
        }
    }
}
