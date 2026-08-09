package br.com.brew.brassia.security.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * O aperto de mão de um login SSO iniciado pelo nosso lado (SEC-B07).
 *
 * <p><strong>Um login federado é uma conversa que sai da nossa aplicação, passa por um terceiro e volta.</strong>
 * Entre a ida e a volta não há nada ligando as duas pontas — o navegador que volta pode ser outro, a
 * resposta pode ter sido fabricada, e a mesma resposta pode voltar duas vezes. Este objeto é o que amarra:
 * ele é criado na ida, guardado do nosso lado, e exigido na volta.
 *
 * <p>Três amarras, cada uma contra um ataque diferente:
 *
 * <ul>
 *   <li><strong>{@code state}</strong> — contra CSRF de login. Sem ele, um atacante inicia um fluxo com a
 *       própria conta e induz a vítima a completá-lo, deixando a vítima logada como o atacante e digitando
 *       dados dele achando que são seus.
 *   <li><strong>{@code nonce}</strong> — contra replay do token. Ele viaja para o provedor e volta dentro
 *       do token assinado; um token capturado e reenviado depois traz o nonce de outra conversa.
 *   <li><strong>PKCE</strong> — contra interceptação do código de autorização. O {@code code_verifier}
 *       nunca sai daqui; só o desafio derivado dele vai ao provedor. Quem roubar o código no redirect não
 *       consegue trocá-lo por token sem o verificador.
 * </ul>
 *
 * <p><strong>É de uso único e vence rápido.</strong> Dez minutos cobrem um login com digitação de senha e
 * segundo fator do outro lado, e não cobrem um handshake esquecido numa aba aberta ontem. Consumir na volta
 * é o que impede a mesma resposta de ser aceita duas vezes.
 */
public final class SsoHandshake {

    /**
     * Validade do aperto de mão.
     *
     * <p>Dez minutos: o tempo de digitar senha e segundo fator num provedor externo, com folga para uma
     * página lenta. Mais que isso passa a cobrir a aba esquecida aberta, que é onde um handshake vivo vira
     * uma janela de ataque.
     */
    public static final Duration LIFETIME = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ENTROPY_BYTES = 32;

    private final UUID id;
    private final UUID providerId;
    private final String state;
    private final String nonce;
    private final String codeVerifier;
    private final String redirectAfterLogin;
    private final Instant createdAt;
    private final Instant consumedAt;

    private SsoHandshake(UUID id, UUID providerId, String state, String nonce, String codeVerifier,
            String redirectAfterLogin, Instant createdAt, Instant consumedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.state = Objects.requireNonNull(state, "state");
        this.nonce = Objects.requireNonNull(nonce, "nonce");
        this.codeVerifier = Objects.requireNonNull(codeVerifier, "codeVerifier");
        this.redirectAfterLogin = redirectAfterLogin;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.consumedAt = consumedAt;
    }

    /** Abre um aperto de mão para um provedor, com state, nonce e verificador PKCE novos. */
    public static SsoHandshake open(UUID providerId, String redirectAfterLogin, Instant now) {
        return new SsoHandshake(UUID.randomUUID(), providerId, randomToken(), randomToken(), randomToken(),
                safeRedirect(redirectAfterLogin), now, null);
    }

    public static SsoHandshake reconstitute(UUID id, UUID providerId, String state, String nonce,
            String codeVerifier, String redirectAfterLogin, Instant createdAt, Instant consumedAt) {
        return new SsoHandshake(id, providerId, state, nonce, codeVerifier, redirectAfterLogin, createdAt,
                consumedAt);
    }

    /**
     * O desafio PKCE (S256) derivado do verificador.
     *
     * <p>É o único derivado que sai daqui. O verificador em si nunca vai ao provedor — é exatamente essa
     * assimetria que faz o PKCE valer: quem intercepta o redirect vê o desafio, e do desafio não se volta
     * ao verificador.
     */
    public String codeChallenge() {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /**
     * Confere a volta e consome o aperto de mão.
     *
     * <p>A comparação do {@code state} é em <strong>tempo constante</strong>. Um {@code equals} sai no
     * primeiro byte diferente, e essa diferença é medível pela rede — quem tem paciência descobre o valor
     * um byte por vez.
     *
     * @throws InvalidSsoHandshakeException quando venceu, já foi usado, ou o state não bate.
     */
    public SsoHandshake consumeWith(String incomingState, Instant now) {
        if (consumedAt != null) {
            // Uso único. Sem isso, a mesma resposta do provedor — capturada do histórico do navegador, de
            // um log de proxy ou do Referer — cria uma sessão nova a cada reenvio.
            throw new InvalidSsoHandshakeException("aperto de mão já utilizado");
        }
        if (!now.isBefore(createdAt.plus(LIFETIME))) {
            throw new InvalidSsoHandshakeException("aperto de mão expirado");
        }
        if (incomingState == null || !MessageDigest.isEqual(
                state.getBytes(StandardCharsets.UTF_8), incomingState.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidSsoHandshakeException("state não corresponde");
        }
        return new SsoHandshake(id, providerId, state, nonce, codeVerifier, redirectAfterLogin, createdAt,
                now);
    }

    /**
     * Para onde levar depois do login.
     *
     * <p><strong>Só caminho interno.</strong> Aceitar uma URL absoluta transformaria o login num redirecionador
     * aberto: um link para o nosso próprio domínio que, depois de autenticar, joga a pessoa num site de
     * terceiro — com a barra de endereço tendo mostrado o nosso domínio o tempo todo. É assim que se
     * constrói uma página de phishing convincente.
     */
    private static String safeRedirect(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/";
        }
        var path = raw.trim();
        // Precisa começar com uma barra e não pode começar com duas: `//evil.example.com` é uma URL
        // absoluta protocol-relative, que o navegador segue para fora.
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")) {
            return "/";
        }
        return path;
    }

    private static String randomToken() {
        var bytes = new byte[ENTROPY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UUID id() { return id; }
    public UUID providerId() { return providerId; }
    public String state() { return state; }
    public String nonce() { return nonce; }
    public String codeVerifier() { return codeVerifier; }
    public String redirectAfterLogin() { return redirectAfterLogin; }
    public Instant createdAt() { return createdAt; }
    public Instant consumedAt() { return consumedAt; }
    public boolean consumed() { return consumedAt != null; }
}
