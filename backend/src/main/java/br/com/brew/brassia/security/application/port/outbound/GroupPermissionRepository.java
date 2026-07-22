package br.com.brew.brassia.security.application.port.outbound;

import java.util.Set;
import java.util.UUID;

public interface GroupPermissionRepository {
    Set<String> findPermissionCodesByGroupId(UUID groupId);
}
