package br.com.brew.brassia.brewery.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cópia imutável das preferências em uma versão específica. Consumidores de
 * produção/estoque futuros devem persistir este snapshot, nunca reler a linha atual.
 */
public record OperationalPreferencesSnapshot(
        UUID breweryId,
        long preferenceVersion,
        String volumeUnit,
        String massUnit,
        String temperatureUnit,
        String currencyCode,
        BigDecimal maxBatchVolume,
        boolean allowNegativeStock,
        String stockPolicy) {}
