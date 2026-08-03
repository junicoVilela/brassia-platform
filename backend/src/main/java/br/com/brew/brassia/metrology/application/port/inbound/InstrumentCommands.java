package br.com.brew.brassia.metrology.application.port.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Comandos do instrumento (MTR-001). */
public final class InstrumentCommands {

    private InstrumentCommands() {
    }

    public interface Register {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String name, String type, BigDecimal rangeMin,
                BigDecimal rangeMax, BigDecimal resolution, BigDecimal accuracy, String unit,
                String location) {}
    }

    /** Corrige nome, faixa e localização; mexer na faixa de instrumento crítico é recusado. */
    public interface Amend {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID instrumentId, String name, BigDecimal rangeMin,
                BigDecimal rangeMax, BigDecimal resolution, BigDecimal accuracy, String unit,
                String location) {}
    }

    /** Bloqueio e desbloqueio são decisão humana; o bloqueio exige motivo. */
    public interface SetBlock {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID instrumentId, boolean blocked, String reason) {}
    }

    /** Baixa do parque: terminal. */
    public interface Retire {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID instrumentId, String reason) {}
    }

    /** Designa (ou remove) uso em ponto crítico; designar exige instrumento apto. */
    public interface DesignateCriticalUse {
        void handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID instrumentId, boolean criticalUse) {}
    }

    /** Registra certificado de calibração; o vencimento vem do certificado, não de regra fixa. */
    public interface Calibrate {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID instrumentId, UUID standardId, LocalDate performedOn,
                LocalDate dueOn, String performedBy, String certificateNumber, String result,
                BigDecimal maxDeviation, String restriction, String note, List<Point> curve) {}

        /** Ponto do certificado: valor verdadeiro × valor indicado pelo instrumento. */
        record Point(BigDecimal reference, BigDecimal measured) {}
    }

    /**
     * Corrige uma leitura (MTR-002) sem tocar no valor bruto. Temperatura e curva são passos
     * independentes: pelo menos um precisa se aplicar, senão não há correção.
     */
    public interface CorrectReading {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID instrumentId, UUID sourceReadingId,
                BigDecimal rawValue, String unit, BigDecimal sampleTempC, BigDecimal calibrationTempC,
                boolean applyCurve) {}
    }
}
