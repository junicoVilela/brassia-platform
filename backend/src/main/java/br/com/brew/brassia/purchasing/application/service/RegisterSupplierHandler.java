package br.com.brew.brassia.purchasing.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.purchasing.application.port.inbound.RegisterSupplierUseCase;
import br.com.brew.brassia.purchasing.application.port.outbound.SupplierRepository;
import br.com.brew.brassia.purchasing.domain.Supplier;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class RegisterSupplierHandler implements RegisterSupplierUseCase {

    private final SupplierRepository repository;
    private final AuditTrail audit;

    public RegisterSupplierHandler(SupplierRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var supplier = Supplier.register(command.breweryId(), command.name(), command.code());
        if (repository.existsByCode(command.breweryId(), supplier.code().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("já existe fornecedor com esse código nesta cervejaria");
        }
        repository.insert(supplier);
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "purchasing.supplier.create",
                "purchasing.supplier", supplier.id().value().toString(),
                Map.of("code", supplier.code(), "name", supplier.name())));
        return new Result(supplier.id().value(), supplier.name(), supplier.code());
    }
}
