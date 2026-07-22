package br.com.brew.brassia.brewery.adapter.inbound.web.dto;

import br.com.brew.brassia.brewery.application.port.inbound.ListBreweriesUseCase;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase;
import java.util.UUID;

public record BreweryResponse(UUID id, String code, String name, String timezone) {

    public static BreweryResponse from(ListBreweriesUseCase.Summary s) {
        return new BreweryResponse(s.id(), s.code(), s.name(), s.timezone());
    }

    public static BreweryResponse from(RegisterBreweryUseCase.Result r) {
        return new BreweryResponse(r.id(), r.code(), r.name(), r.timezone());
    }
}
