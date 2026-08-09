package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.SsoHandshake;
import java.net.URI;
import java.util.Map;

/**
 * A fronteira com o provedor de identidade (SEC-B07).
 *
 * <p>Existe como porta por um motivo concreto e não por simetria: <strong>não há como exercitar um fluxo
 * federado de verdade num teste automatizado</strong> sem um IdP de terceiro, e os cenários que mais
 * importam — token com nonce de outra conversa, assertion fora da janela, provedor devolvendo um e-mail
 * que já pertence a uma conta local — são justamente os que nenhum IdP real produz sob encomenda. O dublê
 * programável é o que torna essas situações testáveis.
 *
 * <p>É a mesma decisão tomada em AIA-001 para o provedor de modelo, e pelo mesmo motivo.
 */
public interface FederatedIdentityProvider {

    /**
     * Para onde mandar o navegador.
     *
     * <p>Recebe o aperto de mão inteiro porque state, nonce e desafio PKCE viajam na URL — e é o provedor
     * concreto (SAML ou OIDC) que sabe como cada um se chama no protocolo dele.
     */
    URI authorizationUri(ProviderConfig config, SsoHandshake handshake);

    /**
     * Verifica a volta e devolve quem é a pessoa.
     *
     * <p>O {@code handshake} entra para que a implementação confira nonce e PKCE contra o que foi guardado
     * na ida. Devolver uma identidade sem essa conferência seria aceitar qualquer resposta bem formada.
     *
     * @throws br.com.brew.brassia.security.domain.InvalidSsoHandshakeException quando a volta não confere.
     */
    AssertedIdentity verify(ProviderConfig config, SsoHandshake handshake, Map<String, String> callback);

    /** O que a configuração do provedor precisa expor para o fluxo funcionar. */
    record ProviderConfig(
            java.util.UUID id,
            java.util.UUID breweryId,
            String code,
            String protocol,
            String issuerOrEntityId,
            Map<String, Object> configuration,
            boolean jitMode) {
    }

    /**
     * Quem o provedor diz que a pessoa é.
     *
     * <p>{@code emailVerified} não é detalhe: é a diferença entre "o provedor checou este e-mail" e "alguém
     * digitou este e-mail no cadastro dele". Sem verificação, quem conseguir um cadastro no provedor
     * escolhe com qual e-mail aparece aqui — e o provisionamento automático viraria uma porta.
     */
    record AssertedIdentity(String subject, String email, boolean emailVerified, String displayName) {
    }
}
