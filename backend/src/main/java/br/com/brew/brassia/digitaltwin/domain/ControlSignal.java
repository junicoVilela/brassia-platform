package br.com.brew.brassia.digitaltwin.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * O que o histórico está dizendo sobre o processo (SPC-001).
 *
 * <p><strong>Um sinal não é um defeito.</strong> Ele diz que a variação observada é grande demais para ser
 * acaso — não que a cerveja está ruim, e muito menos por quê. A distinção é a mesma de DTW-001: o sistema
 * mostra o que os números fazem; a causa é investigada por quem conhece o processo.
 */
public record ControlSignal(Kind kind, String description, int firstIndex, int length) {

    /**
     * Quantos pontos seguidos formam sequência.
     *
     * <p>Sete é a convenção, e o motivo é probabilístico: num processo estável, a chance de sete pontos
     * caírem do mesmo lado da linha central por acaso é ~1 em 128. Menos que isso dispararia alarme sobre
     * coincidência; mais atrasaria o aviso até depois de o problema já ter produzido lotes.
     */
    public static final int RUN_LENGTH = 7;

    public ControlSignal {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(description, "description");
    }

    public enum Kind {
        /** Ponto além de 3σ. É o sinal mais forte: ~0,3% de chance de ser acaso. */
        BEYOND_LIMIT,

        /**
         * Sete ou mais pontos seguidos do mesmo lado da linha central.
         *
         * <p><strong>Nenhum deles precisa estar perto de um limite.</strong> É o caso que a inspeção
         * ponto a ponto não pega: o processo deslocou-se para um novo patamar e continua estável nele.
         */
        RUN_ON_ONE_SIDE,

        /**
         * Sete ou mais pontos seguidos subindo ou descendo.
         *
         * <p>É o aviso mais antecipado dos três: descreve algo mudando <em>agora</em> — desgaste,
         * saturação, sujeira acumulando —, geralmente antes de qualquer ponto sair da faixa.
         */
        TREND
    }

    /**
     * Analisa a sequência de observações contra os limites.
     *
     * <p>A ordem importa e é cronológica: sequência e tendência só existem no tempo. Passar os valores
     * ordenados por magnitude produziria "tendência" em qualquer conjunto — o que é a forma mais fácil de
     * inventar um sinal que não existe.
     */
    public static List<ControlSignal> detect(List<BigDecimal> observations, ControlLimits limits) {
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(limits, "limits");

        var signals = new ArrayList<ControlSignal>();
        signals.addAll(beyondLimits(observations, limits));
        runOnOneSide(observations, limits).ifPresent(signals::add);
        trend(observations).ifPresent(signals::add);
        return List.copyOf(signals);
    }

    private static List<ControlSignal> beyondLimits(List<BigDecimal> observations, ControlLimits limits) {
        var signals = new ArrayList<ControlSignal>();
        for (int i = 0; i < observations.size(); i++) {
            var value = observations.get(i);
            if (!limits.contains(value)) {
                signals.add(new ControlSignal(Kind.BEYOND_LIMIT,
                        "ponto " + (i + 1) + " (" + value.toPlainString() + ") fora dos limites de controle",
                        i, 1));
            }
        }
        return signals;
    }

    /**
     * A sequência mais recente do mesmo lado, se atingir o comprimento.
     *
     * <p>Olha do fim para o começo porque o que interessa é o estado <em>atual</em> do processo: uma
     * sequência que terminou há trinta pontos é história, não aviso.
     */
    private static java.util.Optional<ControlSignal> runOnOneSide(List<BigDecimal> observations,
            ControlLimits limits) {
        if (observations.size() < RUN_LENGTH) {
            return java.util.Optional.empty();
        }
        var last = observations.getLast();
        // Ponto exatamente na linha central não pertence a lado nenhum e interrompe a sequência: contá-lo
        // como continuação inventaria um deslocamento que ele não sustenta.
        if (last.compareTo(limits.centerLine()) == 0) {
            return java.util.Optional.empty();
        }
        var above = limits.above(last);
        var length = 0;
        for (int i = observations.size() - 1; i >= 0; i--) {
            var value = observations.get(i);
            if (value.compareTo(limits.centerLine()) == 0 || limits.above(value) != above) {
                break;
            }
            length++;
        }
        if (length < RUN_LENGTH) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ControlSignal(Kind.RUN_ON_ONE_SIDE,
                length + " pontos seguidos " + (above ? "acima" : "abaixo") + " da linha central",
                observations.size() - length, length));
    }

    /** A tendência mais recente, monotônica estrita. */
    private static java.util.Optional<ControlSignal> trend(List<BigDecimal> observations) {
        if (observations.size() < RUN_LENGTH) {
            return java.util.Optional.empty();
        }
        var last = observations.size() - 1;
        var rising = observations.get(last).compareTo(observations.get(last - 1)) > 0;
        // Empate não é tendência: um processo parado não está indo a lugar nenhum.
        if (observations.get(last).compareTo(observations.get(last - 1)) == 0) {
            return java.util.Optional.empty();
        }
        var length = 1;
        for (int i = last; i > 0; i--) {
            var comparison = observations.get(i).compareTo(observations.get(i - 1));
            if (comparison == 0 || (comparison > 0) != rising) {
                break;
            }
            length++;
        }
        if (length < RUN_LENGTH) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ControlSignal(Kind.TREND,
                length + " pontos seguidos " + (rising ? "subindo" : "descendo"),
                observations.size() - length, length));
    }
}
