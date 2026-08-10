package br.com.brew.brassia.integration.adapter.outbound.http;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Recusa destinos que apontam para dentro da própria infraestrutura (REL-003).
 *
 * <p><strong>O problema que isto fecha é SSRF.</strong> O endereço de um webhook é escolhido por quem
 * cadastra, e quem faz a requisição é o servidor — de dentro da rede. Sem esta checagem, alguém com
 * permissão de cadastrar webhook consegue usar a plataforma como sonda: aponta para
 * {@code https://10.0.0.5/admin} ou para o serviço de metadados da nuvem e lê o resultado pelo
 * <em>status</em> que fica gravado na entrega. Não precisa nem ver o corpo — o código de resposta e a
 * diferença entre "recusou conexão" e "respondeu 401" já mapeiam a rede interna.
 *
 * <p><strong>A checagem é no envio, e não só no cadastro.</strong> Validar apenas na criação seria
 * contornável por DNS: o nome cadastrado resolve para um endereço público hoje e para
 * {@code 127.0.0.1} amanhã — o ataque clássico de <em>rebinding</em>. Como o custo é uma resolução de
 * nome que a requisição faria de qualquer jeito, não há razão para confiar na validação antiga.
 *
 * <p><strong>Recusa se QUALQUER endereço resolvido for interno.</strong> Um nome que devolve um endereço
 * público e um privado seria aceito por uma checagem que olha só o primeiro, e a escolha de qual usar não
 * é nossa — é da pilha de rede.
 */
final class InternalAddressGuard {

    private InternalAddressGuard() {
    }

    /**
     * @return o motivo da recusa, ou vazio quando o destino é externo e resolvível
     */
    static Optional<String> reasonToRefuse(URI endpoint) {
        var host = endpoint.getHost();
        if (host == null || host.isBlank()) {
            return Optional.of("destino sem host");
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // Nome que não resolve não é recusa de segurança: é destino inalcançável, e quem trata isso
            // é o backoff da entrega. Deixar passar aqui mantém as duas causas distinguíveis.
            return Optional.empty();
        }
        for (var address : resolved) {
            var kind = internalKindOf(address);
            if (kind != null) {
                // A mensagem não repete o endereço resolvido: ela já é a resposta do sonda que a
                // checagem existe para impedir. Basta dizer que é interno.
                return Optional.of("destino resolve para endereço " + kind);
            }
        }
        return Optional.empty();
    }

    private static String internalKindOf(InetAddress address) {
        if (address.isLoopbackAddress()) {
            return "de loopback";
        }
        if (address.isAnyLocalAddress()) {
            return "curinga local";
        }
        if (address.isLinkLocalAddress()) {
            // Cobre 169.254.0.0/16, onde vive o serviço de metadados das nuvens — o alvo mais valioso
            // de um SSRF, porque costuma entregar credencial sem autenticação.
            return "link-local";
        }
        if (address.isSiteLocalAddress()) {
            return "de rede privada";
        }
        if (address.isMulticastAddress()) {
            return "multicast";
        }
        // IPv6 unique local (fc00::/7) não é coberto por isSiteLocalAddress.
        var bytes = address.getAddress();
        if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
            return "IPv6 de uso local";
        }
        // Carrier-grade NAT (100.64.0.0/10): não é privado pela definição clássica, mas também não é
        // internet pública, e é onde vivem redes de infraestrutura de provedor.
        if (bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 0x40) {
            return "de faixa reservada de provedor";
        }
        return null;
    }
}
