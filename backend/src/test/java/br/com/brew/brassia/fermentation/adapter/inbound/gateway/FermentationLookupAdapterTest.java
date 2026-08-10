package br.com.brew.brassia.fermentation.adapter.inbound.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.ScheduleRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.fermentation.domain.FermentationReading;
import br.com.brew.brassia.fermentation.domain.FermentationSchedule;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import br.com.brew.brassia.fermentation.domain.AdvanceCondition;
import br.com.brew.brassia.fermentation.domain.ReadingSource;
import br.com.brew.brassia.fermentation.domain.ScheduleAction;
import br.com.brew.brassia.fermentation.domain.ScheduleStep;
import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O retrato que a fermentação publica (DEB-AIA-001).
 *
 * <p>O que se testa aqui é a distinção que o débito existia para preservar: <strong>ausência não é
 * zero</strong>. Um lote sem fermentação registrada e um lote com agenda em dia produzem retratos
 * diferentes, e colapsá-los faria a avaliação ler o primeiro como o segundo.
 */
class FermentationLookupAdapterTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID LOTE = UUID.randomUUID();
    private static final Instant MEDIU = Instant.parse("2026-08-09T10:00:00Z");
    private static final Clock AGORA = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("lote sem leitura, sem agenda e sem levedura devolve VAZIO, não um retrato zerado")
    void semNadaDevolveVazio() {
        // A guarda que importa. Um retrato com zeros diria "0 etapas atrasadas" de um lote que nem chegou
        // ao fermentador — indistinguível de um lote rigorosamente em dia.
        var adapter = adapter(vazio(), Optional.empty(), Optional.empty());

        assertThat(adapter.ofBatch(CERVEJARIA, LOTE)).isEmpty();
    }

    @Test
    @DisplayName("as pontas da curva viajam com valor, unidade e instante")
    void pontasDaCurva() {
        var latest = new ReadingRepository.Latest(842, leitura(ReadingKind.DENSITY, "1.012", "SG"),
                leitura(ReadingKind.TEMPERATURE, "19.5", "C"));

        var retrato = adapter(latest, Optional.empty(), Optional.empty())
                .ofBatch(CERVEJARIA, LOTE).orElseThrow();

        assertThat(retrato.readingCount()).isEqualTo(842);
        assertThat(retrato.lastDensity().value()).isEqualByComparingTo("1.012");
        assertThat(retrato.lastDensity().unit()).isEqualTo("SG");
        assertThat(retrato.lastDensity().measuredAt()).isEqualTo(MEDIU);
        assertThat(retrato.lastTemperature().value()).isEqualByComparingTo("19.5");
    }

    @Test
    @DisplayName("grandeza nunca medida vem NULA, e não como zero")
    void grandezaNuncaMedidaVemNula() {
        // Densidade zero não existe em cerveja; densidade ausente existe o tempo todo, e é o estado de um
        // lote acompanhado só por temperatura. Zero seria lido como fermentação impossível.
        var latest = new ReadingRepository.Latest(12, null,
                leitura(ReadingKind.TEMPERATURE, "19.5", "C"));

        var retrato = adapter(latest, Optional.empty(), Optional.empty())
                .ofBatch(CERVEJARIA, LOTE).orElseThrow();

        assertThat(retrato.lastDensity()).isNull();
        assertThat(retrato.lastTemperature()).isNotNull();
    }

    @Test
    @DisplayName("sem levedura vinculada a geração é NULA — levedura nova não é geração zero")
    void semLeveduraGeracaoNula() {
        var latest = new ReadingRepository.Latest(3, null,
                leitura(ReadingKind.TEMPERATURE, "19.5", "C"));

        assertThat(adapter(latest, Optional.empty(), Optional.empty())
                .ofBatch(CERVEJARIA, LOTE).orElseThrow().yeastGeneration()).isNull();
    }

    @Test
    @DisplayName("sem agenda o total de etapas é zero, e o retrato ainda existe por causa das leituras")
    void semAgendaTotalZero() {
        var latest = new ReadingRepository.Latest(3, null,
                leitura(ReadingKind.TEMPERATURE, "19.5", "C"));

        var retrato = adapter(latest, Optional.empty(), Optional.empty())
                .ofBatch(CERVEJARIA, LOTE).orElseThrow();

        assertThat(retrato.totalSteps()).isZero();
        assertThat(retrato.lateSteps()).isZero();
        assertThat(retrato.doneSteps()).isZero();
    }

    @Test
    @DisplayName("etapa pendente fora da janela é contada como atrasada")
    void etapaForaDaJanelaEAtrasada() {
        // O sinal mais direto de lote em apuros, e o que o modelo não teria como inferir dos outros fatos.
        var agenda = agendaComEtapaVencida();

        var retrato = adapter(vazio(), Optional.of(agenda), Optional.empty())
                .ofBatch(CERVEJARIA, LOTE).orElseThrow();

        assertThat(retrato.totalSteps()).isPositive();
        assertThat(retrato.lateSteps()).isPositive();
        assertThat(retrato.doneSteps()).isZero();
    }

    @Test
    @DisplayName("levedura de quinta geração viaja como geração, não como presença")
    void geracaoDeLevedura() {
        // Geração alta é fator de risco conhecido: a levedura perde viabilidade e muda de comportamento a
        // cada reuso. "Tem levedura vinculada" não contaria isso.
        var colheita = YeastHarvest.reconstitute(UUID.randomUUID(), CERVEJARIA, "LEV-5",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5, MEDIU,
                new BigDecimal("88"), "BOA", "Câmara 1", new BigDecimal("4"),
                br.com.brew.brassia.fermentation.domain.YeastHarvestStatus.USED, null, MEDIU,
                UUID.randomUUID(), LOTE, MEDIU);

        var retrato = adapter(vazio(), Optional.empty(), Optional.of(colheita))
                .ofBatch(CERVEJARIA, LOTE).orElseThrow();

        assertThat(retrato.yeastGeneration()).isEqualTo(5);
    }

    private static ReadingRepository.Latest vazio() {
        return new ReadingRepository.Latest(0, null, null);
    }

    private static FermentationReading leitura(ReadingKind kind, String value, String unit) {
        return FermentationReading.record(CERVEJARIA, LOTE, kind, ReadingSource.SENSOR,
                new BigDecimal(value), unit, MEDIU);
    }

    /** Uma etapa planejada para terminar há uma semana e nunca executada. */
    private static FermentationSchedule agendaComEtapaVencida() {
        var passo = ScheduleStep.plan(1, "Fermentação primária", ScheduleAction.REST,
                AdvanceCondition.TIME, 5, null, Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-02T10:00:00Z"), 12, UUID.randomUUID(), false);
        return FermentationSchedule.reconstitute(UUID.randomUUID(), CERVEJARIA, LOTE, UUID.randomUUID(),
                1, List.of(passo));
    }

    private static FermentationLookupAdapter adapter(ReadingRepository.Latest latest,
            Optional<FermentationSchedule> agenda, Optional<YeastHarvest> colheita) {
        return new FermentationLookupAdapter(readings(latest), schedules(agenda), harvests(colheita), AGORA);
    }

    private static ReadingRepository readings(ReadingRepository.Latest latest) {
        return new ReadingRepository() {
            @Override
            public UpsertResult upsertIfAbsent(FermentationReading reading) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<FermentationReading> findSeries(UUID breweryId, UUID batchId, ReadingKind kind) {
                throw new UnsupportedOperationException(
                        "o retrato não pode carregar a série inteira — é o ponto de latestOf");
            }

            @Override
            public Latest latestOf(UUID breweryId, UUID batchId) {
                return latest;
            }
        };
    }

    private static ScheduleRepository schedules(Optional<FermentationSchedule> agenda) {
        return new ScheduleRepository() {
            @Override
            public void insert(FermentationSchedule schedule) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void replaceSteps(FermentationSchedule schedule) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<FermentationSchedule> findByBatch(UUID breweryId, UUID batchId) {
                return agenda;
            }

            @Override
            public Optional<FermentationSchedule> findById(UUID breweryId, UUID scheduleId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<FermentationSchedule> findWithPendingSteps(UUID breweryId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static YeastHarvestRepository harvests(Optional<YeastHarvest> colheita) {
        return new YeastHarvestRepository() {
            @Override
            public void insert(YeastHarvest harvest) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updateReview(YeastHarvest harvest) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updatePitch(YeastHarvest harvest) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<YeastHarvest> findById(UUID breweryId, UUID harvestId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<YeastHarvest> findPitchedInto(UUID breweryId, UUID batchId) {
                return colheita;
            }

            @Override
            public boolean existsByCode(UUID breweryId, String code) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<YeastHarvest> findAll(UUID breweryId, boolean onlyAvailable) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<YeastHarvest> findAncestry(UUID breweryId, UUID harvestId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
