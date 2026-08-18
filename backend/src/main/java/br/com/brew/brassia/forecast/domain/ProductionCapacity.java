package br.com.brew.brassia.forecast.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Quanto a casa consegue produzir num período, e o que ela precisa declarar para isso ser calculável
 * (DUV-FCST-001).
 *
 * <p><strong>O tempo de ciclo é declarado, e não inferido.</strong> Quantos dias uma cerveja ocupa o
 * fermentador depende do estilo, da temperatura e do que a casa aceita — inferir isso de lotes passados
 * daria um número que parece cálculo e é média de coisas diferentes. A cervejaria declara os dias por
 * tanque; o sistema multiplica.
 *
 * <p><strong>Sem tanque declarado, a resposta é "não sei" — e não zero.</strong> Zero diria que a
 * cervejaria não consegue produzir nada, e alguém planejaria em cima disso. É a mesma escolha que a
 * previsão de demanda faz com histórico curto: {@link ForecastConfidence#INSUFFICIENT} é a ausência do
 * número, e não um número baixo.
 *
 * <p><strong>O que este cálculo NÃO é.</strong> Ele não sabe de turno, de calendário, de limpeza entre
 * lotes nem de gargalo fora do fermentador — maturação a frio, linha de envase, mão de obra. É um teto
 * otimista: se a demanda não cabe nele, ela certamente não cabe na fábrica. O contrário não vale, e está
 * dito no contrato.
 */
public record ProductionCapacity(List<Tank> tanks, int periodDays) {

    public ProductionCapacity {
        tanks = List.copyOf(Objects.requireNonNull(tanks, "tanques"));
        if (periodDays < 1) {
            throw new IllegalArgumentException("o período precisa de pelo menos um dia");
        }
    }

    /** Vazio quando a casa não declarou ciclo de nenhum tanque: ausência de resposta, e não zero. */
    public boolean known() {
        return !tanks.isEmpty();
    }

    /**
     * O teto de litros no período.
     *
     * <p>Cada tanque rende {@code volume × ⌊dias do período ÷ dias de ciclo⌋}. O piso é deliberado: um
     * lote que não termina dentro do período não conta — contá-lo pela fração faria a capacidade incluir
     * cerveja que ainda estará fermentando quando o mês virar.
     */
    public BigDecimal litersInPeriod() {
        return tanks.stream()
                .map(t -> t.volumeLiters().multiply(BigDecimal.valueOf(periodDays / t.cycleDays())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Se a demanda prevista cabe no teto.
     *
     * <p>Vazio quando a capacidade é desconhecida: responder "cabe" sem saber seria pior que não
     * responder, porque quem lê tomaria por confirmação.
     */
    public java.util.Optional<Boolean> fits(BigDecimal demandLiters) {
        Objects.requireNonNull(demandLiters, "demanda");
        return known() ? java.util.Optional.of(litersInPeriod().compareTo(demandLiters) >= 0)
                : java.util.Optional.empty();
    }

    /** Quanto sobra — negativo quando falta, porque a falta é a informação que importa. */
    public java.util.Optional<BigDecimal> headroomLiters(BigDecimal demandLiters) {
        return known() ? java.util.Optional.of(litersInPeriod().subtract(demandLiters))
                : java.util.Optional.empty();
    }

    /** Quantos por cento do teto a demanda ocupa — o número que a tela mostra. */
    public java.util.Optional<BigDecimal> utilizationPercent(BigDecimal demandLiters) {
        if (!known() || litersInPeriod().signum() == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(demandLiters.multiply(BigDecimal.valueOf(100))
                .divide(litersInPeriod(), 1, RoundingMode.HALF_UP));
    }

    /**
     * Um fermentador declarado.
     *
     * @param cycleDays dias que um lote ocupa o tanque, <strong>declarados pela casa</strong>
     */
    public record Tank(String equipmentCode, BigDecimal volumeLiters, int cycleDays) {

        public Tank {
            Objects.requireNonNull(equipmentCode, "equipamento");
            if (volumeLiters == null || volumeLiters.signum() <= 0) {
                throw new IllegalArgumentException("o volume do tanque deve ser positivo");
            }
            if (cycleDays < 1) {
                // Ciclo zero seria produção infinita, e o erro só apareceria como uma capacidade
                // absurda que ninguém questiona porque veio do sistema.
                throw new IllegalArgumentException("o ciclo do tanque deve ser de pelo menos um dia");
            }
        }
    }
}
