package br.com.brew.brassia.brewery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API de leitura do módulo cervejaria para outros módulos (ex.: security resolve
 * o contexto de cervejaria da sessão sem acessar a tabela `brewery` diretamente).
 */
public interface BreweryDirectory {
    List<BreweryRef> findAll();
    Optional<BreweryRef> findById(UUID id);
}
