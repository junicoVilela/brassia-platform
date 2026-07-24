package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import java.math.BigDecimal;

/** Metas calculadas da receita para comparar com um estilo (todas opcionais). */
public record CompareStyleRequest(BigDecimal og, BigDecimal fg, BigDecimal abv, BigDecimal ibu,
        BigDecimal colorEbc) {}
