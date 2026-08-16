package br.com.brew.brassia.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * Encontra um ingrediente do catálogo pelo nome (COM-003).
 *
 * <p><strong>Existe porque o retrato público de uma receita traz nomes, e não identificadores</strong> —
 * o id é a chave do catálogo do autor e nunca sai. Para uma receita copiada virar receita aqui, cada nome
 * precisa achar um ingrediente <em>desta</em> casa.
 *
 * <p>A comparação é <strong>por nome normalizado</strong> — sem diferenciar maiúsculas nem espaços nas
 * pontas. Exigir igualdade exata faria "Malte Pilsen" e "malte pilsen " serem coisas diferentes, e o
 * forkador ficaria criando ingredientes duplicados para casar com um espaço.
 *
 * <p>Vazio quando não há: quem chama decide o que fazer, e essa decisão não é do catálogo.
 */
public interface IngredientDirectory {

    Optional<UUID> findIdByName(UUID breweryId, String name);
}
