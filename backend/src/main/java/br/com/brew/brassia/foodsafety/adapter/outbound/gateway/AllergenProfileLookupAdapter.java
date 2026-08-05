package br.com.brew.brassia.foodsafety.adapter.outbound.gateway;

import br.com.brew.brassia.foodsafety.AllergenProfileLookup;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenQueries;
import br.com.brew.brassia.foodsafety.application.port.outbound.AllergenRepository;
import br.com.brew.brassia.foodsafety.domain.AllergenCode;
import br.com.brew.brassia.foodsafety.domain.AllergenProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Publica o perfil do lote para fora do módulo (FDS-001), trocando o código do alergênico pelo nome
 * que a casa cadastrou — porque quem consome é o rótulo, e o que vai impresso na lata é o nome.
 */
@Component
class AllergenProfileLookupAdapter implements AllergenProfileLookup {

    private final AllergenQueries queries;
    private final AllergenRepository allergens;

    AllergenProfileLookupAdapter(AllergenQueries queries, AllergenRepository allergens) {
        this.queries = Objects.requireNonNull(queries);
        this.allergens = Objects.requireNonNull(allergens);
    }

    @Override
    public Profile ofBatch(UUID breweryId, UUID batchId) {
        var profile = queries.batchProfile(breweryId, batchId);
        return new Profile(named(breweryId, profile),
                profile.gaps().stream().map(AllergenProfile.Gap::label).toList());
    }

    private List<Profile.Allergen> named(UUID breweryId, AllergenProfile profile) {
        Map<AllergenCode, String> names = new LinkedHashMap<>();
        allergens.findAllergens(breweryId).forEach(allergen -> names.put(allergen.code(), allergen.name()));
        return profile.allergens().stream()
                .map(code -> new Profile.Allergen(code.value(), names.getOrDefault(code, code.value())))
                .toList();
    }
}
