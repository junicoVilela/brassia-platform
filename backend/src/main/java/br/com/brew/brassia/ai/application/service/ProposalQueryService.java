package br.com.brew.brassia.ai.application.service;

import br.com.brew.brassia.ai.application.port.inbound.ProposalQueries;
import br.com.brew.brassia.ai.application.port.outbound.CommandProposalRepository;
import br.com.brew.brassia.ai.domain.CommandProposal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Leitura das propostas (AIA-003). */
public final class ProposalQueryService implements ProposalQueries {

    private final CommandProposalRepository proposals;

    public ProposalQueryService(CommandProposalRepository proposals) {
        this.proposals = Objects.requireNonNull(proposals);
    }

    @Override
    public List<CommandProposal> list(UUID breweryId) {
        return proposals.findAll(Objects.requireNonNull(breweryId, "breweryId"));
    }
}
