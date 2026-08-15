package br.com.brew.brassia.sales.application.port.outbound;

import br.com.brew.brassia.sales.domain.PriceSchedule;
import java.util.UUID;

/**
 * A linha do tempo de preço (SAL-001).
 *
 * <p>Carrega o {@link PriceSchedule} inteiro porque a invariante — em qualquer dia, no máximo um preço —
 * só é verificável olhando todas as vigências. Buscar "o preço atual" e decidir em cima disso deixaria
 * passar a sobreposição com um período fechado no meio.
 *
 * <p>{@link #applyChange} grava o que mudou — o encerramento do anterior e a abertura do novo, no mesmo
 * commit — e não regrava a linha do tempo. Um {@code save} que reescrevesse tudo permitiria, por
 * descuido, sumir com uma vigência antiga, que é o preço que explica um pedido de março. Separar as duas
 * escritas em chamadas distintas criaria um instante com dois preços abertos — justamente o que a
 * restrição de exclusão recusa, deixando a operação pela metade.
 */
public interface PriceRepository {

    PriceSchedule load(UUID breweryId, UUID productId, UUID channelId);

    void applyChange(UUID breweryId, UUID productId, UUID channelId, PriceSchedule.Change change,
            UUID actorId);
}
