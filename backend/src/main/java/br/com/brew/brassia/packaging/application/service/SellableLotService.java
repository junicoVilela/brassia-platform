package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.packaging.SellableLotLookup;
import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotRepository;
import br.com.brew.brassia.packaging.application.port.outbound.FreshnessRepository;
import br.com.brew.brassia.packaging.application.port.outbound.LotReleaseRepository;
import br.com.brew.brassia.packaging.domain.FinishedLot;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.QuarantineCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compõe as três condições que tornam um lote vendável (SAL-001-B).
 *
 * <p>Mora aqui, e não em {@code sales}, porque as três já estão ao alcance deste módulo: o lote acabado é
 * dele, a validade vem do frescor (FSL-001) e a quarentena vem de {@code traceability}, do qual
 * {@code packaging} já depende. Se quem pergunta compusesse, precisaria de três dependências para
 * responder uma pergunta só — e cada critério novo viraria uma dependência nova lá.
 */
public class SellableLotService implements SellableLotLookup {

    private final FinishedLotRepository lots;
    private final LotReleaseRepository releases;
    private final FreshnessRepository freshness;
    private final QuarantineCheck quarantine;
    private final BatchLookup batches;

    public SellableLotService(FinishedLotRepository lots, LotReleaseRepository releases,
            FreshnessRepository freshness, QuarantineCheck quarantine, BatchLookup batches) {
        this.lots = Objects.requireNonNull(lots);
        this.releases = Objects.requireNonNull(releases);
        this.freshness = Objects.requireNonNull(freshness);
        this.quarantine = Objects.requireNonNull(quarantine);
        this.batches = Objects.requireNonNull(batches);
    }

    @Override
    public List<SellableLot> sellableLots(UUID breweryId, UUID recipeId, UUID containerId, LocalDate on) {
        Objects.requireNonNull(on, "data");
        var candidatos = lots.findAll(breweryId).stream()
                .filter(l -> l.containerId().equals(containerId))
                .filter(l -> sameRecipe(breweryId, l, recipeId))
                .toList();
        // Um SELECT para todas as liberações, e não um por lote: com trinta lotes na tela a versão
        // ingênua faz trinta idas ao banco, que é o N+1 que a REL-002 já custou uma vez.
        var liberacoes = releases.findAll(breweryId,
                candidatos.stream().map(FinishedLot::id).collect(Collectors.toSet()));

        return candidatos.stream()
                .map(l -> avaliar(breweryId, l, liberacoes.get(l.id()), on))
                .filter(Avaliacao::vendavel)
                .map(a -> new SellableLot(a.lot().id(), a.lot().code(), a.lot().batchCode(),
                        a.lot().units(), a.lot().containerVolumeMl(), a.lot().packagedOn(),
                        a.bestBefore()))
                .toList();
    }

    @Override
    public Optional<LotSaleStatus> statusOf(UUID breweryId, UUID finishedLotId, LocalDate on) {
        Objects.requireNonNull(on, "data");
        return lots.findById(breweryId, finishedLotId)
                .map(l -> avaliar(breweryId, l, releases.find(breweryId, l.id()).orElse(null), on))
                .map(a -> new LotSaleStatus(a.lot().id(), a.lot().code(), a.vendavel(),
                        Optional.ofNullable(a.blocker()), a.bestBefore()));
    }

    /**
     * A ordem dos impedimentos não é acidental.
     *
     * <p>Quarentena vem primeiro porque é a mais grave e a que muda o que se faz: um lote em quarentena
     * não é caso de correr atrás de assinatura. Depois a liberação, que é ação de alguém. Por último a
     * validade, que é fato consumado e não tem o que fazer.
     */
    private Avaliacao avaliar(UUID breweryId, FinishedLot lot, br.com.brew.brassia.packaging.domain.LotRelease release,
            LocalDate on) {
        var bestBefore = freshness.findByPlan(breweryId, lot.planId())
                .map(f -> f.effectiveBestBefore())
                .orElse(null);

        var bloqueio = quarantine.blocking(breweryId, LineageSource.NodeType.FINISHED_LOT, lot.id());
        if (bloqueio.isPresent()) {
            return new Avaliacao(lot, bestBefore,
                    new Blocker(bloqueio.get().code(), bloqueio.get().message()));
        }
        if (release == null) {
            return new Avaliacao(lot, bestBefore, new Blocker("not_released",
                    "Este lote ainda não foi liberado pela qualidade."));
        }
        if (bestBefore == null) {
            // Validade desconhecida não é validade em dia. Sem evidência de oxigênio nem decisão humana
            // registrada, vender seria prometer um prazo que ninguém apurou.
            return new Avaliacao(lot, null, new Blocker("shelf_life_unknown",
                    "Este lote não tem validade apurada: registre a evidência de oxigênio ou a validade."));
        }
        if (on.isAfter(bestBefore)) {
            return new Avaliacao(lot, bestBefore, new Blocker("expired",
                    "A validade deste lote venceu em " + bestBefore + "."));
        }
        return new Avaliacao(lot, bestBefore, null);
    }

    private boolean sameRecipe(UUID breweryId, FinishedLot lot, UUID recipeId) {
        return batches.find(breweryId, lot.batchId())
                .map(b -> recipeId.equals(b.recipeId()))
                .orElse(false);
    }

    private record Avaliacao(FinishedLot lot, LocalDate bestBefore, Blocker blocker) {

        boolean vendavel() {
            return blocker == null;
        }
    }
}
