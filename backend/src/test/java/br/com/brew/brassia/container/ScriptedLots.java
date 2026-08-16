package br.com.brew.brassia.container;

import br.com.brew.brassia.packaging.SellableLotLookup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Lotes acabados roteirizados, para os testes de contêiner.
 *
 * <p><strong>Por que um dublê aqui.</strong> A composição real das três condições de venda é exercida de
 * ponta a ponta pelo {@code PackagingRunIT}, com envase, liberação e quarentena de verdade. O que estes
 * testes precisam provar é outra coisa: <strong>como o vasilhame reage a cada impedimento</strong>.
 * Reproduzir mil linhas de cenário de envase para decidir um {@code if} deixaria o teste caro e o motivo
 * da falha longe da causa.
 *
 * <p>O padrão nasce sem liberação, de propósito: é o estado real de um lote recém-envasado, e é
 * exatamente o caso em que encher <em>deve</em> passar.
 */
@TestConfiguration
public class ScriptedLots {

    @Bean
    @Primary
    public Roteiro roteiro() {
        return new Roteiro();
    }

    public static class Roteiro implements SellableLotLookup {

        private final Map<UUID, LotSaleStatus> lotes = new ConcurrentHashMap<>();

        /** Um lote recém-envasado: existe, tem código, e ainda não foi liberado. */
        public UUID recemEnvasado(String code) {
            var id = UUID.randomUUID();
            lotes.put(id, new LotSaleStatus(id, code, false,
                    Optional.of(new Blocker("not_released", "Falta a liberação da qualidade.")),
                    LocalDate.now().plusMonths(6)));
            return id;
        }

        public UUID comImpedimento(String code, String blocker, String mensagem) {
            var id = UUID.randomUUID();
            lotes.put(id, new LotSaleStatus(id, code, false,
                    Optional.of(new Blocker(blocker, mensagem)), LocalDate.now()));
            return id;
        }

        @Override
        public List<SellableLot> sellableLots(UUID breweryId, UUID recipeId, UUID containerId,
                LocalDate on) {
            return List.of();
        }

        @Override
        public Optional<LotSaleStatus> statusOf(UUID breweryId, UUID finishedLotId, LocalDate on) {
            return Optional.ofNullable(lotes.get(finishedLotId));
        }

        /** Volume qualquer, só para os testes que precisam de um número. */
        public BigDecimal volumePadrao() {
            return new BigDecimal("50");
        }
    }
}
