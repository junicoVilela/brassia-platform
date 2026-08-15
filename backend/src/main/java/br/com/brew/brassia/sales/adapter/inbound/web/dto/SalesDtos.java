package br.com.brew.brassia.sales.adapter.inbound.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos de entrada e saída da SAL-001. */
public final class SalesDtos {

    private SalesDtos() {
    }

    public record CreateProductRequest(@NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 160) String name, @NotNull UUID recipeId, @NotNull UUID containerId) {}

    public record RenameRequest(@NotBlank @Size(max = 160) String name) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record ProductView(UUID id, String sku, String name, UUID recipeId, UUID containerId,
            boolean active) {}

    public record CreateChannelRequest(@NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 120) String name) {}

    public record ChannelView(UUID id, String code, String name, boolean active) {}

    /**
     * "A partir deste dia, passa a custar isto."
     *
     * <p>A data vem de fora e não é carimbada pelo servidor: um preço combinado para valer no primeiro
     * dia do mês que vem precisa poder ser cadastrado hoje, e um que já valia desde a semana passada
     * precisa poder ser regularizado.
     */
    public record PriceFromRequest(@NotNull UUID channelId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull Boolean taxIncluded, @NotNull LocalDate validFrom) {}

    /** A linha do tempo inteira, porque é ela que explica o preço de um pedido antigo. */
    public record PriceScheduleView(UUID productId, UUID channelId, List<PriceEntryView> entries) {}

    public record PriceEntryView(BigDecimal amount, String currency, boolean taxIncluded,
            LocalDate validFrom, LocalDate validTo) {}
}
