package br.com.brew.brassia.community.adapter.outbound.persistence;

import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.domain.PublicRecipeSnapshot;
import br.com.brew.brassia.community.domain.PublishedRecipe;
import br.com.brew.brassia.community.domain.RecipeLicense;
import br.com.brew.brassia.community.domain.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPublishedRecipeRepository implements PublishedRecipeRepository {

    /**
     * Mapper próprio, como no webhook.
     *
     * <p>O retrato é o que sai para fora e fica congelado: herdar a configuração de serialização da API
     * HTTP faria uma mudança de formato para a tela reescrever o significado de algo já publicado.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;

    JdbcPublishedRecipeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PublishedRecipe p) {
        jdbc.sql("""
                INSERT INTO community_published_recipe (id, brewery_id, recipe_id, recipe_version,
                        author_user_id, author_display_name, title, summary, license, visibility,
                        snapshot, published_at, unpublished_at)
                VALUES (:id, :brewery, :recipe, :version, :author, :authorName, :title, :summary,
                        :license, :visibility, CAST(:snapshot AS jsonb), :publishedAt, NULL)
                """)
                .param("id", p.id()).param("brewery", p.breweryId()).param("recipe", p.recipeId())
                .param("version", p.recipeVersion()).param("author", p.authorUserId())
                .param("authorName", p.authorDisplayName()).param("title", p.title())
                .param("summary", p.summary().orElse(null))
                .param("license", p.license().name()).param("visibility", p.visibility().name())
                .param("snapshot", jsonb(p.snapshot()))
                .param("publishedAt", Timestamp.from(p.publishedAt()))
                .update();
    }

    @Override
    public void update(PublishedRecipe p) {
        // O retrato NÃO entra no UPDATE: ele é congelado no momento da publicação. Permitir reescrevê-lo
        // faria a edição privada de amanhã alterar em silêncio o que o público já leu.
        jdbc.sql("""
                UPDATE community_published_recipe
                SET title = :title, summary = :summary, license = :license, visibility = :visibility,
                    unpublished_at = :unpublishedAt
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("title", p.title()).param("summary", p.summary().orElse(null))
                .param("license", p.license().name()).param("visibility", p.visibility().name())
                .param("unpublishedAt", p.unpublishedAt().map(Timestamp::from).orElse(null))
                .param("id", p.id()).param("brewery", p.breweryId())
                .update();
    }

    @Override
    public Optional<PublishedRecipe> findOwned(UUID breweryId, UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery")
                .param("id", id).param("brewery", breweryId)
                .query(JdbcPublishedRecipeRepository::map).optional();
    }

    @Override
    public Optional<PublishedRecipe> findForReader(UUID id, UUID readerBreweryId) {
        // A regra de leitura vive no SQL, e não numa checagem depois de carregar: carregar primeiro e
        // decidir depois é o padrão em que alguém, um dia, esquece o "depois".
        //
        // LINK NÃO ESTÁ AQUI, e isso é a correção que a COM-002 trouxe: na COM-001 ele era legível por
        // qualquer autenticado que soubesse o identificador — semântica de UNLISTED, e não de LINK.
        // Quem chega por link entra pelo caminho do token (ShareLinkHandlers.resolve), que confere o
        // segredo E a visibilidade. UNLISTED continua sendo "abre por endereço direto, sem segredo".
        return jdbc.sql(SELECT + """
                 WHERE id = :id
                   AND unpublished_at IS NULL
                   AND (visibility IN ('UNLISTED', 'PUBLIC')
                        OR (visibility = 'BREWERY' AND brewery_id = :reader))
                """)
                .param("id", id).param("reader", readerBreweryId)
                .query(JdbcPublishedRecipeRepository::map).optional();
    }

    @Override
    public List<PublishedRecipe> listPublic(int limit) {
        // Sem filtro por cervejaria, de propósito: a biblioteca pública não é de ninguém, e filtrar por
        // inquilino mostraria a cada um só o que ele mesmo publicou.
        return jdbc.sql(SELECT + """
                 WHERE unpublished_at IS NULL AND visibility = 'PUBLIC'
                 ORDER BY published_at DESC LIMIT :limit
                """)
                .param("limit", limit)
                .query(JdbcPublishedRecipeRepository::map).list();
    }

    @Override
    public List<PublishedRecipe> listOwned(UUID breweryId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery ORDER BY published_at DESC")
                .param("brewery", breweryId)
                .query(JdbcPublishedRecipeRepository::map).list();
    }

    @Override
    public boolean versionAlreadyPublished(UUID recipeId, long recipeVersion) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM community_published_recipe
                WHERE recipe_id = :recipe AND recipe_version = :version
                """)
                .param("recipe", recipeId).param("version", recipeVersion)
                .query(Integer.class).single() > 0;
    }

    private static final String SELECT = """
            SELECT id, brewery_id, recipe_id, recipe_version, author_user_id, author_display_name,
                   title, summary, license, visibility, snapshot, published_at, unpublished_at
            FROM community_published_recipe
            """;

    /**
     * O retrato como texto, convertido no SQL com {@code CAST(... AS jsonb)}.
     *
     * <p>É o mesmo caminho que o resto do projeto usa (ver {@code JdbcIngredientRepository}), e evita
     * depender de um tipo do driver no código de aplicação — o dia em que o driver mudar, nada aqui
     * precisa mudar junto.
     */
    private static String jsonb(PublicRecipeSnapshot snapshot) {
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar o retrato público", e);
        }
    }

    private static PublishedRecipe map(ResultSet rs, int row) throws SQLException {
        PublicRecipeSnapshot snapshot;
        try {
            snapshot = JSON.readValue(rs.getString("snapshot"), PublicRecipeSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("retrato público ilegível", e);
        }
        var unpublished = rs.getTimestamp("unpublished_at");
        return PublishedRecipe.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class), rs.getObject("recipe_id", UUID.class),
                rs.getLong("recipe_version"), rs.getObject("author_user_id", UUID.class),
                rs.getString("author_display_name"), rs.getString("title"), rs.getString("summary"),
                RecipeLicense.valueOf(rs.getString("license")),
                Visibility.valueOf(rs.getString("visibility")), snapshot,
                rs.getTimestamp("published_at").toInstant(),
                unpublished == null ? null : unpublished.toInstant());
    }
}
