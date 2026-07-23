package br.com.brew.brassia.catalog;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada da especificação de um ingrediente do catálogo, para outros
 * módulos calcularem (ex.: metas cervejeiras). Os atributos específicos por tipo
 * são expostos como campos tipados opcionais.
 */
public interface IngredientSpecLookup {
    Optional<Spec> find(UUID breweryId, UUID ingredientId);

    /**
     * @param potentialSg       potencial de extrato do fermentável (ex.: 1.037)
     * @param colorEbc          cor do fermentável, em EBC
     * @param alphaAcidPercent  alfa-ácido do lúpulo, em %
     * @param attenuationPercent atenuação aparente da levedura, em %
     */
    record Spec(String type, BigDecimal potentialSg, BigDecimal colorEbc, BigDecimal alphaAcidPercent,
            BigDecimal attenuationPercent) {}
}
