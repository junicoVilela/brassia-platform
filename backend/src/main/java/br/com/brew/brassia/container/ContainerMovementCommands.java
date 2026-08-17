package br.com.brew.brassia.container;

import java.util.List;
import java.util.UUID;

/**
 * Mover vasilhames pelo ciclo, a pedido de quem os movimenta de verdade (LOG-002).
 *
 * <p><strong>Porta de escrita publicada por quem tem o dado</strong> (ADR-0016). A distribuição sabe
 * quando o caminhão saiu, quando o keg desceu no bar e quando os vazios voltaram; o ciclo do vasilhame
 * mora aqui. A alternativa — a distribuição escrever na tabela de contêineres — furaria a fronteira e
 * espalharia a máquina de estados por dois módulos.
 *
 * <p><strong>É esta porta que fecha o {@code DEB-LOG-001}</strong>: um vasilhame que sai da casa deixa de
 * estar {@code FILLED}, e o próprio ciclo passa a impedir que ele entre numa segunda carga — sem depender
 * de uma checagem que duas telas simultâneas contornam.
 */
public interface ContainerMovementCommands {

    /** A carga saiu: o que estava cheio no depósito agora está na rua. */
    void dispatch(UUID breweryId, List<UUID> containerIds);

    /** Desceu no cliente. */
    void deliver(UUID breweryId, List<UUID> containerIds);

    /**
     * Os vazios voltaram.
     *
     * <p>Volta como {@code RETURNED}, e não como disponível: o que voltou do cliente está sujo até que
     * alguém diga o contrário (CON-001). E o período do lote se fecha aqui.
     */
    void collect(UUID breweryId, List<UUID> containerIds);
}
