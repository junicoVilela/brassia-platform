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

    /**
     * Um lote que pode ser vendido deste produto (SAL-001-B).
     *
     * <p>Vendável é <strong>liberado pela qualidade</strong>, <strong>dentro da validade</strong> e
     * <strong>sem quarentena</strong> — decisão do mantenedor em 2026-08-15. Quem compõe as três
     * condições é o módulo de envase, que já tem as três ao alcance.
     *
     * <p><strong>{@code units} é o que o lote tem; {@code freeUnits} é o que sobrou depois das
     * reservas.</strong> Os dois viajam porque respondem perguntas diferentes: um lote de 780 unidades
     * com 780 vendidas continua existindo, e mostrar só o total faria a tela oferecer cerveja que já
     * tem dono. O desconto é feito <em>aqui</em>, e não no envase — a reserva é dado de vendas, e
     * pedir ao envase que a conhecesse fecharia ciclo entre os módulos.
     */
    public record SellableLotView(UUID finishedLotId, String code, String batchCode, int units,
            int freeUnits, BigDecimal containerVolumeMl, LocalDate packagedOn, LocalDate bestBefore) {}
}
