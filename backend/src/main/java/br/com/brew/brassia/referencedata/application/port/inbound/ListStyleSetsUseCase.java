package br.com.brew.brassia.referencedata.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListStyleSetsUseCase {

    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record SetView(UUID id, boolean global, String authority, String edition, String language, String permissionStatus,
            String status, Instant publishedAt) {}

    record Result(List<SetView> content, long total) {}
}
