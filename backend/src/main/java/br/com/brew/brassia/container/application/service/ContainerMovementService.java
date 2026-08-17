package br.com.brew.brassia.container.application.service;

import br.com.brew.brassia.container.ContainerMovementCommands;
import br.com.brew.brassia.container.domain.ContainerState;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a porta de movimentação (LOG-002).
 *
 * <p>Delega ao mesmo caso de uso da tela: <strong>a máquina de estados é uma só</strong>. Um caminho
 * paralelo aqui permitiria à distribuição fazer transições que a operação manual não faz, e as duas
 * versões divergiriam na primeira regra nova.
 */
public class ContainerMovementService implements ContainerMovementCommands {

    private final ContainerHandlers containers;

    public ContainerMovementService(ContainerHandlers containers) {
        this.containers = Objects.requireNonNull(containers);
    }

    @Override
    @Transactional
    public void dispatch(UUID breweryId, List<UUID> containerIds) {
        move(breweryId, containerIds, ContainerState.IN_TRANSIT);
    }

    @Override
    @Transactional
    public void deliver(UUID breweryId, List<UUID> containerIds) {
        move(breweryId, containerIds, ContainerState.AT_CUSTOMER);
    }

    @Override
    @Transactional
    public void collect(UUID breweryId, List<UUID> containerIds) {
        move(breweryId, containerIds, ContainerState.RETURNED);
    }

    private void move(UUID breweryId, List<UUID> containerIds, ContainerState to) {
        containerIds.forEach(id -> containers.move(breweryId, id, to));
    }
}
