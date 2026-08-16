package br.com.brew.brassia.community.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A nota média de uma publicação, com quantas pessoas a deram (COM-005).
 *
 * <p><strong>A média nunca viaja sozinha, e é a mesma regra do {@code Estimate} do gêmeo digital.</strong>
 * "5,0" de uma avaliação e "5,0" de duzentas são o mesmo número e significam coisas opostas — a primeira
 * é a opinião de uma pessoa, a segunda é um fato sobre a receita. Mostrar só a média deixa quem lê
 * concluir a segunda quando o dado é a primeira.
 *
 * <p><strong>Sem avaliação não há média</strong>, e {@code zero} não serve: zero é a pior nota possível,
 * e uma receita nova nasceria parecendo péssima. A ausência é dita.
 *
 * @param average vazio quando ninguém avaliou
 */
public record RatingSummary(BigDecimal average, int count) {

    /** Abaixo disto a média é opinião, e a tela precisa dizer isso. */
    public static final int FEW_VOTES = 5;

    public static RatingSummary none() {
        return new RatingSummary(null, 0);
    }

    public static RatingSummary of(BigDecimal sum, int count) {
        if (count <= 0) {
            return none();
        }
        return new RatingSummary(sum.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP), count);
    }

    public boolean hasVotes() {
        return count > 0;
    }

    /**
     * Se a média já diz alguma coisa sobre a receita, em vez de sobre quem votou.
     *
     * <p>Não é sobre confiança estatística — cinco votos não fazem ciência. É o limite abaixo do qual a
     * tela deve mostrar o número como opinião, e não como reputação.
     */
    public boolean meaningful() {
        return count >= FEW_VOTES;
    }
}
