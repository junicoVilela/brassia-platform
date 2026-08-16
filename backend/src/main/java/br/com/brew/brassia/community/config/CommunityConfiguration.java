package br.com.brew.brassia.community.config;

import br.com.brew.brassia.catalog.IngredientDirectory;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.community.application.port.inbound.LibraryCommands;
import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.application.port.outbound.ContributionRepository;
import br.com.brew.brassia.community.application.port.outbound.RatingRepository;
import br.com.brew.brassia.community.application.port.outbound.RecipeForkRepository;
import br.com.brew.brassia.community.application.port.outbound.ShareLinkRepository;
import br.com.brew.brassia.community.application.service.LibraryHandlers;
import br.com.brew.brassia.community.application.service.ContributionHandlers;
import br.com.brew.brassia.community.application.service.ForkHandlers;
import br.com.brew.brassia.community.application.service.RatingHandlers;
import br.com.brew.brassia.community.application.service.ShareLinkHandlers;
import br.com.brew.brassia.recipe.RecipeImportCommands;
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

    @Bean
    ContributionHandlers contributionHandlers(ContributionRepository contributions,
            PublishedRecipeRepository library) {
        return new ContributionHandlers(contributions, library);
    }

    @Bean
    RatingHandlers ratingHandlers(RatingRepository ratings, PublishedRecipeRepository library) {
        return new RatingHandlers(ratings, library);
    }

    @Bean
    ForkHandlers forkHandlers(PublishedRecipeRepository library, RecipeForkRepository forks,
            RecipeImportCommands recipes, IngredientDirectory ingredients) {
        return new ForkHandlers(library, forks, recipes, ingredients);
    }

    @Bean
    ShareLinkHandlers shareLinkHandlers(ShareLinkRepository links, PublishedRecipeRepository library) {
        return new ShareLinkHandlers(links, library);
    }
}
