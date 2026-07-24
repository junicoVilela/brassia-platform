package br.com.brew.brassia.water.adapter.inbound.web.dto;

import br.com.brew.brassia.water.domain.IonProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateWaterReferenceProfileRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) String region,
        @NotBlank @Size(max = 40) String edition,
        @NotNull BigDecimal calcium,
        @NotNull BigDecimal magnesium,
        @NotNull BigDecimal sodium,
        @NotNull BigDecimal sulfate,
        @NotNull BigDecimal chloride,
        @NotNull BigDecimal bicarbonate,
        BigDecimal alkalinity,
        BigDecimal hardness,
        BigDecimal ph,
        UUID sourceId,
        @Size(max = 200) String sourceName) {

    public IonProfile toIons() {
        return new IonProfile(calcium, magnesium, sodium, sulfate, chloride, bicarbonate);
    }
}
