package br.com.brew.brassia.community.config;

import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.community.application.port.inbound.LibraryCommands;
import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.application.service.LibraryHandlers;
import br.com.brew.brassia.recipe.RecipeLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CommunityConfiguration {

    @Bean
    LibraryCommands libraryCommands(PublishedRecipeRepository library, RecipeLookup recipes,
            IngredientSpecLookup ingredients) {
        return new LibraryHandlers(library, recipes, ingredients);
    }
}
