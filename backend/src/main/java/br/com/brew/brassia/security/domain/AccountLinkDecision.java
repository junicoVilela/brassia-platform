package br.com.brew.brassia.security.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O que fazer quando alguém volta de um provedor de identidade (SEC-B07).
 *
 * <p><strong>Esta é a parte perigosa de todo login federado, e o perigo tem nome: vínculo por e-mail.</strong>
 * O caminho tentador é "o provedor disse que esta pessoa é ana@cervejaria.com, então logue-a como a nossa
 * conta ana@cervejaria.com". Isso entrega qualquer conta local a quem controlar — ou conseguir enganar — um
 * provedor configurado: basta ele afirmar o e-mail certo. É o ataque conhecido como <em>account takeover
 * por asserção de e-mail</em>, e ele já derrubou sistemas grandes.
 *
 * <p>A regra aqui é: <strong>e-mail nunca vincula sozinho a uma conta que já existe.</strong> Só três
 * desfechos são possíveis, e a diferença entre eles é quem já provou o quê.
 */
public final class AccountLinkDecision {

    /** O que fazer. */
    public enum Outcome {
        /** Já existe vínculo entre este provedor e este subject. É o caminho normal do segundo login. */
        LINK_EXISTS,

        /**
         * Não há vínculo nem conta local com aquele e-mail: cria a conta (JIT provisioning).
         *
         * <p>Seguro porque não há nada a sequestrar — a conta nasce agora, pertencendo a esta identidade
         * externa desde o primeiro instante.
         */
        PROVISION,

        /**
         * Existe uma conta local com aquele e-mail, mas nenhum vínculo com este provedor.
         *
         * <p><strong>Recusa.</strong> É exatamente o caso em que vincular seria entregar uma conta
         * existente a quem apenas afirmou um e-mail. O caminho legítimo é a pessoa entrar pela conta local
         * e vincular o provedor de dentro dela — provando que é dona dos dois lados.
         */
        REFUSE_WOULD_HIJACK
    }

    private final Outcome outcome;
    private final UUID userId;

    private AccountLinkDecision(Outcome outcome, UUID userId) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.userId = userId;
    }

    /**
     * Decide o desfecho.
     *
     * @param existingLink      usuário já vinculado a este provedor+subject, se houver.
     * @param localAccount      usuário local com o e-mail asserido, se houver.
     * @param providerJitMode   se o provedor está autorizado a criar contas.
     * @param emailVerified     se o provedor afirma ter verificado o e-mail.
     */
    public static AccountLinkDecision decide(Optional<UUID> existingLink, Optional<UUID> localAccount,
            boolean providerJitMode, boolean emailVerified) {
        Objects.requireNonNull(existingLink, "existingLink");
        Objects.requireNonNull(localAccount, "localAccount");

        // 1. Vínculo existente ganha de tudo. Quem já provou que é dono dos dois lados não precisa provar
        //    de novo, e o e-mail asserido agora nem entra na decisão — uma pessoa pode ter trocado de
        //    e-mail no provedor sem deixar de ser a mesma pessoa.
        if (existingLink.isPresent()) {
            return new AccountLinkDecision(Outcome.LINK_EXISTS, existingLink.get());
        }

        // 2. Conta local com o mesmo e-mail e sem vínculo: NÃO vincular. É o ponto inteiro desta classe.
        if (localAccount.isPresent()) {
            return new AccountLinkDecision(Outcome.REFUSE_WOULD_HIJACK, null);
        }

        // 3. Não há o que sequestrar. Criar exige que o provedor tenha JIT habilitado e que ele afirme ter
        //    verificado o e-mail — sem verificação, qualquer pessoa que consiga um cadastro no provedor
        //    escolhe o e-mail com que aparece aqui.
        if (providerJitMode && emailVerified) {
            return new AccountLinkDecision(Outcome.PROVISION, null);
        }

        // JIT desligado, ou e-mail não verificado. Recusa pelo mesmo motivo: não se cria conta sobre uma
        // afirmação que ninguém checou.
        return new AccountLinkDecision(Outcome.REFUSE_WOULD_HIJACK, null);
    }

    /** Normaliza o e-mail para comparação. Caixa diferente é a mesma caixa postal. */
    public static String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("e-mail asserido é obrigatório");
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Presente apenas em {@link Outcome#LINK_EXISTS}. */
    public Optional<UUID> userId() {
        return Optional.ofNullable(userId);
    }
}
