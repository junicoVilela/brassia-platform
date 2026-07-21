package br.com.brew.brassia.recipe.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase.Command;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateRecipeHandlerTest {

    @Test
    void recordsSuccessAuditWhenRecipeIsCreated() {
        var recorded = new ArrayList<AuditEvent>();
        AuditTrail audit = recorded::add;
        var repository = new InMemoryRecipeRepository();
        var handler = new CreateRecipeHandler(repository, audit);
        var breweryId = UUID.randomUUID();

        var result = handler.handle(new Command(breweryId, "Hoppy Lager"));

        assertThat(recorded).hasSize(1);
        var event = recorded.get(0);
        assertThat(event.action()).isEqualTo("recipe.create");
        assertThat(event.resourceType()).isEqualTo("recipe");
        assertThat(event.resourceId()).isEqualTo(result.id().toString());
        assertThat(event.breweryId()).isEqualTo(breweryId);
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    private static final class InMemoryRecipeRepository implements RecipeRepository {
        private final List<Recipe> saved = new ArrayList<>();

        @Override
        public boolean existsByName(UUID breweryId, String normalizedName) {
            return false;
        }

        @Override
        public void save(Recipe recipe) {
            saved.add(recipe);
        }
    }
}
