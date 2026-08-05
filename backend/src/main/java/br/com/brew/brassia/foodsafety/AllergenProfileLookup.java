package br.com.brew.brassia.foodsafety;

import java.util.List;
import java.util.UUID;

/**
 * Consulta publicada do perfil de alergênicos de um lote (FDS-001), para que o rótulo (PKG-004)
 * tenha uma fonte rastreável para o campo de alergênicos — o que fecha o débito PKG-004-A.
 *
 * <p><strong>O perfil incompleto se declara.</strong> O rótulo precisa distinguir "declarado isento"
 * de "ninguém declarou": o primeiro é uma frase impressa na lata, o segundo é motivo para não
 * imprimir. Por isso a lacuna vem no resultado e não como lista vazia.
 */
public interface AllergenProfileLookup {

    /** Perfil derivado da composição da receita publicada do lote. Lote inexistente devolve vazio. */
    Profile ofBatch(UUID breweryId, UUID batchId);

    /**
     * @param allergens             alergênicos declarados, com código e nome, em ordem estável
     * @param undeclaredIngredients ingredientes da composição sobre os quais ninguém respondeu
     */
    record Profile(List<Allergen> allergens, List<String> undeclaredIngredients) {

        public Profile {
            allergens = List.copyOf(allergens);
            undeclaredIngredients = List.copyOf(undeclaredIngredients);
        }

        /** Só um perfil completo autoriza afirmar o que a cerveja contém — ou não contém. */
        public boolean complete() {
            return undeclaredIngredients.isEmpty();
        }

        public record Allergen(String code, String name) {}
    }
}
