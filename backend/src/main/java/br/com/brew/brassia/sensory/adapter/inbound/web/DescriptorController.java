package br.com.brew.brassia.sensory.adapter.inbound.web;

import br.com.brew.brassia.sensory.application.port.inbound.DescriptorCommands;
import br.com.brew.brassia.sensory.application.port.inbound.DescriptorQueries;
import br.com.brew.brassia.sensory.domain.DescriptorCategory;
import br.com.brew.brassia.sensory.domain.Hypothesis;
import br.com.brew.brassia.sensory.domain.LicenseTier;
import br.com.brew.brassia.sensory.domain.SensoryDescriptor;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biblioteca de descritores e off-flavors (SEN-002).
 *
 * <p><strong>A resposta carrega a licença junto com o conteúdo.</strong> Quem consome precisa saber, sem
 * perguntar a ninguém, se pode reproduzir o limiar num relatório que sai da cervejaria e se precisa
 * imprimir a atribuição. Devolver o descritor sem isso transferiria a responsabilidade da licença para a
 * memória de quem monta o relatório.
 */
@RestController
@RequestMapping("/api/v1/sensory/descriptors")
final class DescriptorController {

    private final DescriptorCommands commands;
    private final DescriptorQueries queries;

    DescriptorController(DescriptorCommands commands, DescriptorQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    List<DescriptorResponse> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) String term) {
        principal.requirePermission("sensory.descriptor.read");
        var brewery = principal.requireBrewery();
        var found = term == null || term.isBlank()
                ? queries.list(brewery)
                : queries.search(brewery, term);
        return found.stream().map(DescriptorResponse::from).toList();
    }

    /** O vocabulário do estilo, para o scoresheet oferecer o que se está provando. */
    @GetMapping("/by-style/{styleCode}")
    List<StyleDescriptorResponse> byStyle(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable String styleCode) {
        principal.requirePermission("sensory.descriptor.read");
        return queries.forStyle(principal.requireBrewery(), styleCode).stream()
                .map(link -> new StyleDescriptorResponse(
                        DescriptorResponse.from(link.descriptor()), link.expected()))
                .toList();
    }

    @PostMapping
    ResponseEntity<DescriptorResponse> create(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody CreateRequest request) {
        principal.requirePermission("sensory.descriptor.write");
        var descriptor = commands.create(new DescriptorCommands.CreateCommand(
                principal.requireBrewery(), request.code(), request.name(), request.category(),
                request.synonyms() == null ? Set.of() : request.synonyms(),
                request.sourceName(), request.sourceReference(), request.licenseTier(),
                request.attribution(), request.perceptionThreshold(), request.thresholdUnit(),
                request.hypotheses() == null ? List.of()
                        : request.hypotheses().stream().map(HypothesisRequest::toDomain).toList(),
                principal.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DescriptorResponse.from(descriptor));
    }

    @PostMapping("/{descriptorId}/styles/{styleCode}")
    ResponseEntity<Void> linkToStyle(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID descriptorId, @PathVariable String styleCode,
            @RequestParam(defaultValue = "true") boolean expected) {
        principal.requirePermission("sensory.descriptor.write");
        commands.linkToStyle(principal.requireBrewery(), styleCode, descriptorId, expected,
                principal.userId());
        return ResponseEntity.noContent().build();
    }

    record CreateRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull DescriptorCategory category,
            Set<String> synonyms,
            @NotBlank @Size(max = 200) String sourceName,
            @Size(max = 500) String sourceReference,
            @NotNull LicenseTier licenseTier,
            @Size(max = 500) String attribution,
            BigDecimal perceptionThreshold,
            @Size(max = 20) String thresholdUnit,
            List<@Valid HypothesisRequest> hypotheses) {
    }

    /** Causa possível e como verificá-la. Nunca "diagnóstico" — ver {@link Hypothesis}. */
    record HypothesisRequest(
            @NotBlank @Size(max = 300) String possibleCause,
            @NotBlank @Size(max = 300) String suggestedCheck,
            @NotNull Hypothesis.Likelihood likelihood) {

        Hypothesis toDomain() {
            return new Hypothesis(possibleCause, suggestedCheck, likelihood);
        }
    }

    /**
     * @param exportable se este descritor pode sair da cervejaria. Explícito no contrato porque deduzi-lo
     *                   de {@code licenseTier} exigiria conhecer a regra de cada licença
     * @param attribution o texto a imprimir junto, quando a licença exige
     */
    record DescriptorResponse(UUID id, String code, String name, String category,
            List<String> synonyms, String sourceName, String sourceReference, String licenseTier,
            String attribution, boolean exportable, BigDecimal perceptionThreshold,
            String thresholdUnit, List<HypothesisResponse> hypotheses) {

        static DescriptorResponse from(SensoryDescriptor d) {
            return new DescriptorResponse(d.id(), d.code(), d.name(), d.category().name(),
                    d.synonyms().stream().sorted().toList(),
                    d.source().name(), d.source().referenceText().orElse(null),
                    d.source().tier().name(), d.source().attributionText().orElse(null),
                    d.exportable(),
                    d.perceptionThreshold().orElse(null), d.thresholdUnit().orElse(null),
                    d.hypotheses().stream()
                            .map(h -> new HypothesisResponse(h.possibleCause(), h.suggestedCheck(),
                                    h.likelihood().name()))
                            .toList());
        }
    }

    /** O nome do campo é a garantia: são hipóteses, não diagnóstico. */
    record HypothesisResponse(String possibleCause, String suggestedCheck, String likelihood) {
    }

    record StyleDescriptorResponse(DescriptorResponse descriptor, boolean expected) {
    }
}
