package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import java.util.List;
import java.util.UUID;

public interface ListProfilesUseCase {
    List<FermentationProfile> handle(UUID breweryId);
}
