package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.QuarantineCheck;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineQueries;
import br.com.brew.brassia.traceability.application.port.outbound.QuarantineRepository;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.Quarantine;
import br.com.brew.brassia.traceability.domain.Spread;
import br.com.brew.brassia.traceability.domain.UnknownQuarantineException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Leituras e propagação da quarentena (FDS-002).
 *
 * <p><strong>O bloqueio é derivado, e a travessia é de trás para frente.</strong> Perguntar "que
 * quarentena alcança este plano de envase" é caminhar do plano para os ancestrais até encontrar uma
 * origem quarentenada — o mesmo grafo da genealogia, lido do outro lado. Guardar a lista de
 * descendentes na abertura seria mais rápido e estaria errada no dia seguinte: um envase criado
 * depois não estaria nela, e passaria.
 */
public final class QuarantineQueryHandler implements QuarantineQueries, QuarantineCheck {

    /**
     * Profundidade do bloqueio. Igual ao padrão da genealogia: se a investigação enxerga seis
     * saltos, a contenção não pode enxergar menos — o operador veria na tela um descendente que o
     * envase deixou passar.
     */
    private static final int BLOCK_DEPTH = 6;

    private final QuarantineRepository quarantines;
    private final List<LineageSource> sources;

    public QuarantineQueryHandler(QuarantineRepository quarantines, List<LineageSource> sources) {
        this.quarantines = Objects.requireNonNull(quarantines);
        this.sources = List.copyOf(Objects.requireNonNull(sources));
    }

    @Override
    public List<Quarantine> list(UUID breweryId, boolean onlyOpen) {
        return onlyOpen ? quarantines.findOpen(breweryId) : quarantines.findAll(breweryId);
    }

    @Override
    public Detail detail(UUID breweryId, UUID quarantineId, int depth) {
        var quarantine = quarantines.findById(breweryId, quarantineId)
                .orElseThrow(() -> new UnknownQuarantineException(quarantineId));
        var spread = Spread.from(quarantine.origin(), Direction.FORWARD, depth,
                new FederatedLineageGraph(sources, breweryId));
        return new Detail(quarantine, spread);
    }

    @Override
    public Optional<Block> blocking(UUID breweryId, NodeType type, UUID nodeId) {
        var open = quarantines.findOpen(breweryId);
        if (open.isEmpty()) {
            // Cervejaria sem quarentena aberta não paga o custo da travessia.
            return Optional.empty();
        }

        var spread = Spread.from(Node.of(type, nodeId), Direction.BACKWARD, BLOCK_DEPTH,
                new FederatedLineageGraph(sources, breweryId));
        return open.stream()
                .flatMap(quarantine -> spread.reaching(quarantine.origin()).stream()
                        .map(affected -> new Block(quarantine.id(), label(quarantine),
                                quarantine.reason(), affected.suspected())))
                // Bloqueio confirmado vence o suspeito: se há um caminho de fato, é o que se diz.
                .min(Comparator.comparing(Block::suspected));
    }

    private static String label(Quarantine quarantine) {
        return quarantine.originLabel() == null
                ? quarantine.nodeType().name() + " " + quarantine.nodeId()
                : quarantine.originLabel();
    }
}
