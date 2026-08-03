package br.com.brew.brassia.metrology.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.metrology.application.port.inbound.StandardCommands;
import br.com.brew.brassia.metrology.application.port.outbound.CalibrationStandardRepository;
import br.com.brew.brassia.metrology.domain.CalibrationStandard;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Cadastro e renovação do padrão de referência (MTR-001). */
public final class StandardHandlers {

    private StandardHandlers() {
    }

    public static final class Register implements StandardCommands.Register {

        private final CalibrationStandardRepository standards;
        private final AuditTrail audit;

        public Register(CalibrationStandardRepository standards, AuditTrail audit) {
            this.standards = Objects.requireNonNull(standards);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            if (standards.existsByCode(command.breweryId(), command.code())) {
                throw new IllegalStateException("já existe padrão com o código " + command.code());
            }
            var standard = CalibrationStandard.register(command.breweryId(), command.code(),
                    command.description(), command.certificateNumber(), command.issuer(),
                    command.traceability(), command.validUntil());
            standards.insert(standard);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "metrology.standard.register",
                    "metrology.standard", standard.id().toString(),
                    Map.of("code", standard.code(), "traceability", standard.traceability(),
                            "validUntil", standard.validUntil().toString())));
            return standard.id();
        }
    }

    public static final class Renew implements StandardCommands.Renew {

        private final CalibrationStandardRepository standards;
        private final AuditTrail audit;

        public Renew(CalibrationStandardRepository standards, AuditTrail audit) {
            this.standards = Objects.requireNonNull(standards);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var standard = standards.lockById(command.breweryId(), command.standardId())
                    .orElseThrow(() -> new IllegalArgumentException("padrão inexistente"));
            standard.renew(command.certificateNumber(), command.issuer(), command.validUntil(),
                    command.issuedOn());
            standards.update(standard);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "metrology.standard.renew",
                    "metrology.standard", standard.id().toString(),
                    Map.of("code", standard.code(), "validUntil", standard.validUntil().toString())));
        }
    }
}
