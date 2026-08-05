package br.com.brew.brassia.traceability.application.port.inbound;

import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.domain.Recall;
import br.com.brew.brassia.traceability.domain.RecallNotification;
import br.com.brew.brassia.traceability.domain.Spread;
import java.util.List;
import java.util.UUID;

/** Leituras do recall (FDS-003). */
public interface RecallQueries {

    List<Recall> list(UUID breweryId);

    /**
     * O dossiê: a decisão registrada, o escopo de hoje e as lacunas.
     *
     * @throws br.com.brew.brassia.traceability.domain.UnknownRecallException id inexistente
     */
    Dossier dossier(UUID breweryId, UUID recallId, int depth);

    /**
     * @param notifications  destinos alcançados na abertura, com o que já foi comunicado — a parte
     *                       guardada, porque é fato sobre o que a cervejaria fez
     * @param spread         o escopo derivado agora
     * @param newDestinations expedições que hoje estão no escopo e não estavam na abertura: o lote
     *                       saiu depois. Aparecem separadas em vez de entrar caladas na lista, porque
     *                       "avisado" e "descoberto agora" são coisas diferentes num dossiê
     * @param gaps           lotes do escopo sem expedição registrada — não se sabe onde estão
     */
    record Dossier(Recall recall, List<RecallNotification> notifications, Spread spread,
            List<NewDestination> newDestinations, List<Gap> gaps) {}

    record NewDestination(UUID shipmentId, String destination, String contact, int units) {}
}
