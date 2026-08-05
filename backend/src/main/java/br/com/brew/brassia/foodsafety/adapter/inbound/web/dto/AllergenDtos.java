package br.com.brew.brassia.foodsafety.adapter.inbound.web.dto;

import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenQueries;
import br.com.brew.brassia.foodsafety.domain.Allergen;
import br.com.brew.brassia.foodsafety.domain.AllergenCode;
import br.com.brew.brassia.foodsafety.domain.AllergenProfile;
import br.com.brew.brassia.foodsafety.domain.ChangeoverVerdict;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contrato da matriz de alergênicos (FDS-001). */
public final class AllergenDtos {

    private AllergenDtos() {
    }

    public record RegisterAllergenRequest(@NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name) {}

    /** Declarar é responder por inteiro: a lista enviada passa a ser a vigente. */
    public record DeclareRequest(List<String> allergens) {}

    public record AllergenView(UUID id, String code, String name) {

        public static AllergenView from(Allergen allergen) {
            return new AllergenView(allergen.id(), allergen.code().value(), allergen.name());
        }

        public static List<AllergenView> from(List<Allergen> allergens) {
            return allergens.stream().map(AllergenView::from).toList();
        }
    }

    /**
     * O perfil sai sempre com {@code complete}: é ele que distingue "declarado isento" de "ninguém
     * declarou", e a tela precisa mostrar coisas diferentes nos dois casos.
     */
    public record ProfileView(List<String> allergens, boolean complete, List<GapView> gaps) {

        public static ProfileView from(AllergenProfile profile) {
            return new ProfileView(codes(profile.allergens()), profile.complete(),
                    profile.gaps().stream().map(GapView::from).toList());
        }
    }

    public record GapView(UUID ingredientId, String label) {

        public static GapView from(AllergenProfile.Gap gap) {
            return new GapView(gap.ingredientId(), gap.label());
        }
    }

    public record ChangeoverView(boolean allowed, String outcome, String code, String detail,
            List<String> allergens, List<GapView> gaps) {

        public static ChangeoverView from(ChangeoverVerdict verdict) {
            return new ChangeoverView(verdict.allowed(), verdict.outcome().name(), verdict.code(),
                    verdict.detail(), codes(verdict.allergens()),
                    verdict.gaps().stream().map(GapView::from).toList());
        }
    }

    public record MatrixView(List<AllergenView> allergens, List<IngredientView> ingredients,
            List<EquipmentView> dedications, List<ProcedureView> procedures) {

        public static MatrixView from(AllergenQueries.Matrix matrix) {
            return new MatrixView(AllergenView.from(matrix.allergens()),
                    matrix.ingredients().stream().map(IngredientView::from).toList(),
                    matrix.dedications().stream().map(EquipmentView::from).toList(),
                    matrix.procedures().stream().map(ProcedureView::from).toList());
        }
    }

    public record IngredientView(UUID ingredientId, String code, String name, boolean declared,
            List<String> allergens) {

        public static IngredientView from(AllergenQueries.IngredientRow row) {
            return new IngredientView(row.ingredientId(), row.code(), row.name(), row.declared(),
                    codes(row.allergens()));
        }
    }

    public record EquipmentView(UUID equipmentId, List<String> allergens) {

        public static EquipmentView from(AllergenQueries.EquipmentRow row) {
            return new EquipmentView(row.equipmentId(), codes(row.allergens()));
        }
    }

    public record ProcedureView(String procedureCode, List<String> allergens) {

        public static ProcedureView from(AllergenQueries.ProcedureRow row) {
            return new ProcedureView(row.procedureCode(), codes(row.allergens()));
        }
    }

    private static List<String> codes(Set<AllergenCode> codes) {
        return codes.stream().map(AllergenCode::value).toList();
    }
}
