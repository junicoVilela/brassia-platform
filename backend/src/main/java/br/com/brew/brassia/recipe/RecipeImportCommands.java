package br.com.brew.brassia.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cria uma receita a partir de conteúdo que veio de fora (COM-003).
 *
 * <p><strong>Porta de escrita publicada, na direção padrão do ADR-0016:</strong> quem tem o dado —
 * receita — declara a porta, e quem precisa do efeito depende dela. A comunidade não sabe montar uma
 * receita; ela sabe que quer uma, a partir do que copiou.
 *
 * <p><strong>Ela recebe identificadores já resolvidos, e não nomes.</strong> Traduzir "Malte Pilsen" no
 * ingrediente da casa é decisão de quem forka, e envolve dizer o que fazer quando não existe — recusar,
 * criar, ignorar. Deixar essa escolha entrar aqui faria a receita ganhar uma regra de comunidade que ela
 * não deveria conhecer.
 *
 * <p><strong>A receita nasce em rascunho.</strong> Uma cópia que já nascesse publicada colocaria no
 * catálogo interno, pronta para brassar, algo que ninguém da casa revisou.
 */
public interface RecipeImportCommands {

    UUID importRecipe(UUID breweryId, UUID actorId, ImportedRecipe recipe);

    /**
     * @param equipmentId equipamento da casa em que a receita será feita — o do autor nunca sai no
     *                    retrato público, então quem copia escolhe o seu
     */
    record ImportedRecipe(String name, UUID equipmentId, BigDecimal batchVolumeLiters,
            Integer boilTimeMinutes, List<ImportedItem> items) {}

    record ImportedItem(UUID ingredientId, String stage, BigDecimal quantity, String unit,
            Integer timingMinutes) {}
}
