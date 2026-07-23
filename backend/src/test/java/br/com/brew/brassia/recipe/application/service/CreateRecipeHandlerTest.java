package br.com.brew.brassia.recipe.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase.Command;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase.ItemInput;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateRecipeHandlerTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID EQUIPMENT = UUID.randomUUID();

    private final List<AuditEvent> recorded = new ArrayList<>();
    private final AuditTrail audit = recorded::add;
    private final InMemoryRecipeRepository repository = new InMemoryRecipeRepository();

    private CreateRecipeHandler handler(BigDecimal capacity) {
        EquipmentCapacityLookup lookup = (brewery, equipment) ->
                EQUIPMENT.equals(equipment) ? Optional.ofNullable(capacity) : Optional.empty();
        return new CreateRecipeHandler(repository, lookup, audit);
    }

    private Command command(BigDecimal batch) {
        return new Command(UUID.randomUUID(), BREWERY, "Hoppy Lager", EQUIPMENT, batch,
                null, null, null, null, 60,
                List.of(new ItemInput(UUID.randomUUID(), "MASH", new BigDecimal("5"), "KG", null, null),
                        new ItemInput(UUID.randomUUID(), "BOIL", new BigDecimal("30"), "G", 60, null)));
    }

    @Test
    void createsRecipeAndRecordsAudit() {
        var result = handler(new BigDecimal("500")).handle(command(new BigDecimal("400")));

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(recorded).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo("recipe.create");
            assertThat(e.breweryId()).isEqualTo(BREWERY);
            assertThat(e.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        });
    }

    @Test
    void rejectsUnknownEquipment() {
        EquipmentCapacityLookup empty = (b, e) -> Optional.empty();
        var handler = new CreateRecipeHandler(repository, empty, audit);
        assertThatThrownBy(() -> handler.handle(command(new BigDecimal("400"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("equipamento");
    }

    @Test
    void rejectsBatchAboveCapacity() {
        assertThatThrownBy(() -> handler(new BigDecimal("300")).handle(command(new BigDecimal("400"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capacidade");
    }

    private static final class InMemoryRecipeRepository implements RecipeRepository {
        private final List<Recipe> saved = new ArrayList<>();

        @Override public boolean existsByName(UUID breweryId, String normalizedName) { return false; }

        @Override public void insert(Recipe recipe) { saved.add(recipe); }

        @Override public boolean markPublished(UUID breweryId, UUID recipeId) { return true; }

        @Override public Optional<Recipe> findById(UUID breweryId, UUID id) { return Optional.empty(); }

        @Override public List<Recipe> findPage(UUID breweryId, int page, int size) { return List.copyOf(saved); }

        @Override public long count(UUID breweryId) { return saved.size(); }
    }
}
