package br.com.brew.brassia.brewery;

import java.util.UUID;

/** Referência leve de cervejaria exposta a outros módulos (ex.: contexto de sessão). */
public record BreweryRef(UUID id, String code, String name) {}
