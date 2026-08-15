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
     * @param name              nome do ingrediente como a cervejaria o cadastrou. Entrou com a COM-001:
     *                          uma receita publicada sem os nomes dos ingredientes é inútil, e o
     *                          <strong>identificador não pode sair</strong> — ele é a chave do catálogo,
     *                          onde moram preço de compra e fornecedor
     * @param potentialSg       potencial de extrato do fermentável (ex.: 1.037)
     * @param colorEbc          cor do fermentável, em EBC
     * @param alphaAcidPercent  alfa-ácido do lúpulo, em %
     * @param attenuationPercent atenuação aparente da levedura, em %
     * @param volumeMl          volume nominal da embalagem, em ml
     * @param maxPressureBar    pressão máxima que a embalagem suporta, em bar (PKG-002-A). Vazio quando
     *                          a cervejaria ainda não cadastrou o limite — e aí o sistema não tem como
     *                          recusar um alvo alto em embalagem frágil, que é o que o débito dizia
     * @param useUnit           unidade de uso do ingrediente
     */
    record Spec(String name, String type, BigDecimal potentialSg, BigDecimal colorEbc, BigDecimal alphaAcidPercent,
            BigDecimal attenuationPercent, BigDecimal volumeMl, BigDecimal maxPressureBar,
            String useUnit) {}
}
