package br.com.brew.brassia.catalog.application.port.outbound;

import br.com.brew.brassia.catalog.domain.Ingredient;
import br.com.brew.brassia.catalog.domain.IngredientType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientRepository {
    boolean existsByCode(UUID breweryId, String code);

    void insert(Ingredient ingredient);

    /** Atualização com lock otimista: só afeta a linha se a versão bater. */
    boolean update(Ingredient ingredient, long expectedVersion);

    Optional<Ingredient> findById(UUID breweryId, UUID id);

    /**
     * Busca pelo nome normalizado — sem diferenciar maiúsculas nem espaços nas pontas (COM-003).
     *
     * <p>Exigir igualdade exata faria "Malte Pilsen" e "malte pilsen " serem coisas diferentes, e quem
     * copia uma receita ficaria criando duplicatas para casar com um espaço.
     */
    Optional<UUID> findIdByName(UUID breweryId, String name);

    /** Lista paginada da cervejaria; {@code type} nulo lista todos os tipos. */
    List<Ingredient> findPage(UUID breweryId, IngredientType type, int page, int size);

    long count(UUID breweryId, IngredientType type);
}
