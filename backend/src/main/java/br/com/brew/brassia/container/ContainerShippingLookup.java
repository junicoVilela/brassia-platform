package br.com.brew.brassia.container;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * O que a distribuição precisa saber sobre um vasilhame antes de pô-lo no caminhão (LOG-001).
 *
 * <p><strong>Quem compõe é este módulo, e não quem pergunta.</strong> O contêiner, o conteúdo e o lote já
 * estão ao alcance daqui — {@code container} já depende de {@code packaging}. Se {@code distribution}
 * compusesse, precisaria de duas dependências para responder uma pergunta só, e cada critério novo de
 * saída viraria uma dependência nova lá. É o mesmo desenho do {@code SellableLotLookup}.
 *
 * <p><strong>O resultado diz por que NÃO pode sair.</strong> Um booleano faria a tela dizer "indisponível"
 * e o operador ligar para o depósito perguntando o motivo — e os motivos levam a ações diferentes: keg
 * vazio se enche, lote não liberado se cobra da qualidade, quarentena não se resolve hoje.
 */
public interface ContainerShippingLookup {

    Optional<ShippableContainer> shippable(UUID breweryId, UUID containerId);

    /**
     * @param volumeLiters o que há dentro, para a conta de capacidade do veículo
     * @param blocker      vazio quando o vasilhame pode sair
     */
    record ShippableContainer(UUID containerId, String code, String lotCode, BigDecimal volumeLiters,
            boolean shippable, Optional<Blocker> blocker) {}

    /**
     * @param code    código estável: {@code container_empty}, {@code not_released}, {@code expired},
     *                {@code quarantined}, {@code quarantine_suspected}, {@code shelf_life_unknown} ou
     *                {@code wrong_state}
     * @param message frase pronta para quem monta a carga, que diz o que fazer
     */
    record Blocker(String code, String message) {}
}
