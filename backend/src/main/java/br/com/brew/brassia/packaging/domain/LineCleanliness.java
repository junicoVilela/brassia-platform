package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Regra de limpeza da linha de envase (PKG-001): a linha só é considerada limpa quando existe
 * um ciclo de sanitização <strong>liberado</strong> que (a) foi liberado antes do início planejado
 * e (b) é posterior ao último envase que já ocupou a linha. Sem a segunda condição, uma liberação
 * antiga cobriria indefinidamente envases seguintes na mesma linha.
 *
 * <p>Prazo de validade da limpeza por tempo (shelf life do CIP) não é decidido aqui: o número
 * depende do POP e da cervejaria, e inventá-lo criaria regra de negócio sem fonte (PKG-001-A).
 */
public final class LineCleanliness {

    private LineCleanliness() {
    }

    /**
     * @param releasedAt   liberação do último ciclo de limpeza da linha; {@code null} se nunca houve
     * @param lastUseAt    início do último envase reservado na linha; {@code null} se a linha é nova
     * @param plannedStart início planejado do envase que está sendo reservado
     * @return o bloqueio, quando a linha não está comprovadamente limpa
     */
    public static Optional<PackagingBlockedException.Blocker> check(
            Instant releasedAt, Instant lastUseAt, Instant plannedStart) {
        if (releasedAt == null) {
            return Optional.of(new PackagingBlockedException.Blocker("line_not_clean",
                    "A linha não tem ciclo de limpeza liberado."));
        }
        if (releasedAt.isAfter(plannedStart)) {
            return Optional.of(new PackagingBlockedException.Blocker("line_not_clean",
                    "A limpeza da linha foi liberada depois do início planejado do envase."));
        }
        if (lastUseAt != null && !releasedAt.isAfter(lastUseAt)) {
            return Optional.of(new PackagingBlockedException.Blocker("line_not_clean",
                    "A linha foi usada em outro envase depois da última limpeza liberada."));
        }
        return Optional.empty();
    }
}
