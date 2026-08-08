package br.com.brew.brassia.ai.application.port.inbound;

import java.util.UUID;

/** Comandos do gateway (AIA-001). */
public interface GatewayCommands {

    /**
     * Faz a menor chamada possível que ainda prova o caminho inteiro.
     *
     * <p><strong>Por que existe um comando só para isso.</strong> As quatro histórias seguintes desta
     * sprint dependem do gateway, e "a IA não respondeu" tem quatro causas diferentes: provedor
     * desligado, credencial errada, orçamento esgotado e resposta fora do contrato. Um pedido mínimo
     * que atravessa provedor, orçamento, contrato e registro separa as quatro em segundos, em vez de
     * transformar cada uma num mistério descoberto no meio de outra história.
     *
     * <p>É comando, não consulta: gasta dinheiro de verdade e por isso tem autor, permissão própria e
     * linha no ledger como qualquer outra chamada.
     */
    ProbeAnswer probe(UUID actorId, UUID breweryId);

    /**
     * O contrato que a resposta da verificação precisa satisfazer.
     *
     * <p>Deliberadamente estreito: o valor do teste está em provar que a resposta é recusada quando
     * sai da forma, e um contrato frouxo não recusaria nada. As invariantes ficam no construtor porque
     * é onde a desserialização passa — um campo ausente, um tipo errado ou um texto vazio falham aqui,
     * antes de existir objeto.
     *
     * @param ready confirmação de que o modelo conseguiu responder no formato pedido
     * @param note  uma frase curta do próprio modelo, para mostrar que houve geração de fato
     */
    record ProbeAnswer(boolean ready, String note) {

        /** Teto da frase: o suficiente para provar geração, curto o bastante para não virar resposta. */
        private static final int MAX_NOTE = 200;

        public ProbeAnswer {
            if (note == null || note.isBlank()) {
                throw new IllegalArgumentException("note é obrigatório");
            }
            note = note.trim();
            if (note.length() > MAX_NOTE) {
                throw new IllegalArgumentException("note excede " + MAX_NOTE + " caracteres");
            }
        }
    }
}
