package br.com.brew.brassia.community.application.port.outbound;

import br.com.brew.brassia.community.domain.Contribution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência dos comentários e sugestões (COM-004).
 *
 * <p>{@link #listVisible} não recebe cervejaria: a conversa de uma publicação pública é de todos que a
 * alcançam. Quem pode ver a publicação vê a conversa — a autorização acontece antes, na publicação.
 */
public interface ContributionRepository {

    /**
     * @param breweryId a cervejaria de quem escreveu — ela vai na tabela para a moderação e o
     *                  isolamento das consultas internas, e <strong>nunca</strong> na resposta pública:
     *                  quem lê vê o nome, e não de onde a pessoa é
     */
    void insert(UUID breweryId, Contribution contribution);

    /**
     * @param actingBreweryId a cervejaria de quem está decidindo ou moderando — <strong>a dona da
     *                        publicação</strong>, e não a de quem escreveu. A escrita é filtrada por ela
     *                        no próprio SQL: sem isso, a garantia dependeria de o handler lembrar de
     *                        conferir antes, que é o padrão que a OBS-REL-001 encontrou em dez escritas
     */
    void update(UUID actingBreweryId, Contribution contribution);

    Optional<Contribution> find(UUID id);

    List<Contribution> listVisible(UUID publicationId);

    int countPending(UUID publicationId);
}
