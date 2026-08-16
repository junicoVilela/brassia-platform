package br.com.brew.brassia.community.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.community.application.port.inbound.LibraryCommands;
import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.application.port.outbound.RecipeForkRepository;
import br.com.brew.brassia.community.application.service.ForkHandlers;
import br.com.brew.brassia.community.domain.PublicRecipeSnapshot;
import br.com.brew.brassia.community.domain.PublishedRecipe;
import br.com.brew.brassia.community.domain.RecipeLicense;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import br.com.brew.brassia.community.domain.Visibility;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A biblioteca de receitas (COM-001).
 *
 * <p><strong>A resposta pública nunca carrega a cervejaria.</strong> O plano de testes é explícito —
 * "busca e feed não expõem tenant" —, e por isso a {@code PublicationView} não tem esse campo: quem lê a
 * biblioteca vê autor, licença, versão e o retrato, e não de qual casa aquilo veio.
 *
 * <p><strong>Publicação inacessível responde 404, e não 403.</strong> Numa biblioteca, distinguir "não
 * existe" de "é privada" permite enumerar o que as outras cervejarias têm sem ler nada — basta contar
 * quais identificadores respondem diferente.
 */
@RestController
@RequestMapping("/api/v1/community/library")
final class LibraryController {

    private static final int MAX_PAGE = 50;

    private final LibraryCommands commands;
    private final PublishedRecipeRepository library;
    private final ForkHandlers forks;
    private final RecipeForkRepository lineage;
    private final AuditTrail audit;

    LibraryController(LibraryCommands commands, PublishedRecipeRepository library, ForkHandlers forks,
            RecipeForkRepository lineage, AuditTrail audit) {
        this.commands = Objects.requireNonNull(commands);
        this.library = Objects.requireNonNull(library);
        this.forks = Objects.requireNonNull(forks);
        this.lineage = Objects.requireNonNull(lineage);
        this.audit = Objects.requireNonNull(audit);
    }

    /** A vitrine: só o que está no ar e é público, de qualquer cervejaria. */
    @GetMapping
    List<PublicationView> feed(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(defaultValue = "20") int limit) {
        principal.requirePermission("community.library.read");
        return library.listPublic(Math.min(Math.max(limit, 1), MAX_PAGE)).stream()
                .map(LibraryController::view).toList();
    }

    /** A estante do autor: o que esta cervejaria publicou, em qualquer visibilidade. */
    @GetMapping("/mine")
    List<OwnedView> mine(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("community.library.read");
        return library.listOwned(principal.requireBrewery()).stream()
                .map(LibraryController::owned).toList();
    }

    @GetMapping("/{id}")
    PublicationView read(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("community.library.read");
        return library.findForReader(id, principal.requireBrewery()).map(LibraryController::view)
                .orElseThrow(() -> new UnknownPublicationException(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> publish(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody PublishRequest request) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        var id = commands.publish(brewery, principal.userId(), principal.displayName(),
                request.recipeId(), request.title(), request.summary(), request.license(),
                request.visibility());
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.recipe.publish",
                "community.publication", id.toString(),
                Map.of("recipeId", request.recipeId().toString(),
                        "visibility", request.visibility().name(),
                        "license", request.license().name())));
        return Map.of("id", id);
    }

    @PutMapping("/{id}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void visibility(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody VisibilityRequest request) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        commands.changeVisibility(brewery, principal.userId(), id, request.visibility());
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.recipe.visibility",
                "community.publication", id.toString(),
                Map.of("visibility", request.visibility().name())));
    }

    @PutMapping("/{id}/license")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void license(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody LicenseRequest request) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        commands.relicense(brewery, principal.userId(), id, request.license());
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.recipe.license",
                "community.publication", id.toString(), Map.of("license", request.license().name())));
    }

    /** Tirar de circulação. Não apaga: o que já foi lido não se desfaz. */
    @PostMapping("/{id}/unpublish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unpublish(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("community.recipe.publish");
        var brewery = principal.requireBrewery();
        commands.unpublish(brewery, principal.userId(), id);
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.recipe.unpublish",
                "community.publication", id.toString(), Map.of()));
    }

    /**
     * Copiar a receita para a própria casa (COM-003).
     *
     * <p><strong>A cópia sai do retrato congelado, e não da receita do autor.</strong> O forkador leva o
     * que estava publicado naquele momento — nem mais, nem o que vier depois. Se o autor fechar a
     * publicação amanhã, esta receita continua sendo dele e a atribuição continua correta.
     *
     * <p>O equipamento é de quem copia: o do autor nunca saiu no retrato, e quem vai brassar escolhe o
     * seu.
     *
     * <p>Alçada de criar receita, e não uma nova: quem pode criar do zero pode criar inspirado na de
     * outro.
     */
    @PostMapping("/{id}/fork")
    @ResponseStatus(HttpStatus.CREATED)
    ForkedView fork(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody ForkRequest request) {
        principal.requirePermission("recipe.create");
        var brewery = principal.requireBrewery();
        var result = forks.fork(brewery, principal.userId(), id, request.name(), request.equipmentId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "community.recipe.fork",
                "recipe.recipe", result.recipeId().toString(),
                Map.of("sourcePublicationId", id.toString())));
        return new ForkedView(result.recipeId(), result.origin().attribution(),
                result.origin().sourceLicense().name(),
                result.origin().requiredLicenseForDerivative().map(Enum::name).orElse(null));
    }

    /** Quantas cópias esta publicação gerou — a pergunta do autor. */
    @GetMapping("/{id}/forks")
    Map<String, Integer> forkCount(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id) {
        principal.requirePermission("community.library.read");
        library.findForReader(id, principal.requireBrewery())
                .orElseThrow(() -> new UnknownPublicationException(id));
        return Map.of("count", lineage.countForksOf(id));
    }

    private static PublicationView view(PublishedRecipe p) {
        return new PublicationView(p.id(), p.title(), p.summary().orElse(null), p.authorDisplayName(),
                p.license().name(), p.license().label(), p.recipeVersion(), p.publishedAt(),
                p.forkableByOthers(), p.snapshot());
    }

    private static OwnedView owned(PublishedRecipe p) {
        return new OwnedView(p.id(), p.title(), p.recipeId(), p.recipeVersion(), p.license().name(),
                p.visibility().name(), p.isPublished(), p.publishedAt());
    }

    record PublishRequest(@NotNull UUID recipeId, @NotBlank @Size(max = 160) String title,
            @Size(max = 1000) String summary, @NotNull RecipeLicense license,
            @NotNull Visibility visibility) {}

    record VisibilityRequest(@NotNull Visibility visibility) {}

    record ForkRequest(@Size(max = 160) String name, @NotNull UUID equipmentId) {}

    /**
     * A resposta do fork traz a atribuição pronta e a obrigação de licença, se houver.
     *
     * <p>{@code requiredLicense} não nulo significa que a licença de origem se propaga (CC BY-SA) — e
     * dizer isso na resposta evita o forkador descobrir a obrigação quando for publicar.
     */
    record ForkedView(UUID recipeId, String attribution, String sourceLicense,
            String requiredLicense) {}

    record LicenseRequest(@NotNull RecipeLicense license) {}

    /**
     * O que o público vê.
     *
     * <p><strong>Sem cervejaria e sem identificador de receita.</strong> O primeiro é o inquilino; o
     * segundo permitiria tentar a receita pelos endpoints internos e descobrir, pelo código de resposta,
     * o que existe do outro lado.
     */
    record PublicationView(UUID id, String title, String summary, String author, String license,
            String licenseLabel, long recipeVersion, Instant publishedAt, boolean forkable,
            PublicRecipeSnapshot recipe) {}

    /** A visão do autor sobre a própria estante — aqui o id da receita é dele mesmo. */
    record OwnedView(UUID id, String title, UUID recipeId, long recipeVersion, String license,
            String visibility, boolean published, Instant publishedAt) {}
}
