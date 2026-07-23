package br.com.brew.brassia.recipe.adapter.inbound.web.exchange;

import java.util.List;

/** Documento importado + os campos que não reconhecemos (relatório de compatibilidade). */
public record ParsedDocument(RecipeDocument document, List<String> unknownFields) {}
