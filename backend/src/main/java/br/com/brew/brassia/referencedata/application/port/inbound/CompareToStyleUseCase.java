package br.com.brew.brassia.referencedata.application.port.inbound;

import br.com.brew.brassia.referencedata.domain.RangeCheck;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Compara as metas calculadas de uma receita com as faixas de um estilo. Fora da
 * faixa é aviso explicável — nunca bloqueia a receita.
 */
public interface CompareToStyleUseCase {

    Result handle(Query query);

    record Query(UUID breweryId, UUID styleSetId, String styleCode, BigDecimal og, BigDecimal fg, BigDecimal abv,
            BigDecimal ibu, BigDecimal colorEbc) {}

    record Result(String styleCode, String styleName, List<RangeCheck> checks) {}
}
