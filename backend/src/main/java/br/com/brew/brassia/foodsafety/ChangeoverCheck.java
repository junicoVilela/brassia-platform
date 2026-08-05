package br.com.brew.brassia.foodsafety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Veredito de troca de produto num equipamento (FDS-001), para que quem agenda o uso — hoje o
 * envase (PKG-001) — recuse a troca insegura sem conhecer a matriz de alergênicos.
 *
 * <p>Quem sabe qual foi o uso anterior é o módulo que agenda: o envase conhece o último plano
 * daquela linha, a produção conhecerá o último lote daquele tanque. Por isso o uso anterior é
 * <em>parâmetro</em>, e não algo que a segurança de alimentos vá descobrir lendo tabela alheia.
 */
public interface ChangeoverCheck {

    /**
     * @param equipmentId      equipamento que vai receber o produto
     * @param incomingBatchId  lote que vai entrar
     * @param previousBatchId  lote do uso anterior; {@code null} quando não houve uso anterior
     * @param previousUseAt    início do uso anterior; {@code null} junto com {@code previousBatchId}
     * @param at               instante de referência (início planejado do uso que entra)
     */
    Verdict check(UUID breweryId, UUID equipmentId, UUID incomingBatchId, UUID previousBatchId,
            Instant previousUseAt, Instant at);

    /**
     * @param code      motivo estável do bloqueio ({@code allergen_undeclared},
     *                  {@code allergen_dedication_violated}, {@code allergen_changeover_required})
     * @param allergens alergênicos em questão — sem eles o operador não sabe qual POP resolve
     * @param undeclaredIngredients ingredientes sem declaração, quando o bloqueio é a lacuna
     */
    record Verdict(boolean allowed, String code, String detail, List<String> allergens,
            List<String> undeclaredIngredients) {

        public Verdict {
            allergens = List.copyOf(allergens);
            undeclaredIngredients = List.copyOf(undeclaredIngredients);
        }
    }
}
