package br.com.brew.brassia.distribution.application.port.outbound;

import br.com.brew.brassia.distribution.domain.ProofOfDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProofRepository {

    /** Só grava; nunca atualiza. O append-only é do repositório, e não uma disciplina de quem chama. */
    void record(UUID breweryId, ProofOfDelivery proof);

    /** A prova original da parada, se já houver. */
    Optional<ProofOfDelivery> originalOf(UUID breweryId, UUID stopId);

    /** Tudo o que foi registrado naquela parada — original e correção, na ordem. */
    List<ProofOfDelivery> ofStop(UUID breweryId, UUID stopId);

    /** As provas da carga inteira, para a tela do dia. */
    List<ProofOfDelivery> ofLoad(UUID breweryId, UUID loadId);
}
