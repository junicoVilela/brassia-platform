package br.com.brew.brassia.water.application.port.inbound;

import br.com.brew.brassia.water.domain.ChargeBalance;
import br.com.brew.brassia.water.domain.IonProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ListWaterReferenceProfilesUseCase {

    Result handle(Query query);

    record Query(UUID breweryId, int page, int size) {}

    record ProfileView(UUID id, boolean global, String name, String region, String edition, IonProfile ions,
            BigDecimal alkalinity, BigDecimal hardness, BigDecimal ph, String status, String sourceName,
            ChargeBalance chargeBalance) {}

    record Result(List<ProfileView> content, long total) {}
}
