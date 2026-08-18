package br.com.brew.brassia.distribution;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Quando o cliente recebeu uma entrega pela última vez (DUV-CRM-001).
 *
 * <p>Mesma direção do {@code CustomerActivityLookup} de vendas: quem tem o dado responde. A entrega é
 * relacionamento por si — um bar que recebeu keg no mês passado teve contato com a casa mesmo que o
 * pedido seja antigo.
 *
 * <p><strong>Vale a tentativa, e não só o sucesso.</strong> Uma entrega recusada ou uma visita em que
 * ninguém estava também são relacionamento: o caminhão foi até lá. Contar só o entregue faria o relógio
 * da retenção correr para quem a cervejaria acabou de visitar.
 */
public interface CustomerDeliveryLookup {

    Optional<LocalDate> lastDeliveryOn(UUID breweryId, UUID customerId);
}
