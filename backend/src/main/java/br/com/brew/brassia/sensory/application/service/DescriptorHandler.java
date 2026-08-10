package br.com.brew.brassia.sensory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sensory.application.port.inbound.DescriptorCommands;
import br.com.brew.brassia.sensory.application.port.inbound.DescriptorQueries;
import br.com.brew.brassia.sensory.application.port.outbound.DescriptorRepository;
import br.com.brew.brassia.sensory.domain.DescriptorSource;
import br.com.brew.brassia.sensory.domain.SensoryDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Biblioteca de descritores (SEN-002). */
public final class DescriptorHandler implements DescriptorCommands, DescriptorQueries {

    private final DescriptorRepository descriptors;
    private final AuditTrail audit;

    public DescriptorHandler(DescriptorRepository descriptors, AuditTrail audit) {
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public SensoryDescriptor create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        var source = new DescriptorSource(command.sourceName(), command.sourceReference(),
                command.licenseTier(), command.attribution());
        var descriptor = SensoryDescriptor.create(UUID.randomUUID(), command.breweryId(),
                command.code(), command.name(), command.category(), command.synonyms(), source,
                command.perceptionThreshold(), command.thresholdUnit(), command.hypotheses());
        descriptors.insert(descriptor);

        // A licença vai para a auditoria: se um dia alguém perguntar de onde veio um descritor que saiu
        // num relatório, a resposta está aqui — e não na memória de quem cadastrou.
        audit.record(AuditEvent.success(command.breweryId(), command.actor(),
                "sensory.descriptor.create", "sensory_descriptor", descriptor.id().toString(),
                Map.of("code", descriptor.code(),
                        "licenseTier", source.tier().name(),
                        "hasThreshold", String.valueOf(descriptor.perceptionThreshold().isPresent()))));
        return descriptor;
    }

    @Override
    public void linkToStyle(UUID breweryId, String styleCode, UUID descriptorId, boolean expected,
            UUID actor) {
        descriptors.linkToStyle(breweryId, styleCode, descriptorId, expected);
        audit.record(AuditEvent.success(breweryId, actor, "sensory.descriptor.link",
                "sensory_descriptor", descriptorId.toString(),
                Map.of("style", styleCode, "expected", String.valueOf(expected))));
    }

    @Override
    public List<SensoryDescriptor> list(UUID breweryId) {
        return descriptors.list(breweryId);
    }

    @Override
    public List<SensoryDescriptor> search(UUID breweryId, String term) {
        return term == null || term.isBlank() ? List.of() : descriptors.searchByTerm(breweryId, term);
    }

    @Override
    public List<DescriptorRepository.StyleLink> forStyle(UUID breweryId, String styleCode) {
        return descriptors.byStyle(breweryId, styleCode);
    }
}
