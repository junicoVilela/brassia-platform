package br.com.brew.brassia.foodsafety.application.port.inbound;

import br.com.brew.brassia.foodsafety.domain.Allergen;
import java.util.Set;
import java.util.UUID;

/**
 * Comandos da matriz de alergênicos (FDS-001): cadastrar o vocabulário e declarar os três eixos —
 * ingrediente, equipamento e POP.
 *
 * <p>Todo declarar é uma resposta completa, não um acréscimo: o conjunto enviado passa a ser o
 * conjunto vigente. Acrescentar item a item deixaria "tirei o glúten desta receita" sem comando.
 */
public interface AllergenCommands {

    interface RegisterAllergen {
        Allergen handle(UUID actorId, UUID breweryId, String code, String name);
    }

    interface DeclareIngredient {
        void handle(UUID actorId, UUID breweryId, UUID ingredientId, Set<String> allergenCodes);
    }

    interface DeclareDedication {
        /** Conjunto vazio é a dedicação "livre de alergênicos"; {@code null} devolve ao compartilhado. */
        void handle(UUID actorId, UUID breweryId, UUID equipmentId, Set<String> allergenCodes);
    }

    interface DeclareProcedureEffectiveness {
        void handle(UUID actorId, UUID breweryId, String procedureCode, Set<String> allergenCodes);
    }
}
