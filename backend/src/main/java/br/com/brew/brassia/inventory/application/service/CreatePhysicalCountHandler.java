package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.inventory.application.port.inbound.CreatePhysicalCountUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.PhysicalCountRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.CountLine;
import br.com.brew.brassia.inventory.domain.PhysicalCount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registra uma contagem física (STK-004): para cada lote informado, captura o
 * saldo do sistema no momento (conciliação) e grava a quantidade contada. A
 * contagem nasce OPEN — os ajustes só são gerados na aprovação.
 */
public final class CreatePhysicalCountHandler implements CreatePhysicalCountUseCase {

    private final PhysicalCountRepository counts;
    private final StockLotRepository lots;
    private final StockLedgerRepository ledger;
    private final AuditTrail audit;

    public CreatePhysicalCountHandler(PhysicalCountRepository counts, StockLotRepository lots,
            StockLedgerRepository ledger, AuditTrail audit) {
        this.counts = Objects.requireNonNull(counts);
        this.lots = Objects.requireNonNull(lots);
        this.ledger = Objects.requireNonNull(ledger);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("contagem precisa de ao menos uma linha");
        }
        List<CountLine> lines = command.lines().stream().map(input -> {
            var lot = lots.findById(command.breweryId(), input.lotId())
                    .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
            var system = ledger.balance(command.breweryId(), lot.id().value()).onHand();
            return new CountLine(lot.id().value(), lot.ingredientId(), lot.unit(), input.countedQuantity(), system);
        }).toList();

        var count = PhysicalCount.open(command.breweryId(), lines, Instant.now());
        counts.insert(count, command.actorId());

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.count.create",
                "inventory.count", count.id().value().toString(),
                Map.of("lines", Integer.toString(lines.size()))));

        return new Result(count.id().value(), count.status().name());
    }
}
