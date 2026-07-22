package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import java.util.List;
import java.util.UUID;

public interface ManageSecurityAlertUseCase {
    List<SecurityAlertRepository.AlertView> list(UUID breweryId, String status);
    void updateStatus(UUID breweryId, UUID actorId, UUID alertId, String status);
}
