package br.com.brew.brassia.ai.application.port.inbound;

import br.com.brew.brassia.ai.domain.CommandProposal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Propor comando e decidir sobre a proposta (AIA-003).
 *
 * <p><strong>As permissões de quem decide entram no caso de uso.</strong> Não são verificadas apenas na borda
 * HTTP: a alçada exigida no aceite é a do comando proposto, e conferi-la aqui é o que faz a regra valer para
 * qualquer chamador que apareça depois — inclusive um que não passe por requisição nenhuma.
 */
public interface ProposalCommands {

    /**
     * Pede ao copiloto uma proposta para um lote.
     *
     * <p>Pode devolver lista vazia, e isso é resposta legítima: nem todo lote pede providência, e um copiloto
     * que sempre encontra algo a fazer ensina a ignorá-lo.
     *
     * @param permissions permissões de quem pediu — o copiloto não propõe o que essa pessoa não poderia nem
     *                    ver, mas ter a permissão do comando <strong>não</strong> é exigido para propor
     */
    List<CommandProposal> propose(UUID actorId, UUID breweryId, UUID batchId, Set<String> permissions);

    /**
     * Confirma a proposta.
     *
     * @param permissions permissões atuais de quem confirma; a do comando é exigida aqui
     * @throws br.com.brew.brassia.shared.security.ForbiddenException sem a permissão do comando
     * @throws br.com.brew.brassia.ai.domain.UnknownProposalException  proposta inexistente nesta cervejaria
     * @throws br.com.brew.brassia.ai.domain.ExpiredProposalException  prazo vencido
     * @throws br.com.brew.brassia.ai.domain.ProposalNotPendingException já decidida
     */
    CommandProposal accept(UUID actorId, UUID breweryId, UUID proposalId, Set<String> permissions,
            String note);

    /** Descarta a proposta. Não exige a permissão do comando: recusar não altera nada. */
    CommandProposal reject(UUID actorId, UUID breweryId, UUID proposalId, String note);
}
