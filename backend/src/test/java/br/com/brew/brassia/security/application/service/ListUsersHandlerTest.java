package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.security.application.port.inbound.ListUsersUseCase.Query;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ListUsersHandlerTest {

    @Test
    void mapsSummariesAndComputesPagination() {
        var repo = new FakeUsers(
                List.of(active("a@x.com", "Ana"), active("b@x.com", "Bruno")), 5);
        var handler = new ListUsersHandler(repo);

        var result = handler.handle(new Query(0, 2));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).email()).isEqualTo("a@x.com");
        assertThat(result.content().get(0).status()).isEqualTo("ACTIVE");
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3); // ceil(5/2)
    }

    @Test
    void clampsSizeAndNegativePage() {
        var repo = new FakeUsers(List.of(), 0);
        var handler = new ListUsersHandler(repo);

        var result = handler.handle(new Query(-3, 0));

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(1); // size mínimo 1
        assertThat(result.totalPages()).isZero();
    }

    private static SecurityUser active(String email, String name) {
        return SecurityUser.reconstitute(UserId.newId(), new EmailAddress(email),
                new DisplayName(name), AccountStatus.ACTIVE, Instant.now(), 1);
    }

    private record FakeUsers(List<SecurityUser> page, long total) implements SecurityUserRepository {
        @Override public boolean existsByNormalizedEmail(String normalizedEmail) { return false; }
        @Override public Optional<SecurityUser> findById(UserId id) { return Optional.empty(); }
        @Override public List<SecurityUser> findPage(int page, int size) { return this.page; }
        @Override public long count() { return total; }
        @Override public void save(SecurityUser user) { }
    }
}
