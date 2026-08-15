package br.com.brew.brassia.sales.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Unidades de um lote acabado seguradas para um pedido (SAL-002).
 *
 * <p><strong>A reserva aponta para o lote, e não só para o produto.</strong> É o que faz o pedido manter
 * rastreio: quando um recall alcança um lote, a pergunta "quem comprou disto?" precisa ter resposta antes
 * de a cerveja sair. Reservar "10 unidades de IPA lata" não diz de qual brassa, e um recall teria de
 * avisar todo mundo que comprou IPA.
 *
 * <p><strong>A validade do lote viaja junto e é congelada.</strong> Não por desconfiança do dado, mas
 * porque é ela que sustenta a promessa de entrega: prometer para depois de a cerveja vencer é o erro que
 * esta história existe para impedir, e a checagem precisa do número que valia quando se prometeu.
 *
 * @param bestBefore validade do lote no momento da reserva
 */
public record LotReservation(UUID finishedLotId, String lotCode, int units, LocalDate bestBefore) {

    public LotReservation {
        Objects.requireNonNull(finishedLotId, "lote acabado");
        Objects.requireNonNull(bestBefore, "validade do lote");
        if (lotCode == null || lotCode.isBlank()) {
            throw new IllegalArgumentException("o código do lote é obrigatório");
        }
        lotCode = lotCode.strip();
        if (units <= 0) {
            // Reserva de zero não segura nada e ocuparia lugar como se segurasse.
            throw new IllegalArgumentException("a reserva deve ter unidades");
        }
    }
}
