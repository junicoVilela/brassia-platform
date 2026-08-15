package br.com.brew.brassia.sales.domain;

/**
 * Tentaram operar sobre duas moedas diferentes (SAL-001).
 *
 * <p>Recusar é o ponto: somar real com dólar sem taxa produz um número que parece dinheiro e não é. O
 * erro silencioso só apareceria no fechamento, longe da causa.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String one, String other) {
        super("não se opera " + one + " com " + other + " sem uma taxa de conversão declarada");
    }
}
