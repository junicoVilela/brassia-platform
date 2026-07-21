package br.com.brew.brassia.brewery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase.Command;
import br.com.brew.brassia.brewery.application.port.outbound.BreweryRepository;
import br.com.brew.brassia.brewery.domain.Brewery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterBreweryHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeBreweries repository = new FakeBreweries();

    @Test
    void registersAndAudits() {
        var actorId = UUID.randomUUID();

        var result = new RegisterBreweryHandler(repository, audit)
                .handle(new Command(actorId, "sb40", "Casa Brew", "America/Sao_Paulo"));

        assertThat(result.code()).isEqualTo("SB40");
        assertThat(repository.saved).hasSize(1);
        assertThat(audited).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo("brewery.register");
            assertThat(e.resourceType()).isEqualTo("brewery");
            assertThat(e.actorId()).isEqualTo(actorId);
            assertThat(e.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        });
    }

    @Test
    void rejectsDuplicateCodeWithoutPersisting() {
        repository.existing.add("SB40");

        assertThatThrownBy(() -> new RegisterBreweryHandler(repository, audit)
                .handle(new Command(UUID.randomUUID(), "sb40", "Casa Brew", "America/Sao_Paulo")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.saved).isEmpty();
        assertThat(audited).isEmpty();
    }

    private static final class FakeBreweries implements BreweryRepository {
        final List<Brewery> saved = new ArrayList<>();
        final List<String> existing = new ArrayList<>();

        @Override public boolean existsByCode(String code) { return existing.contains(code); }
        @Override public List<Brewery> findPage(int page, int size) { return List.copyOf(saved); }
        @Override public long count() { return saved.size(); }
        @Override public void save(Brewery brewery) { saved.add(brewery); }
    }
}
