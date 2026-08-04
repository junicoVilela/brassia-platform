package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Regra de limpeza da linha de envase (PKG-001): a linha só é considerada limpa quando existe
 * um ciclo de sanitização <strong>liberado</strong> que (a) foi liberado antes do início planejado
 * e (b) é posterior ao último envase que já ocupou a linha. Sem a segunda condição, uma liberação
 * antiga cobriria indefinidamente envases seguintes na mesma linha.
 *
 * <p>O prazo de validade por tempo (shelf life do CIP) é <strong>parâmetro da cervejaria</strong>
 * (PRM-001), respondido pela sanitização, que é dona do conceito. Aqui só se pergunta se a
 * liberação ainda cobre o início planejado — o envase não sabe quantas horas são, nem deveria.
 */
public final class LineCleanliness {

    private LineCleanliness() {
    }

    /**
     * @param releasedAt   liberação do último ciclo de limpeza da linha; {@code null} se nunca houve
     * @param lastUseAt    início do último envase reservado na linha; {@code null} se a linha é nova
     * @param plannedStart início planejado do envase que está sendo reservado
     * @param stillCovered se a liberação ainda cobre o início planejado, segundo a política de
     *                     limpeza da cervejaria; sem prazo configurado, sempre cobre
     * @return o bloqueio, quando a linha não está comprovadamente limpa
     */
    public static Optional<PackagingBlockedException.Blocker> check(
            Instant releasedAt, Instant lastUseAt, Instant plannedStart, boolean stillCovered) {
        if (releasedAt == null) {
            return Optional.of(new PackagingBlockedException.Blocker("line_not_clean",
                    "A linha não tem ciclo de limpeza liberado."));
        }
        if (releasedAt.isAfter(plannedStart)) {
            return Optional.of(new PackagingBlockedException.Blocker("line_not_clean",
                    "A limpeza da linha foi liberada depois do início planejado do envase."));
        }
        if (!stillCovered) {
            return Optional.of(new PackagingBlockedException.Blocker("line_not_clean",
                    "A liberação de limpeza da linha venceu antes do início planejado do envase."));
        }
        if (lastUseAt != null && !releasedAt.isAfter(lastUseAt)) {
            return Optional.of(new PackagingBlockedException.Blocker("line_not_clean",
                    "A linha foi usada em outro envase depois da última limpeza liberada."));
        }
        return Optional.empty();
    }
}
