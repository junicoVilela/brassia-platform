package br.com.brew.brassia.sensor.domain;

/**
 * Qualidade de uma leitura (INT-001).
 *
 * <p><strong>Sinalizar, não rejeitar — e o motivo é sobre o que fica no lugar.</strong> Recusar uma leitura
 * ruim não deixa "nada"; deixa um buraco na curva, e um buraco é indistinguível de "o sensor não mediu" e de
 * "não aconteceu nada". Uma densidade absurda gravada e marcada conta duas coisas verdadeiras — que o
 * dispositivo reportou naquele instante e que não se deve acreditar no número. Um vazio não conta nenhuma.
 *
 * <p>Quem consome decide o que fazer: um gráfico esconde o que não é {@link #GOOD}, uma média ignora, um
 * alerta de manutenção se interessa justamente pelo que foi sinalizado.
 */
public enum ReadingQuality {

    /** Dentro da faixa plausível e com relógio coerente. */
    GOOD,

    /**
     * Valor fora da faixa plausível da grandeza. Sensor sujo, descalibrado, fora d'água ou com o cabo
     * solto — todos produzem número, e número plausível é o que distingue medição de ruído.
     */
    OUT_OF_RANGE,

    /**
     * Medida vinda do futuro segundo o nosso relógio. Não é filosofia: é o sintoma clássico de dispositivo
     * sem NTP que voltou de um reset com o relógio de fábrica, e de fuso configurado errado no firmware.
     * O valor pode estar certíssimo — o instante é que não serve para ordenar nada, e uma curva ordenada
     * por um instante inventado mente sobre a sequência dos fatos.
     */
    FUTURE_CLOCK
}
