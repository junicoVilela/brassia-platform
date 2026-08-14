package br.com.brew.brassia.packaging.application.port.inbound;

import br.com.brew.brassia.packaging.domain.Carbonation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cálculo e decisão de carbonatação do plano de envase (PKG-002). */
public final class CarbonationCommands {

    private CarbonationCommands() {
    }

    /**
     * Prévia: calcula e explica, sem gravar nada. É recomendação — o cervejeiro precisa ver
     * entradas, método e alertas antes de decidir.
     */
    public interface Preview {
        Recommendation handle(Query query);

        record Query(UUID breweryId, UUID planId, String method, BigDecimal targetVolumes,
                BigDecimal referenceTempC, String primingSugar) {}
    }

    /**
     * Grava a decisão. A confirmação humana é obrigatória: o comando é recusado sem ela, para
     * nenhum número calculado virar decisão sozinho.
     */
    public interface Record {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID planId, String method, BigDecimal targetVolumes,
                BigDecimal referenceTempC, String primingSugar, boolean confirmed) {}
    }

    /** Carbonatação decidida do plano, se já houver uma. */
    public interface Get {
        Optional<Carbonation> handle(UUID breweryId, UUID planId);
    }

    /**
     * Recomendação com tudo o que a decisão precisa mostrar: entradas normalizadas, o que falta
     * dissolver, o resultado, o método com versão e os alertas.
     *
     * @param beerVolumeLiters volume planejado do envase, que entra no cálculo do priming
     */
    /**
     * @param pressureBar pressão aplicada na carbonatação forçada; nula no priming, onde não há pressão
     *                    aplicada — quem pressuriza é o próprio açúcar
     * @param equilibriumPressureBar a pressão que a embalagem vai ver quando o CO₂ equilibrar, na
     *                    temperatura de referência. Vale para os DOIS métodos: a física é a mesma,
     *                    independente de como o gás chegou lá (PKG-002-A)
     * @param containerMaxPressureBar o limite da embalagem, quando cadastrado
     */
    public record Recommendation(String method, BigDecimal targetVolumes, BigDecimal referenceTempC,
            BigDecimal residualVolumes, BigDecimal missingVolumes, BigDecimal beerVolumeLiters,
            String primingSugar, BigDecimal primingSugarGrams, BigDecimal pressureBar,
            BigDecimal equilibriumPressureBar, BigDecimal containerMaxPressureBar,
            String calculationMethod, String calculatorVersion, List<String> assumptions,
            List<String> alerts) {}
}
