package br.com.brew.brassia.fermentation.adapter.inbound.web;

import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.CreateProfileRequest;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ProfileView;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.StageDto;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.UpdateProfileRequest;
import br.com.brew.brassia.fermentation.application.port.inbound.CreateProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.GetProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.ListProfilesUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.PublishProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.UpdateProfileUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfis de fermentação versionados (FER-001). */
@RestController
@RequestMapping("/api/v1/fermentation/profiles")
final class ProfileController {

    private final CreateProfileUseCase createProfile;
    private final UpdateProfileUseCase updateProfile;
    private final PublishProfileUseCase publishProfile;
    private final ListProfilesUseCase listProfiles;
    private final GetProfileUseCase getProfile;

    ProfileController(CreateProfileUseCase createProfile, UpdateProfileUseCase updateProfile,
            PublishProfileUseCase publishProfile, ListProfilesUseCase listProfiles, GetProfileUseCase getProfile) {
        this.createProfile = createProfile;
        this.updateProfile = updateProfile;
        this.publishProfile = publishProfile;
        this.listProfiles = listProfiles;
        this.getProfile = getProfile;
    }

    @GetMapping
    List<ProfileView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.profile.read");
        return listProfiles.handle(principal.requireBrewery()).stream().map(ProfileView::from).toList();
    }

    @GetMapping("/{id}")
    ProfileView get(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.profile.read");
        return ProfileView.from(getProfile.handle(principal.requireBrewery(), id));
    }

    @PostMapping
    ResponseEntity<Created> create(@Valid @RequestBody CreateProfileRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.profile.manage");
        var result = createProfile.handle(new CreateProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.name(),
                request.stages().stream().map(StageDto::toInput).toList(),
                request.stability() == null ? null : request.stability().toInput()));
        return ResponseEntity.created(URI.create("/api/v1/fermentation/profiles/" + result.id()))
                .body(new Created(result.id(), result.version()));
    }

    record Created(UUID id, int version) {}

    @PutMapping("/{id}")
    void update(@PathVariable UUID id, @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.profile.manage");
        updateProfile.handle(new UpdateProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(),
                request.stages().stream().map(StageDto::toInput).toList(),
                request.stability() == null ? null : request.stability().toInput()));
    }

    @PostMapping("/{id}/publish")
    void publish(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.profile.manage");
        publishProfile.handle(new PublishProfileUseCase.Command(
                principal.userId(), principal.requireBrewery(), id));
    }
}
