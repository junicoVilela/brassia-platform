package br.com.brew.brassia.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeLookup {
    Optional<PublishedRecipe> findPublished(UUID breweryId, UUID recipeId);

    /**
     * Composição da receita publicada (itens + volume da batelada), para outros
     * módulos explodirem em necessidade de materiais (PLN-002) sem acessar a
     * tabela de receitas.
     */
    Optional<PublishedComposition> findPublishedComposition(UUID breweryId, UUID recipeId);

    /**
     * Dados da receita publicada para o snapshot de uma ordem de produção (BOP-001):
     * cabeçalho + equipamento + volume + métricas calculadas (quando existirem).
     * As métricas vêm vazias se ainda não foram calculadas ("snapshot incompleto").
     */
    Optional<PublishedForOrder> findPublishedForOrder(UUID breweryId, UUID recipeId);

    record PublishedRecipe(UUID id, int version, String name) {}

    /**
     * Quanto a receita admite perder por etapa (CST-002-A), em percentual.
     *
     * <p>Publicada para o custeio comparar a perda real com a esperada. Vazio quando a cervejaria ainda
     * não mediu a própria perda — e aí a variação volta a mostrar a perda como fato, sem desvio.
     */
    java.util.Optional<ExpectedLoss> expectedLoss(UUID breweryId, UUID recipeId);

    /** Percentuais; cada um pode ser nulo por si — quem mediu a transferência pode não ter medido o envase. */
    record ExpectedLoss(BigDecimal transferPercent, BigDecimal packagingPercent) {}

    record PublishedComposition(UUID id, int version, BigDecimal batchVolumeLiters, List<CompositionItem> items) {}

    record CompositionItem(UUID ingredientId, String stage, BigDecimal quantity, String unit) {}

    record PublishedForOrder(UUID id, int version, String name, UUID equipmentId, BigDecimal batchVolumeLiters,
            Optional<Metrics> metrics) {}

    record Metrics(BigDecimal ogSg, BigDecimal fgSg, BigDecimal abv, BigDecimal ibu, BigDecimal colorEbc) {}
}
