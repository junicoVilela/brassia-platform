package br.com.brew.brassia.referencedata.application.port.inbound;

import java.util.List;
import java.util.UUID;

public interface ListReferenceSourcesUseCase {

    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record SourceView(UUID id, boolean global, String type, String name, String owner, String url,
            String licenseName, String permissionStatus, String attribution) {}

    record Result(List<SourceView> content, long total) {}
}
