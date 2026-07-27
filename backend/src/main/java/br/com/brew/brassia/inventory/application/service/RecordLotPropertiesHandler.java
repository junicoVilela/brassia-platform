package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.inventory.application.port.inbound.RecordLotPropertiesUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotPropertyRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.LotPropertyConfidence;
import br.com.brew.brassia.inventory.domain.LotPropertySource;
import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Vincula valores medidos a um lote (STK-005). Valida o lote (tenant), impede
 * regravação da mesma propriedade (write-once → 409) e audita. Não escreve no
 * catálogo — o valor é privado do lote/tenant.
 */
public final class RecordLotPropertiesHandler implements RecordLotPropertiesUseCase {

    private final StockLotRepository lots;
    private final StockLotPropertyRepository properties;
    private final AuditTrail audit;

    public RecordLotPropertiesHandler(StockLotRepository lots, StockLotPropertyRepository properties,
            AuditTrail audit) {
        this.lots = Objects.requireNonNull(lots);
        this.properties = Objects.requireNonNull(properties);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (command.properties() == null || command.properties().isEmpty()) {
            throw new IllegalArgumentException("informe ao menos uma propriedade");
        }
        var lot = lots.findById(command.breweryId(), command.lotId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));

        var now = Instant.now();
        var seen = new HashSet<String>();
        var ids = new ArrayList<java.util.UUID>();
        for (var input : command.properties()) {
            var domain = StockLotProperty.record(lot.id().value(), lot.breweryId(), input.property(), input.value(),
                    input.unit(), LotPropertySource.of(input.source()),
                    LotPropertyConfidence.of(input.confidence()), now, command.actorId());
            if (!seen.add(domain.property())) {
                throw new IllegalArgumentException("propriedade duplicada na requisição: " + domain.property());
            }
            if (properties.existsByProperty(command.breweryId(), lot.id().value(), domain.property())) {
                throw new IllegalStateException("propriedade já registrada para o lote: " + domain.property());
            }
            properties.insert(domain);
            ids.add(domain.id());
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.lot.property.record",
                "inventory.lot", lot.id().value().toString(),
                Map.of("count", String.valueOf(ids.size()))));

        return new Result(List.copyOf(ids));
    }
}
