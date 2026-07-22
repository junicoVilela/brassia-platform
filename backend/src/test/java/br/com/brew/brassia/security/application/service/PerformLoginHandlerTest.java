package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.PerformLoginUseCase;
import br.com.brew.brassia.security.application.port.inbound.RecordLoginAttemptUseCase;
import br.com.brew.brassia.security.application.port.outbound.LoginThrottleRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.shared.security.InvalidCredentialsException;
import br.com.brew.brassia.shared.security.TooManyRequestsException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerformLoginHandlerTest {

    private final FakeThrottleRepo throttleRepo = new FakeThrottleRepo();
    private final FakeAlertRepo alertRepo = new FakeAlertRepo();
    private final TokenHasher tokenHasher = raw -> raw; // identidade: facilita semear estado
    private final LoginThrottleService throttle = new LoginThrottleService(throttleRepo, alertRepo, tokenHasher);
    private final List<RecordLoginAttemptUseCase.Command> history = new ArrayList<>();
    private final RecordLoginAttemptUseCase loginHistory = history::add;

    private PerformLoginHandler handler(AuthenticateUserUseCase authenticate) {
        return new PerformLoginHandler(throttle, authenticate, loginHistory);
    }

    private PerformLoginUseCase.Command command() {
        return new PerformLoginUseCase.Command("brewer@example.com", "segredo1", "10.0.0.1", "JUnit", "trace-1");
    }

    @Test
    void authenticatesWithoutMfa() {
        var userId = UUID.randomUUID();
        var result = handler(cmd -> new AuthenticateUserUseCase.Result(userId, "Brewer", cmd.email(), false))
                .handle(command());

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.displayName()).isEqualTo("Brewer");
        assertThat(result.mfaRequired()).isFalse();
        assertThat(history).singleElement().satisfies(h -> {
            assertThat(h.outcome()).isEqualTo(RecordLoginAttemptUseCase.Outcome.SUCCESS);
            assertThat(h.userId()).isEqualTo(userId);
            assertThat(h.reasonCode()).isEqualTo("OK");
        });
        // Sucesso zera qualquer penalidade do e-mail.
        assertThat(throttleRepo.find("brewer@example.com", LoginThrottleRepository.SubjectType.EMAIL)).isEmpty();
    }

    @Test
    void authenticatesRequiringMfa() {
        var userId = UUID.randomUUID();
        var result = handler(cmd -> new AuthenticateUserUseCase.Result(userId, "Brewer", cmd.email(), true))
                .handle(command());

        assertThat(result.mfaRequired()).isTrue();
        assertThat(history).singleElement()
                .satisfies(h -> assertThat(h.outcome()).isEqualTo(RecordLoginAttemptUseCase.Outcome.SUCCESS));
    }

    @Test
    void rejectsInvalidCredentialsAndRecordsFailure() {
        var handler = handler(cmd -> {
            throw new IllegalArgumentException("credenciais inválidas");
        });

        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(history).singleElement().satisfies(h -> {
            assertThat(h.outcome()).isEqualTo(RecordLoginAttemptUseCase.Outcome.FAILURE);
            assertThat(h.userId()).isNull(); // não vaza existência da conta
            assertThat(h.reasonCode()).isEqualTo("INVALID_CREDENTIALS");
        });
        // Falha incrementa o contador de tentativas do e-mail.
        assertThat(throttleRepo.find("brewer@example.com", LoginThrottleRepository.SubjectType.EMAIL))
                .get().extracting(LoginThrottleRepository.ThrottleState::failureCount).isEqualTo(1);
    }

    @Test
    void blocksWhenThrottlePenaltyActiveWithoutTouchingCredentials() {
        throttleRepo.store.put(key("brewer@example.com", LoginThrottleRepository.SubjectType.EMAIL),
                new LoginThrottleRepository.ThrottleState(5, Instant.now().plusSeconds(60)));
        var authenticateCalled = new boolean[]{false};
        var handler = handler(cmd -> {
            authenticateCalled[0] = true;
            return new AuthenticateUserUseCase.Result(UUID.randomUUID(), "Brewer", cmd.email(), false);
        });

        assertThatThrownBy(() -> handler.handle(command()))
                .isInstanceOf(TooManyRequestsException.class);

        assertThat(authenticateCalled[0]).as("não deve autenticar sob penalidade ativa").isFalse();
        assertThat(history).isEmpty();
    }

    private static String key(String hash, LoginThrottleRepository.SubjectType type) {
        return type + ":" + hash;
    }

    private static final class FakeThrottleRepo implements LoginThrottleRepository {
        final Map<String, ThrottleState> store = new HashMap<>();

        @Override
        public Optional<ThrottleState> find(String subjectHash, SubjectType type) {
            return Optional.ofNullable(store.get(key(subjectHash, type)));
        }

        @Override
        public void recordFailure(String subjectHash, SubjectType type, int failureCount, Instant penaltyUntil) {
            store.put(key(subjectHash, type), new ThrottleState(failureCount, penaltyUntil));
        }

        @Override
        public void reset(String subjectHash, SubjectType type) {
            store.remove(key(subjectHash, type));
        }
    }

    private static final class FakeAlertRepo implements SecurityAlertRepository {
        @Override
        public UUID create(UUID breweryId, UUID userId, String alertType, String severity, Map<String, Object> evidence) {
            return UUID.randomUUID();
        }

        @Override
        public List<AlertView> listByBrewery(UUID breweryId, String status, int limit) {
            return List.of();
        }

        @Override
        public Optional<AlertView> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public void updateStatus(UUID id, String status, UUID resolvedBy) {
        }
    }
}
