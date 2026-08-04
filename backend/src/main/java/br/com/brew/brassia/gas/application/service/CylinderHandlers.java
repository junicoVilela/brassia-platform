package br.com.brew.brassia.gas.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.gas.application.port.inbound.CylinderCommands;
import br.com.brew.brassia.gas.application.port.outbound.GasCylinderRepository;
import br.com.brew.brassia.gas.application.port.outbound.GasPolicyRepository;
import br.com.brew.brassia.gas.domain.GasCylinder;
import br.com.brew.brassia.gas.domain.GasType;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ciclo de vida do cilindro (GAS-001). Bloqueio, desbloqueio, requalificação e recarga mudam a
 * aptidão do cilindro para servir gás, então todos são auditados com o estado resultante.
 */
public final class CylinderHandlers {

    private CylinderHandlers() {
    }

    public static final class Register implements CylinderCommands.Register {

        private final GasCylinderRepository cylinders;
        private final AuditTrail audit;

        public Register(GasCylinderRepository cylinders, AuditTrail audit) {
            this.cylinders = Objects.requireNonNull(cylinders);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            if (cylinders.existsByCode(command.breweryId(), command.code())) {
                throw new IllegalStateException("já existe cilindro com o código " + command.code());
            }
            var cylinder = GasCylinder.register(command.breweryId(), command.code(),
                    GasType.of(command.gasType()), command.capacityKg(), command.tareKg(), command.contentKg(),
                    command.requalificationDueOn(), command.location());
            cylinders.insert(cylinder);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.cylinder.register",
                    "gas.cylinder", cylinder.id().toString(),
                    Map.of("code", cylinder.code(), "gasType", cylinder.gasType().name(),
                            "requalificationDueOn", cylinder.requalificationDueOn().toString())));
            return cylinder.id();
        }
    }

    public static final class SetBlock implements CylinderCommands.SetBlock {

        private final GasCylinderRepository cylinders;
        private final AuditTrail audit;

        public SetBlock(GasCylinderRepository cylinders, AuditTrail audit) {
            this.cylinders = Objects.requireNonNull(cylinders);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            mutate(cylinders, audit, command.actorId(), command.breweryId(), command.cylinderId(),
                    command.blocked() ? "gas.cylinder.block" : "gas.cylinder.unblock",
                    cylinder -> {
                        if (command.blocked()) {
                            cylinder.block(command.reason());
                        } else {
                            cylinder.unblock();
                        }
                    },
                    cylinder -> Map.of("code", cylinder.code(), "status", cylinder.status().name(),
                            "reason", cylinder.blockReason() == null ? "" : cylinder.blockReason()));
        }
    }

    public static final class Requalify implements CylinderCommands.Requalify {

        private final GasCylinderRepository cylinders;
        private final GasPolicyRepository policies;
        private final AuditTrail audit;

        public Requalify(GasCylinderRepository cylinders, GasPolicyRepository policies, AuditTrail audit) {
            this.cylinders = Objects.requireNonNull(cylinders);
            this.policies = Objects.requireNonNull(policies);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var today = LocalDate.now(ZoneOffset.UTC);
            // A data informada sempre vence: a política (PRM-001) só preenche o que ficou em
            // branco. Sem política e sem data, o comando é recusado como antes.
            var dueOn = command.dueOn() != null ? command.dueOn()
                    : policies.find(command.breweryId()).nextDueOn(today)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "informe o vencimento ou configure a periodicidade de requalificação"));
            mutate(cylinders, audit, command.actorId(), command.breweryId(), command.cylinderId(),
                    "gas.cylinder.requalify",
                    cylinder -> cylinder.requalify(dueOn, today),
                    cylinder -> Map.of("code", cylinder.code(),
                            "requalificationDueOn", cylinder.requalificationDueOn().toString()));
        }
    }

    public static final class Refill implements CylinderCommands.Refill {

        private final GasCylinderRepository cylinders;
        private final AuditTrail audit;

        public Refill(GasCylinderRepository cylinders, AuditTrail audit) {
            this.cylinders = Objects.requireNonNull(cylinders);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            mutate(cylinders, audit, command.actorId(), command.breweryId(), command.cylinderId(),
                    "gas.cylinder.refill",
                    cylinder -> cylinder.refill(command.contentKg()),
                    cylinder -> Map.of("code", cylinder.code(),
                            "contentKg", cylinder.contentKg().toPlainString()));
        }
    }

    /** Carrega travado, aplica a mudança sob lock otimista e audita o estado resultante. */
    private static void mutate(GasCylinderRepository cylinders, AuditTrail audit, UUID actorId, UUID breweryId,
            UUID cylinderId, String action, Consumer<GasCylinder> change,
            Function<GasCylinder, Map<String, String>> metadata) {
        var cylinder = cylinders.findForUpdate(breweryId, cylinderId)
                .orElseThrow(() -> new IllegalArgumentException("cilindro inexistente"));
        var version = cylinder.version();
        change.accept(cylinder);
        if (!cylinders.update(cylinder, version)) {
            throw new IllegalStateException("cilindro alterado por outra operação; tente novamente");
        }
        audit.record(AuditEvent.success(breweryId, actorId, action, "gas.cylinder", cylinder.id().toString(),
                metadata.apply(cylinder)));
    }
}
