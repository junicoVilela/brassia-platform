package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.inbound.ListUsersUseCase;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.SecurityUser;
import java.util.Objects;

/** Consulta paginada de contas internas para a visão administrativa. */
public final class ListUsersHandler implements ListUsersUseCase {
    private static final int MAX_SIZE = 100;

    private final SecurityUserRepository users;

    public ListUsersHandler(SecurityUserRepository users) {
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public Result handle(Query query) {
        var page = Math.max(0, query.page());
        var size = Math.clamp(query.size(), 1, MAX_SIZE);

        var content = users.findPage(page, size).stream().map(ListUsersHandler::toSummary).toList();
        var total = users.count();
        var totalPages = (int) Math.ceilDiv(total, size);

        return new Result(content, page, size, total, totalPages);
    }

    private static Summary toSummary(SecurityUser user) {
        return new Summary(
                user.id().value(),
                user.email().value(),
                user.displayName().value(),
                user.status().name(),
                user.emailVerifiedAt());
    }
}
