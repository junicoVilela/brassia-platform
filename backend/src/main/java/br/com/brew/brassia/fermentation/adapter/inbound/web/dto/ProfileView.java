package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import java.util.List;
import java.util.UUID;

public record ProfileView(
        UUID id, String code, String name, int version, String status, List<StageDto> stages,
        StabilityDto stability) {

    public static ProfileView from(FermentationProfile p) {
        return new ProfileView(p.id().value(), p.code(), p.name(), p.version(), p.status().name(),
                p.stages().stream().map(StageDto::from).toList(),
                StabilityDto.from(p.stability()));
    }
}
