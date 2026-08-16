package br.com.brew.brassia.container.application.port.outbound;

import br.com.brew.brassia.container.domain.Container;
import br.com.brew.brassia.container.domain.ContainerIdentifier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerRepository {

    void save(Container container);

    void update(Container container);

    Optional<Container> find(UUID breweryId, UUID id);

    List<Container> list(UUID breweryId, String state);

    void assign(ContainerIdentifier identifier);

    void retireIdentifier(UUID breweryId, UUID identifierId, java.time.Instant at);

    List<ContainerIdentifier> identifiersOf(UUID containerId);

    /**
     * Resolve uma leitura de código para um contêiner <strong>da própria cervejaria</strong>.
     *
     * <p>O escopo está aqui, e não no chamador: ler é a operação mais fácil de fazer errado, porque o
     * código chega de um leitor de mão e não de uma tela com contexto.
     */
    Optional<Container> resolve(UUID breweryId, String value);
}
