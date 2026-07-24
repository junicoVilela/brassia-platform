package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.CompareToStyleUseCase;
import br.com.brew.brassia.referencedata.domain.RangeCheck;
import java.util.List;

public record CompareStyleResponse(String styleCode, String styleName, List<RangeCheck> checks) {

    public static CompareStyleResponse from(CompareToStyleUseCase.Result r) {
        return new CompareStyleResponse(r.styleCode(), r.styleName(), r.checks());
    }
}
