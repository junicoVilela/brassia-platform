package br.com.brew.brassia.catalog.application.service;

import br.com.brew.brassia.catalog.application.port.inbound.RankSubstitutionsUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.IngredientRepository;
import br.com.brew.brassia.catalog.application.port.outbound.TechnicalProfileRepository;
import br.com.brew.brassia.catalog.domain.Ingredient;
import br.com.brew.brassia.catalog.domain.SubstitutionMatch;
import br.com.brew.brassia.catalog.domain.SubstitutionRanker;
import br.com.brew.brassia.catalog.domain.SubstitutionRanker.Candidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RankSubstitutionsHandler implements RankSubstitutionsUseCase {

    /** Teto de candidatos do mesmo tipo a avaliar; o ranking devolve apenas os melhores. */
    private static final int CANDIDATE_POOL = 200;

    private final IngredientRepository ingredients;
    private final TechnicalProfileRepository profiles;
    private final SubstitutionRanker ranker;

    public RankSubstitutionsHandler(IngredientRepository ingredients, TechnicalProfileRepository profiles) {
        this.ingredients = Objects.requireNonNull(ingredients);
        this.profiles = Objects.requireNonNull(profiles);
        this.ranker = new SubstitutionRanker();
    }

    @Override
    public Optional<Result> handle(Query query) {
        var target = ingredients.findById(query.breweryId(), query.ingredientId());
        if (target.isEmpty()) {
            return Optional.empty();
        }
        Ingredient targetIngredient = target.get();
        var targetProfile = profiles.findByIngredient(query.breweryId(), query.ingredientId());
        if (targetProfile.isEmpty()) {
            return Optional.of(new Result(targetIngredient.id().value(), targetIngredient.code().value(),
                    targetIngredient.name().value(), false, List.of()));
        }

        var candidates = new ArrayList<Candidate>();
        for (Ingredient other : ingredients.findPage(query.breweryId(), targetIngredient.type(), 0, CANDIDATE_POOL)) {
            if (other.id().value().equals(query.ingredientId())) {
                continue;
            }
            profiles.findByIngredient(query.breweryId(), other.id().value())
                    .ifPresent(p -> candidates.add(new Candidate(other.id().value(), other.code().value(),
                            other.name().value(), p.sourceName(), p.ranges())));
        }

        List<SubstitutionMatch> ranked = ranker.rank(targetProfile.get().ranges(), candidates);
        var matches = ranked.stream().limit(Math.max(1, query.limit())).map(RankSubstitutionsHandler::toMatch).toList();
        return Optional.of(new Result(targetIngredient.id().value(), targetIngredient.code().value(),
                targetIngredient.name().value(), true, matches));
    }

    private static Match toMatch(SubstitutionMatch m) {
        var comparisons = m.comparisons().stream()
                .map(c -> new Comparison(c.property(), c.target(), c.candidate(), c.unit(), c.similar()))
                .toList();
        return new Match(m.ingredientId(), m.code(), m.name(), m.sourceName(), m.score(), m.confidence(), comparisons);
    }
}
