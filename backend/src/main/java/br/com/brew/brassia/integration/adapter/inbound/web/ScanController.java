package br.com.brew.brassia.integration.adapter.inbound.web;

import br.com.brew.brassia.integration.domain.ScanReference;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolve um código lido (INT-003).
 *
 * <p><strong>Este endpoint é um roteador, não uma porta de entrada.</strong> Ele interpreta o que estava
 * escrito no código e diz para onde ir — depois de exigir a permissão do tipo apontado. O que ele
 * deliberadamente <em>não</em> faz é carregar o recurso: quem responde pelo equipamento, pelo lote, pela OP
 * e pela embalagem são os módulos donos deles, e é lá que a cervejaria e o estado são verificados. Duplicar
 * essa verificação aqui criaria uma segunda autoridade sobre a mesma pergunta — e duas autoridades
 * divergem com o tempo.
 *
 * <p><strong>A ordem importa e é o critério da história.</strong> A sessão é exigida pelo filtro de
 * segurança <em>antes</em> de qualquer coisa; a permissão é verificada <em>depois</em> de interpretar o
 * código, porque só aí se sabe qual permissão é. Ler o código não é ganhar acesso: é fazer uma pergunta que
 * ainda precisa ser autorizada.
 *
 * <p>Um código legível por alguém sem a alçada responde <strong>403</strong> — e não uma tela vazia ou um
 * "não encontrado". A pessoa apontou a câmera para uma etiqueta real; a resposta honesta é que ela não pode
 * ver aquilo, não que aquilo não existe.
 */
@RestController
@RequestMapping("/api/v1/integration/scan")
final class ScanController {

    /**
     * Resolve o conteúdo lido.
     *
     * <p>Não há {@code POST} nem corpo: a leitura não altera nada, e um {@code GET} é o que permite o QR
     * conter um link que o aplicativo de câmera do telefone abre sozinho — sem instalar nada, sem
     * biblioteca de leitura no nosso lado.
     */
    @GetMapping
    Map<String, String> resolve(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam("code") String code) {
        var reference = ScanReference.parse(code);

        // A cervejaria ativa é exigida antes da permissão: sem tenant não há alçada que valha.
        principal.requireBrewery();
        principal.requirePermission(reference.target().requiredPermission());

        return Map.of(
                "type", reference.target().segment(),
                "identifier", reference.identifier(),
                "route", reference.target().route());
    }
}
