package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;

/** Parecer humano sobre a coleta (YST-001); reprovação exige motivo (validado no domínio). */
public record ReviewYeastRequest(@NotNull Boolean approve, String note) {}
