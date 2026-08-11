package br.com.brew.brassia.blend.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Saída que ainda não é lote (BLD-001, DEC-BLD-003).
 *
 * <p>É o resultado do blend antes de existir: o lote nasce na execução, e não na simulação, porque antes
 * de executar nenhuma cerveja se tocou. Um lote criado na simulação apareceria nas telas de produção como
 * cerveja que está no tanque quando ninguém abriu válvula nenhuma.
 *
 * <p><strong>A receita é declarada, não inferida.</strong> Herdar a receita da origem predominante faria
 * uma união de 60% de IPA com 40% de Stout se chamar "IPA" — e o rótulo imprimiria o ABV e o estilo dela
 * sobre uma cerveja que não é ela. Quem planeja diz o que o resultado é.
 *
 * @param seq      posição na operação, estável entre simulação e execução: é por ela que o lote criado se
 *                 liga de volta ao que foi planejado
 * @param recipeId a receita publicada que o resultado passa a ser
 * @param equipmentId o tanque que recebe o resultado — declarado junto com o volume, porque as duas
 *                    respostas descrevem o mesmo ato de quem planeja
 */
public record PlannedOutput(int seq, UUID recipeId, UUID equipmentId, BigDecimal liters) {

    public PlannedOutput {
        Objects.requireNonNull(recipeId, "recipeId");
        // Cerveja está sempre em algum lugar. Um resultado sem tanque existiria com volume e sem endereço,
        // e é o endereço que liga sensor e fermentação ao lote.
        Objects.requireNonNull(equipmentId, "equipmentId");
        Objects.requireNonNull(liters, "liters");
        if (seq < 1) {
            throw new IllegalArgumentException("a posição da saída começa em 1: " + seq);
        }
        if (liters.signum() <= 0) {
            // Saída de zero litro não é saída: seria um lote de resultado sem cerveja dentro.
            throw new IllegalArgumentException("volume precisa ser positivo: " + liters);
        }
    }
}
