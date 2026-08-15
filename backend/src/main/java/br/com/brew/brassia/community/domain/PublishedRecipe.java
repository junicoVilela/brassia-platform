package br.com.brew.brassia.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma versão de receita publicada na biblioteca (COM-001).
 *
 * <p><strong>Publica-se uma versão, e não uma receita.</strong> A receita continua evoluindo em casa; o
 * que está lá fora é o retrato de uma versão específica, com o número dela à vista. Publicar "a receita"
 * faria cada ajuste interno mudar em silêncio o que o público lê — e o autor descobriria ter publicado
 * algo que nunca revisou.
 *
 * <p><strong>O aceite exige quatro coisas na publicação: autor, licença, fonte e versão.</strong> As
 * quatro são obrigatórias aqui, e não campos opcionais que a tela preenche quando lembra: sem autor não
 * há a quem atribuir, sem licença ninguém sabe o que pode fazer, sem versão a fonte não é reproduzível, e
 * sem fonte a atribuição é uma afirmação sem lastro.
 *
 * <p><strong>Despublicar não apaga.</strong> O que já foi lido não se desfaz, e um fork feito enquanto a
 * receita estava pública continua legítimo — inclusive a linhagem que aponta para cá (COM-003). O que a
 * despublicação faz é tirar de circulação daqui para a frente.
 */
public final class PublishedRecipe {

    private static final int MAX_TITLE = 160;
    private static final int MAX_SUMMARY = 1000;

    private final UUID id;
    private final UUID breweryId;
    private final UUID recipeId;
    private final long recipeVersion;
    private final UUID authorUserId;
    private final String authorDisplayName;
    private String title;
    private String summary;
    private RecipeLicense license;
    private Visibility visibility;
    private final PublicRecipeSnapshot snapshot;
    private final Instant publishedAt;
    private Instant unpublishedAt;

    private PublishedRecipe(UUID id, UUID breweryId, UUID recipeId, long recipeVersion,
            UUID authorUserId, String authorDisplayName, String title, String summary,
            RecipeLicense license, Visibility visibility, PublicRecipeSnapshot snapshot,
            Instant publishedAt, Instant unpublishedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria");
        this.recipeId = Objects.requireNonNull(recipeId, "receita");
        this.recipeVersion = recipeVersion;
        this.authorUserId = Objects.requireNonNull(authorUserId, "autor");
        this.authorDisplayName = Objects.requireNonNull(authorDisplayName, "nome do autor");
        this.title = title;
        this.summary = summary;
        this.license = Objects.requireNonNull(license, "licença");
        this.visibility = Objects.requireNonNull(visibility, "visibilidade");
        this.snapshot = Objects.requireNonNull(snapshot, "retrato público");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publicado em");
        this.unpublishedAt = unpublishedAt;
    }

    public static PublishedRecipe publish(UUID id, UUID breweryId, UUID recipeId, long recipeVersion,
            UUID authorUserId, String authorDisplayName, String title, String summary,
            RecipeLicense license, Visibility visibility, PublicRecipeSnapshot snapshot,
            Instant publishedAt) {
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("a versão publicada é obrigatória");
        }
        return new PublishedRecipe(id, breweryId, recipeId, recipeVersion, authorUserId,
                authorDisplayName, required(title, "título", MAX_TITLE),
                optional(summary, "resumo", MAX_SUMMARY), license, visibility, snapshot, publishedAt,
                null);
    }

    public static PublishedRecipe reconstitute(UUID id, UUID breweryId, UUID recipeId, long recipeVersion,
            UUID authorUserId, String authorDisplayName, String title, String summary,
            RecipeLicense license, Visibility visibility, PublicRecipeSnapshot snapshot,
            Instant publishedAt, Instant unpublishedAt) {
        return new PublishedRecipe(id, breweryId, recipeId, recipeVersion, authorUserId,
                authorDisplayName, title, summary, license, visibility, snapshot, publishedAt,
                unpublishedAt);
    }

    /**
     * Muda quem enxerga.
     *
     * <p>Fechar é sempre possível. Abrir depois de despublicar, não: republicar é ato novo, com data
     * nova — senão a linha do tempo diria que esteve público o tempo todo.
     */
    public void changeVisibility(Visibility newVisibility) {
        requirePublished();
        this.visibility = Objects.requireNonNull(newVisibility, "visibilidade");
    }

    /**
     * Muda a licença.
     *
     * <p><strong>Vale daqui para a frente, e não retroage.</strong> Quem já copiou sob a licença antiga
     * copiou sob a licença antiga — mudar não desfaz o que foi autorizado, e fingir que desfaz seria a
     * plataforma prometendo um controle que ela não tem sobre o que já saiu.
     */
    public void relicense(RecipeLicense newLicense) {
        requirePublished();
        this.license = Objects.requireNonNull(newLicense, "licença");
    }

    public void edit(String title, String summary) {
        requirePublished();
        this.title = required(title, "título", MAX_TITLE);
        this.summary = optional(summary, "resumo", MAX_SUMMARY);
    }

    public void unpublish(Instant at) {
        requirePublished();
        this.unpublishedAt = Objects.requireNonNull(at, "instante");
    }

    public boolean isPublished() {
        return unpublishedAt == null;
    }

    /**
     * Se este item aparece em busca e feed.
     *
     * <p>Despublicado nunca aparece, qualquer que seja a visibilidade guardada — a ordem das duas
     * checagens importa, porque o contrário faria uma receita despublicada com {@code PUBLIC} salvo
     * continuar listada.
     */
    public boolean listed() {
        return isPublished() && visibility.listed();
    }

    /** Se quem tem o endereço, e não é da cervejaria do autor, consegue ler. */
    public boolean readableByOutsider() {
        return isPublished() && visibility.reachableFromOutside();
    }

    /** Se terceiros podem forkar (COM-003): precisa estar publicado e a licença precisa permitir. */
    public boolean forkableByOthers() {
        return isPublished() && visibility.reachableFromOutside() && license.allowsDerivatives();
    }

    private void requirePublished() {
        if (!isPublished()) {
            throw new RecipeUnpublishedException(id);
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("o " + field + " da publicação é obrigatório");
        }
        var clean = value.strip();
        if (clean.length() > max) {
            throw new IllegalArgumentException("o " + field + " passa de " + max + " caracteres");
        }
        return clean;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var clean = value.strip();
        if (clean.length() > max) {
            throw new IllegalArgumentException("o " + field + " passa de " + max + " caracteres");
        }
        return clean;
    }

    public UUID id() {
        return id;
    }

    /** Interno: nunca sai numa resposta pública. Existe para a autorização do lado de dentro. */
    public UUID breweryId() {
        return breweryId;
    }

    public UUID recipeId() {
        return recipeId;
    }

    public long recipeVersion() {
        return recipeVersion;
    }

    public UUID authorUserId() {
        return authorUserId;
    }

    public String authorDisplayName() {
        return authorDisplayName;
    }

    public String title() {
        return title;
    }

    public Optional<String> summary() {
        return Optional.ofNullable(summary);
    }

    public RecipeLicense license() {
        return license;
    }

    public Visibility visibility() {
        return visibility;
    }

    public PublicRecipeSnapshot snapshot() {
        return snapshot;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public Optional<Instant> unpublishedAt() {
        return Optional.ofNullable(unpublishedAt);
    }
}
