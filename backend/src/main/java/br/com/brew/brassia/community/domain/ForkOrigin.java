package br.com.brew.brassia.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * De onde uma receita foi copiada (COM-003).
 *
 * <p><strong>Tudo aqui é congelado, e nada é ponteiro vivo.</strong> O critério da história é literal:
 * "sem acesso futuro ao conteúdo privado do autor". O nome do autor, o título e a licença são gravados
 * como estavam no momento do fork — se o autor renomear a publicação, fechar a visibilidade ou
 * despublicar, a atribuição continua correta e o forkador <strong>não ganha nada novo</strong>.
 *
 * <p>O identificador da publicação fica guardado para a tela poder oferecer o link de volta, e não para
 * dar acesso: abrir aquela publicação continua passando pela matriz de visibilidade. Se o autor fechou,
 * o forkador vê a atribuição e não vê o conteúdo — que é exatamente o comportamento certo.
 *
 * <p><strong>A licença viaja junto porque ela é a obrigação que sobrevive à cópia.</strong> Uma receita
 * forkada sob CC BY-SA carrega a exigência de compartilhar igual; sem registrar a licença de origem,
 * ninguém saberia disso seis meses depois.
 *
 * @param sourceAuthorName nome como estava no fork — atribuição não muda quando a pessoa troca o nome
 */
public record ForkOrigin(UUID sourcePublicationId, String sourceAuthorName, String sourceTitle,
        RecipeLicense sourceLicense, long sourceRecipeVersion, Instant forkedAt) {

    public ForkOrigin {
        Objects.requireNonNull(sourcePublicationId, "publicação de origem");
        Objects.requireNonNull(sourceLicense, "licença de origem");
        Objects.requireNonNull(forkedAt, "instante do fork");
        if (sourceAuthorName == null || sourceAuthorName.isBlank()) {
            throw new IllegalArgumentException("a atribuição precisa do nome do autor");
        }
        if (sourceTitle == null || sourceTitle.isBlank()) {
            throw new IllegalArgumentException("a atribuição precisa do título de origem");
        }
        sourceAuthorName = sourceAuthorName.strip();
        sourceTitle = sourceTitle.strip();
    }

    /**
     * A licença que a cópia é obrigada a carregar, se houver.
     *
     * <p>CC BY-SA é a única que se propaga: ela existe justamente para que derivados continuem abertos.
     * As outras deixam o forkador escolher a licença da própria receita — o que ele fez é dele, desde que
     * a atribuição fique.
     */
    public java.util.Optional<RecipeLicense> requiredLicenseForDerivative() {
        return sourceLicense == RecipeLicense.CC_BY_SA
                ? java.util.Optional.of(RecipeLicense.CC_BY_SA)
                : java.util.Optional.empty();
    }

    /** Como a atribuição se escreve numa tela: "IPA da Casa, de Ana (CC BY 4.0)". */
    public String attribution() {
        return sourceTitle + ", de " + sourceAuthorName + " (" + sourceLicense.label() + ")";
    }
}
