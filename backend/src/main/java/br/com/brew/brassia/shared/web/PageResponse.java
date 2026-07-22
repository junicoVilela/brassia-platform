package br.com.brew.brassia.shared.web;

import java.util.List;

/** Envelope de paginação do contrato HTTP. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
