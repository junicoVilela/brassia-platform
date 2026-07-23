package br.com.brew.brassia.recipe.application.port.outbound;

import br.com.brew.brassia.recipe.RecipePublished;

/** Publica eventos de domínio da receita para outros módulos (ex.: publicação). */
public interface RecipeEventPublisher {
    void publish(RecipePublished event);
}
