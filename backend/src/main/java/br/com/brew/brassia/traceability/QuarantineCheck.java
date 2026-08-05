package br.com.brew.brassia.traceability;

import java.util.Optional;
import java.util.UUID;

/**
 * Pergunta se um nó está contido por alguma quarentena aberta (FDS-002), para que os módulos que
 * movimentam produto recusem a operação sem conhecer a rastreabilidade.
 *
 * <p>O bloqueio alcança o nó por herança: quarentenar um lote bloqueia o plano de envase que nasce
 * dele, sem que ninguém precise bloquear o plano. Como a resposta é derivada do grafo no momento da
 * pergunta, um envase criado <em>depois</em> da abertura já nasce bloqueado.
 */
public interface QuarantineCheck {

    /** Vazio quando o nó está livre. */
    Optional<Block> blocking(UUID breweryId, LineageSource.NodeType type, UUID nodeId);

    /**
     * @param origin    rótulo do nó que foi quarentenado — o operador precisa saber de onde vem o
     *                  bloqueio, não só que existe
     * @param suspected verdadeiro quando o caminho até aqui passa por intenção e não por fato
     *                  registrado; bloqueia igual, mas não afirma o mesmo
     */
    record Block(UUID quarantineId, String origin, String reason, boolean suspected) {

        /** Código estável do impedimento, para o Problem Details de quem recusa. */
        public String code() {
            return suspected ? "quarantine_suspected" : "quarantined";
        }

        public String message() {
            var prefix = suspected
                    ? "Este item pode ter vindo de "
                    : "Este item vem de ";
            var tail = suspected
                    ? " — o elo é uma reserva, não um consumo comprovado, e a quarentena alcança por suspeita."
                    : ".";
            return prefix + origin + ", em quarentena: " + reason + tail;
        }
    }
}
