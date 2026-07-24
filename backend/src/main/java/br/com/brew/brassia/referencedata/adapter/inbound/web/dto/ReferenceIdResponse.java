package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.RegisterReferenceSourceUseCase;
import java.util.UUID;

public record ReferenceIdResponse(UUID id) {

    public static ReferenceIdResponse from(RegisterReferenceSourceUseCase.Result result) {
        return new ReferenceIdResponse(result.id());
    }
}
