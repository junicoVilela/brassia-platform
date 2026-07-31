package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Uso de levedura exige lote de destino e confirmação explícita (YST-002). */
public record UseYeastRequest(@NotNull UUID targetBatchId, @NotNull Boolean confirmed) {}
