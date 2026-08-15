package br.com.brew.brassia.forecast.domain;

/**
 * Como a previsão foi calculada (FCST-001).
 *
 * <p><strong>A versão viaja com o resultado, e é exigência do aceite da sprint.</strong> Sem ela, duas
 * previsões do mesmo produto feitas em meses diferentes não são comparáveis — e ninguém consegue dizer se
 * o número mudou porque a demanda mudou ou porque o método mudou. É a mesma razão que faz a receita ser
 * versionada.
 *
 * <p><strong>Média móvel, e não algo mais sofisticado.</strong> Com o histórico que uma cervejaria
 * pequena tem, um modelo com sazonalidade ajusta ruído e apresenta o ajuste como conhecimento. A média
 * móvel erra de um jeito que se enxerga no backtest; um modelo complexo erra de um jeito que parece
 * previsão.
 */
public record ForecastMethod(String name, int version) {

    public static final ForecastMethod MOVING_AVERAGE_V1 = new ForecastMethod("moving-average", 1);

    public ForecastMethod {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("o método precisa de nome");
        }
        name = name.strip();
        if (version <= 0) {
            throw new IllegalArgumentException("a versão do método deve ser positiva");
        }
    }

    public String label() {
        return name + " v" + version;
    }
}
