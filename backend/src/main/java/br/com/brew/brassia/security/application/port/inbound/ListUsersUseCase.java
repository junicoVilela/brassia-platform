package br.com.brew.brassia.security.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ListUsersUseCase {
    Result handle(Query query);

    record Query(int page, int size) {}

    record Summary(UUID id, String email, String displayName, String status, Instant emailVerifiedAt) {}

    record Result(List<Summary> content, int page, int size, long totalElements, int totalPages) {}
}
