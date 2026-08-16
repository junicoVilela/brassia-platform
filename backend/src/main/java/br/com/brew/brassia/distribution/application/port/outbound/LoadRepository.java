package br.com.brew.brassia.distribution.application.port.outbound;

import br.com.brew.brassia.distribution.domain.Load;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadRepository {

    void save(Load load);

    /**
     * Grava a carga inteira: cabeçalho, paradas e itens.
     *
     * <p>Uma carga é um agregado pequeno e que muda por inteiro — salvar parte dela deixaria roteiro e
     * itens divergirem entre si, que é o defeito que o romaneio impresso denuncia tarde demais.
     */
    void update(Load load);

    Optional<Load> find(UUID breweryId, UUID id);

    List<Load> list(UUID breweryId, LocalDate day);

    /** Em que outra carga aberta este vasilhame já está — o engano do dia a dia (DEB-LOG-001). */
    Optional<String> openLoadWith(UUID breweryId, UUID containerId, UUID exceptLoadId);
}
