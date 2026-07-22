package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.PerformLoginUseCase;
import br.com.brew.brassia.security.application.port.inbound.RecordLoginAttemptUseCase;
import br.com.brew.brassia.shared.security.InvalidCredentialsException;
import java.util.Objects;

/**
 * Compõe limite de tentativas, autenticação e registro de histórico de login.
 *
 * <p>Não abre transação própria: cada colaborador persiste seu efeito de forma
 * independente. Assim, uma falha de credencial ainda registra a tentativa e
 * incrementa o bloqueio — comportamento de segurança de SEC-006/SEC-012 que
 * seria perdido se tudo compartilhasse uma transação com rollback no erro.
 */
public final class PerformLoginHandler implements PerformLoginUseCase {
    private final LoginThrottleService throttle;
    private final AuthenticateUserUseCase authenticate;
    private final RecordLoginAttemptUseCase loginHistory;

    public PerformLoginHandler(LoginThrottleService throttle, AuthenticateUserUseCase authenticate,
            RecordLoginAttemptUseCase loginHistory) {
        this.throttle = Objects.requireNonNull(throttle);
        this.authenticate = Objects.requireNonNull(authenticate);
        this.loginHistory = Objects.requireNonNull(loginHistory);
    }

    @Override
    public Result handle(Command command) {
        // Penalidade ativa interrompe antes de tocar nas credenciais (TooManyRequestsException -> 429).
        throttle.checkAllowed(command.email(), command.ip());

        AuthenticateUserUseCase.Result result;
        try {
            result = authenticate.handle(new AuthenticateUserUseCase.Command(command.email(), command.password()));
        } catch (IllegalArgumentException e) {
            throttle.recordFailure(command.email(), command.ip(), null, null);
            loginHistory.record(new RecordLoginAttemptUseCase.Command(
                    null, command.email(), RecordLoginAttemptUseCase.Outcome.FAILURE,
                    "INVALID_CREDENTIALS", command.ip(), command.userAgent(), command.traceId()));
            throw new InvalidCredentialsException("credenciais inválidas");
        }

        throttle.recordSuccess(command.email(), command.ip());
        loginHistory.record(new RecordLoginAttemptUseCase.Command(
                result.userId(), command.email(), RecordLoginAttemptUseCase.Outcome.SUCCESS,
                "OK", command.ip(), command.userAgent(), command.traceId()));

        return new Result(result.userId(), result.displayName(), result.mfaRequired());
    }
}
