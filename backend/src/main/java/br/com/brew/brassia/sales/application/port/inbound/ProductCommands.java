package br.com.brew.brassia.sales.application.port.inbound;

import br.com.brew.brassia.shared.money.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O que se faz com produto, canal e preço (SAL-001).
 *
 * <p>{@link #priceFrom} recebe a data de início e não a calcula: um preço combinado para valer no
 * primeiro dia do mês que vem precisa poder ser cadastrado hoje, e um que já valia desde a semana
 * passada precisa poder ser regularizado. Carimbar "hoje" tiraria as duas coisas.
 */
public interface ProductCommands {

    UUID createProduct(UUID breweryId, UUID actorId, String sku, String name, UUID recipeId,
            UUID containerId);

    void renameProduct(UUID breweryId, UUID actorId, UUID productId, String name);

    void setProductActive(UUID breweryId, UUID actorId, UUID productId, boolean active);

    UUID createChannel(UUID breweryId, UUID actorId, String code, String name);

    void setChannelActive(UUID breweryId, UUID actorId, UUID channelId, boolean active);

    /** "A partir deste dia, passa a custar isto." Fecha sozinho o preço em aberto, na véspera. */
    void priceFrom(UUID breweryId, UUID actorId, UUID productId, UUID channelId, Money price,
            boolean taxIncluded, LocalDate from);
}
