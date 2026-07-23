package br.com.brew.brassia.recipe.adapter.outbound.event;

import br.com.brew.brassia.recipe.RecipePublished;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringRecipeEventPublisher implements RecipeEventPublisher {
    private final ApplicationEventPublisher publisher;

    SpringRecipeEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(RecipePublished event) {
        publisher.publishEvent(event);
    }
}
