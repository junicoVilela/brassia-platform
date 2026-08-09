package br.com.brew.brassia.digitaltwin.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Os limites que o processo <em>tem</em> — não os que alguém <em>quer</em> (SPC-001).
 *
 * <p><strong>Limite de controle não é especificação, e confundir os dois é o erro que esta história existe
 * para impedir.</strong> A diferença não é de fórmula, é de origem:
 *
 * <ul>
 *   <li><strong>Especificação</strong> vem de uma <em>decisão</em>: o estilo pede FG entre 1.010 e 1.014, o
 *       cliente aceita no máximo 50 ppb de oxigênio. Está no plano de controle da qualidade
 *       ({@code quality.SpecLimits}) e não se calcula — se escolhe.
 *   <li><strong>Controle</strong> vem de <em>observação</em>: é o que este processo, com este equipamento e
 *       esta equipe, produz quando nada de anormal acontece. Calcula-se do histórico e não se escolhe.
 * </ul>
 *
 * <p>As duas combinações que a confusão esconde são as que importam:
 *
 * <ul>
 *   <li><strong>Sob controle e fora de especificação</strong> — o processo é estável e está estavelmente
 *       errado. Nenhum ponto dispara alarme, e a cerveja está fora do que se prometeu. Ajustar o processo
 *       ponto a ponto aqui não resolve: é o processo inteiro que precisa mudar.
 *   <li><strong>Fora de controle e dentro de especificação</strong> — tudo passa na inspeção e o processo
 *       está mudando. É o aviso que chega antes do problema, e é exatamente ele que se perde quando alguém
 *       usa o limite da especificação como se fosse limite de controle.
 * </ul>
 *
 * <p>Por isso esta classe <strong>não recebe limite de fora</strong>: só existe fábrica que calcula do
 * histórico. Não há como construí-la com um número escolhido, e essa impossibilidade é a fronteira.
 */
public record ControlLimits(
        BigDecimal centerLine,
        BigDecimal lowerControlLimit,
        BigDecimal upperControlLimit,
        BigDecimal sigma,
        int sampleSize) {

    /**
     * Mínimo de observações para calcular limites.
     *
     * <p>Vinte é a convenção do controle estatístico, e o motivo é concreto: com poucos pontos o desvio
     * observado é instável, e limites calculados sobre ele oscilam a cada medição nova — disparando alarme
     * ora por variação real, ora porque o próprio limite se mexeu. Um limite que se move não serve para
     * dizer que algo mudou.
     */
    public static final int MINIMUM_SAMPLE = 20;

    /**
     * Três sigmas.
     *
     * <p>Não é arbitrário: num processo estável, ~99,7% dos pontos caem dentro de 3σ. Um ponto fora tem
     * ~0,3% de chance de ser acaso, o que torna "algo mudou" a explicação mais provável. Limites mais
     * estreitos (2σ) disparariam alarme falso a cada vinte medições, e alarme falso frequente treina quem
     * opera a ignorar o alarme — que é pior que não ter alarme nenhum.
     */
    private static final BigDecimal SIGMA_MULTIPLIER = new BigDecimal("3");

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    public ControlLimits {
        Objects.requireNonNull(centerLine, "centerLine");
        Objects.requireNonNull(lowerControlLimit, "lowerControlLimit");
        Objects.requireNonNull(upperControlLimit, "upperControlLimit");
    }

    /**
     * Calcula os limites a partir do histórico.
     *
     * @throws InsufficientHistoryException quando não há observações suficientes. Recusar é melhor que
     *                                      devolver limites frouxos: limites calculados sobre cinco pontos
     *                                      passam qualquer coisa, e um controle que nunca dispara parece
     *                                      um processo saudável.
     */
    public static ControlLimits from(List<BigDecimal> observations) {
        Objects.requireNonNull(observations, "observations");
        if (observations.size() < MINIMUM_SAMPLE) {
            throw new InsufficientHistoryException(observations.size(), MINIMUM_SAMPLE);
        }

        var n = new BigDecimal(observations.size());
        var mean = observations.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(n, MC);
        var variance = observations.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(observations.size() - 1), MC);
        var sigma = variance.sqrt(MC);
        var margin = sigma.multiply(SIGMA_MULTIPLIER, MC);

        return new ControlLimits(scaled(mean), scaled(mean.subtract(margin)), scaled(mean.add(margin)),
                scaled(sigma), observations.size());
    }

    public boolean contains(BigDecimal value) {
        return value.compareTo(lowerControlLimit) >= 0 && value.compareTo(upperControlLimit) <= 0;
    }

    /**
     * Em qual metade da faixa o valor caiu.
     *
     * <p>Serve às regras de sequência: sete pontos seguidos do mesmo lado da linha central indicam
     * deslocamento mesmo que nenhum deles chegue perto de um limite.
     */
    public boolean above(BigDecimal value) {
        return value.compareTo(centerLine) > 0;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    /** Histórico curto demais para calcular limites que signifiquem alguma coisa. */
    public static final class InsufficientHistoryException extends RuntimeException {

        private final int available;
        private final int required;

        InsufficientHistoryException(int available, int required) {
            super("histórico insuficiente para limites de controle: " + available + " de " + required);
            this.available = available;
            this.required = required;
        }

        public int available() {
            return available;
        }

        public int required() {
            return required;
        }
    }
}
