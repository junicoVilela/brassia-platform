package br.com.brew.brassia.sales.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * O preço novo cairia em cima de uma vigência que já existe (SAL-001).
 *
 * <p>Recusar é o ponto. Se dois preços valem no mesmo dia, "quanto custa hoje?" tem duas respostas e o
 * sistema escolhe uma pela ordem em que leu as linhas — o pedido sai com um valor e a fatura com outro.
 *
 * <p>A recusa também é o que impede o domínio de adivinhar. Sobrepor um período já fechado pode querer
 * dizer encurtar o antigo, dividi-lo em dois ou substituí-lo, e escolher por conta própria seria
 * reescrever preço histórico sem que ninguém tenha pedido.
 */
public class OverlappingPriceException extends RuntimeException {

    private final LocalDate from;

    public OverlappingPriceException(UUID productId, UUID channelId, LocalDate from) {
        super("já existe preço vigente para este produto e canal em " + from);
        this.from = from;
    }

    public LocalDate from() {
        return from;
    }
}
