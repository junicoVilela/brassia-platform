package br.com.brew.brassia.packaging.application.port.inbound;

import br.com.brew.brassia.packaging.domain.FreshnessRecord;
import br.com.brew.brassia.packaging.domain.ShelfLifePolicy;
import br.com.brew.brassia.packaging.domain.ShelfLifeRecommendation;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Oxigênio e vida útil do envase (FSL-001). */
public final class FreshnessCommands {

    private FreshnessCommands() {
    }

    /**
     * Registra DO/TPO, purga e vedação, e deriva a validade recomendada da política da casa.
     * Sem política configurada a medição vale do mesmo jeito — a validade é que fica a decidir.
     */
    public interface Record {
        Result handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, BigDecimal dissolvedOxygenPpb,
                BigDecimal totalPackageOxygenPpb, String purgeMethod, boolean purgeVerified,
                String sealCheckMethod, boolean sealCheckPassed) {}

        /** {@code recommendation} nulo significa cervejaria sem política de vida útil. */
        record Result(FreshnessRecord record, ShelfLifeRecommendation recommendation) {}
    }

    /** Sobrepõe a validade recomendada; o motivo é obrigatório e o override é auditado. */
    public interface OverrideShelfLife {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, int shelfLifeDays, String reason) {}
    }

    public interface Get {
        Optional<FreshnessRecord> handle(UUID breweryId, UUID planId);
    }

    /** Política de vida útil da cervejaria: as faixas de TPO e os dias que elas sustentam. */
    public interface Policy {
        Optional<ShelfLifePolicy> get(UUID breweryId);

        void save(UUID actorId, UUID breweryId, ShelfLifePolicy policy);
    }
}
