package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.brewery.BreweryRef;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Contexto de sessão resolvido: cervejaria ativa, cervejarias acessíveis e as
 * permissões efetivas na cervejaria ativa.
 */
public record SessionContext(UUID activeBreweryId, List<BreweryRef> accessibleBreweries, Set<String> permissions) {}
