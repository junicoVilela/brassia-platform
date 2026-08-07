package br.com.brew.brassia.shared.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Um número do painel operacional (RPT-002), com o que ele significa e onde ele se abre.
 *
 * <p><strong>Os três requisitos da história são invariantes de construção</strong>, e não convenção:
 * o construtor recusa indicador sem definição, sem período e sem destino de drill-down. É de
 * propósito. "Indicador sem definição" está listado como risco da sprint, e a única forma de um
 * risco desses não voltar é tornar impossível criar o objeto errado — um número de painel sem
 * definição escrita vira, em três meses, um número que cada pessoa da fábrica interpreta de um jeito.
 *
 * <p><strong>Quem define é quem mede.</strong> O que conta como "desvio em aberto" é assunto da
 * qualidade, não do painel; o painel que escrevesse essas definições estaria inventando regra sobre
 * domínio alheio. Por isso a forma mora aqui, no compartilhado, e o conteúdo em cada módulo.
 *
 * @param code       identificador estável, em ponto ({@code producao.lotes_iniciados}); é por ele
 *                   que a tela reconhece o indicador, e não pelo rótulo, que muda com a tradução
 * @param definition o que o número quer dizer, por extenso. Obrigatório
 * @param from       início do período; <strong>vazio significa posição</strong>, não ausência — um
 *                   estoque vencendo é uma foto do instante {@code to}, não um acumulado
 * @param to         fim do período, ou o instante da foto. Obrigatório
 * @param drillDown  onde o número se abre. Obrigatório: número de painel que não se abre é número
 *                   que ninguém consegue conferir, e o que não se confere não se corrige
 * @param gap        o que este número não cobre, quando há algo a dizer; nulo quando não há
 */
public record OperationalIndicator(String code, IndicatorGroup group, String label, String definition,
        BigDecimal value, String unit, Instant from, Instant to, DrillDown drillDown, String gap) {

    public OperationalIndicator {
        code = requireText(code, "código do indicador");
        Objects.requireNonNull(group, "grupo é obrigatório");
        label = requireText(label, "rótulo do indicador");
        definition = requireText(definition, "definição do indicador");
        Objects.requireNonNull(value, "valor é obrigatório");
        Objects.requireNonNull(to, "período é obrigatório");
        Objects.requireNonNull(drillDown, "destino de drill-down é obrigatório");
        if (from != null && from.isAfter(to)) {
            throw new IllegalArgumentException("o início do período é depois do fim");
        }
    }

    /** Indicador de acumulado num intervalo. */
    public static OperationalIndicator inPeriod(String code, IndicatorGroup group, String label,
            String definition, BigDecimal value, String unit, Instant from, Instant to,
            DrillDown drillDown) {
        return new OperationalIndicator(code, group, label, definition, value, unit, from, to,
                drillDown, null);
    }

    /** Indicador de posição: a foto de agora, sem acumular nada. */
    public static OperationalIndicator snapshot(String code, IndicatorGroup group, String label,
            String definition, BigDecimal value, String unit, Instant at, DrillDown drillDown) {
        return new OperationalIndicator(code, group, label, definition, value, unit, null, at,
                drillDown, null);
    }

    public OperationalIndicator withGap(String reason) {
        return new OperationalIndicator(code, group, label, definition, value, unit, from, to,
                drillDown, reason);
    }

    /** Verdadeiro quando o número é uma foto do instante, e não um acumulado do intervalo. */
    public boolean positional() {
        return from == null;
    }

    /**
     * Onde o número se abre.
     *
     * <p>Recurso e filtro, não rota: a rota é da interface, e o dia em que ela mudar não pode
     * obrigar o backend a mudar junto. O painel diz "isto se abre nos lotes de produção, filtrados
     * assim"; a tela sabe traduzir isso em endereço.
     */
    public record DrillDown(String resource, Map<String, String> filter) {

        public DrillDown {
            resource = requireText(resource, "recurso do drill-down");
            filter = filter == null ? Map.of() : Map.copyOf(filter);
        }

        public static DrillDown of(String resource) {
            return new DrillDown(resource, Map.of());
        }

        public static DrillDown of(String resource, String key, String value) {
            return new DrillDown(resource, Map.of(key, value));
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value;
    }
}
