package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import java.util.UUID;

public interface GetProfileUseCase {
    FermentationProfile handle(UUID breweryId, UUID profileId);
}
