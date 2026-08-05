package br.com.brew.brassia.traceability.application.port.inbound;

import br.com.brew.brassia.traceability.domain.Quarantine;
import br.com.brew.brassia.traceability.domain.Spread;
import java.util.List;
import java.util.UUID;

/** Leituras da quarentena (FDS-002). */
public interface QuarantineQueries {

    List<Quarantine> list(UUID breweryId, boolean onlyOpen);

    /**
     * A quarentena e o que ela alcança hoje.
     *
     * <p>O alcance é recalculado a cada leitura: um envase feito depois da abertura aparece aqui,
     * porque ele nasceu bloqueado. Uma lista congelada na abertura mentiria no dia seguinte.
     *
     * @throws br.com.brew.brassia.traceability.domain.UnknownQuarantineException id inexistente
     */
    Detail detail(UUID breweryId, UUID quarantineId, int depth);

    record Detail(Quarantine quarantine, Spread spread) {}
}
