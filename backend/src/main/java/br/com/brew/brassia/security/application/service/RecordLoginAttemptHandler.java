package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.inbound.RecordLoginAttemptUseCase;
import br.com.brew.brassia.security.application.port.outbound.LoginEventRepository;
import java.util.Objects;

public final class RecordLoginAttemptHandler implements RecordLoginAttemptUseCase {
    private final LoginEventRepository loginEvents;

    public RecordLoginAttemptHandler(LoginEventRepository loginEvents) {
        this.loginEvents = Objects.requireNonNull(loginEvents);
    }

    @Override
    public void record(Command command) {
        var outcome = command.outcome() == Outcome.SUCCESS
                ? LoginEventRepository.Outcome.SUCCESS
                : LoginEventRepository.Outcome.FAILURE;
        loginEvents.record(
                command.userId(),
                command.identifier(),
                outcome,
                command.reasonCode(),
                command.ip(),
                command.userAgent(),
                command.traceId());
    }
}
