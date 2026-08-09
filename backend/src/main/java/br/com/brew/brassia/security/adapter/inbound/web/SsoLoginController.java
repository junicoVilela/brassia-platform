package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.ResolveSessionContextUseCase;
import br.com.brew.brassia.security.application.port.inbound.SsoLoginUseCase;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O fluxo SSO no navegador (SEC-B07).
 *
 * <p><strong>SP-initiated: o fluxo começa aqui, não no provedor.</strong> É o que permite exigir o aperto
 * de mão — sem uma ida nossa não haveria state, nonce nem verificador PKCE guardados, e a volta seria uma
 * resposta sem nada a que se ligar. Um fluxo IdP-initiated é justamente o que não se aceita: ele chega sem
 * ida, e por isso não tem como ser distinguido de uma resposta fabricada.
 *
 * <p><strong>Os dois endpoints são públicos, e é a única forma de funcionarem.</strong> Quem os chama ainda
 * não tem sessão — está tentando criar uma. A proteção não é autenticação: é o aperto de mão de uso único,
 * com validade curta e state conferido em tempo constante.
 *
 * <p>A cervejaria vem do parâmetro na ida porque o provedor é configurado por cervejaria e ainda não há
 * sessão de onde tirá-la. Isso não vaza nada: o código do provedor já é público por natureza — ele aparece
 * no botão da tela de login.
 */
@RestController
@RequestMapping("/api/v1/security/sso")
final class SsoLoginController {

    private final SsoLoginUseCase ssoLogin;
    private final ResolveSessionContextUseCase sessionContext;
    private final HttpSessionSecurityContextPersister sessionPersister;
    private final br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository users;
    private final String appBaseUri;

    SsoLoginController(SsoLoginUseCase ssoLogin, ResolveSessionContextUseCase sessionContext,
            HttpSessionSecurityContextPersister sessionPersister,
            br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository users,
            @org.springframework.beans.factory.annotation.Value(
                    "${brassia.security.sso.app-base-uri:}") String appBaseUri) {
        this.ssoLogin = ssoLogin;
        this.sessionContext = sessionContext;
        this.sessionPersister = sessionPersister;
        this.users = users;
        this.appBaseUri = appBaseUri;
    }

    /**
     * Começa o login: abre o aperto de mão e manda o navegador ao provedor.
     *
     * <p>302 e não JSON: o navegador precisa <em>ir</em>, e uma resposta JSON exigiria que o cliente
     * fizesse o redirecionamento — o que só adicionaria um lugar a mais onde a URL pode ser adulterada.
     */
    @GetMapping("/{providerCode}/start")
    ResponseEntity<Void> start(@PathVariable String providerCode,
            @RequestParam UUID breweryId,
            @RequestParam(required = false) String redirect) {
        var start = ssoLogin.start(new SsoLoginUseCase.StartCommand(breweryId, providerCode, redirect));
        return ResponseEntity.status(HttpStatus.FOUND).location(start.authorizationUri()).build();
    }

    /**
     * A volta do provedor.
     *
     * <p>Aceita {@code GET} (OIDC, que volta por redirect com o código na query) e {@code POST} (SAML, que
     * volta por form POST com a assertion no corpo). São protocolos diferentes chegando ao mesmo lugar, e
     * o que decide a interpretação é o provedor cadastrado — não o método HTTP.
     *
     * <p>Termina em <strong>redirect para a aplicação</strong>, com ou sem sucesso. Devolver JSON aqui
     * deixaria a pessoa numa página em branco no meio de um login: ela clicou num botão e espera voltar
     * para a aplicação, não ler um corpo de resposta.
     */
    @GetMapping("/{providerCode}/callback")
    ResponseEntity<Void> callbackGet(@PathVariable String providerCode,
            @RequestParam Map<String, String> parameters,
            HttpServletRequest request, HttpServletResponse response) {
        return complete(parameters, request, response);
    }

    @PostMapping("/{providerCode}/callback")
    ResponseEntity<Void> callbackPost(@PathVariable String providerCode,
            @RequestParam Map<String, String> parameters,
            HttpServletRequest request, HttpServletResponse response) {
        return complete(parameters, request, response);
    }

    private ResponseEntity<Void> complete(Map<String, String> parameters, HttpServletRequest request,
            HttpServletResponse response) {
        // SAML devolve o nosso state em `RelayState`; OIDC, em `state`. A diferença é de protocolo e morre
        // aqui.
        var state = parameters.getOrDefault("state", parameters.get("RelayState"));
        var params = new HashMap<>(parameters);

        SsoLoginUseCase.Completion completion;
        try {
            completion = ssoLogin.complete(new SsoLoginUseCase.CallbackCommand(state, params));
        } catch (br.com.brew.brassia.security.application.service.SsoLoginHandler.SsoLinkRefusedException e) {
            // A recusa de vínculo tem mensagem própria na tela porque a providência é específica: entrar
            // pela conta local e vincular o provedor de dentro dela.
            return redirectTo("/login?sso=vinculo-recusado");
        } catch (br.com.brew.brassia.security.domain.InvalidSsoHandshakeException e) {
            // Motivo único para todas as falhas do aperto de mão: dizer qual amarra falhou ensinaria a
            // quem está sondando exatamente o que contornar.
            return redirectTo("/login?sso=falhou");
        }

        var context = sessionContext.resolve(new UserId(completion.userId()), null);
        // O nome vem da conta, não do contexto de sessão — o contexto responde por cervejarias e
        // permissões, e misturar identidade nele criaria duas fontes para o mesmo dado.
        var displayName = users.findById(new UserId(completion.userId()))
                .map(user -> user.displayName().value())
                .orElse("");
        var principal = new SecurityPrincipal(completion.userId(), context.activeBreweryId(),
                displayName, context.permissions());
        // `rotate = true`: sessão nova depois de autenticar, sempre. É o que impede fixação de sessão —
        // um identificador plantado antes do login deixando de valer no instante em que ele vale algo.
        sessionPersister.persist(principal, request, response, true);

        return redirectTo(completion.redirectAfterLogin());
    }

    /**
     * Redireciona para dentro da aplicação.
     *
     * <p>O caminho já foi normalizado pelo domínio ({@code SsoHandshake} só aceita caminho interno), e a
     * base é configuração da instalação — nunca vem da requisição. As duas coisas juntas são o que impede
     * o login de virar um redirecionador aberto.
     */
    private ResponseEntity<Void> redirectTo(String path) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(appBaseUri + path))
                .build();
    }
}
