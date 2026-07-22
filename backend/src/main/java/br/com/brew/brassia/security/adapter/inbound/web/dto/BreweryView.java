package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.brewery.BreweryRef;
import java.util.UUID;

public record BreweryView(UUID id, String code, String name) {
    public static BreweryView from(BreweryRef ref) {
        return new BreweryView(ref.id(), ref.code(), ref.name());
    }
}
