package br.com.brew.brassia.integration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Recusa de destinos internos no webhook (REL-003).
 *
 * <p>O que estes testes fixam: a plataforma não pode ser usada como sonda da própria rede. O alvo mais
 * valioso é o serviço de metadados da nuvem em {@code 169.254.169.254}, que costuma entregar credencial
 * sem autenticação — e é alcançável de dentro por qualquer requisição que o servidor faça.
 *
 * <p>Acessado por reflexão porque a classe é de pacote e não deve virar API só para ser testada.
 */
class InternalAddressGuardTest {

    private static Optional<String> refuse(String url) throws Exception {
        var type = Class.forName(
                "br.com.brew.brassia.integration.adapter.outbound.http.InternalAddressGuard");
        Method m = type.getDeclaredMethod("reasonToRefuse", URI.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        var result = (Optional<String>) m.invoke(null, URI.create(url));
        return result;
    }

    @Test
    @DisplayName("METADADOS DA NUVEM é recusado")
    void metadadosDaNuvem() throws Exception {
        // O alvo clássico de SSRF: link-local, sem autenticação, com credencial atrás.
        assertThat(refuse("https://169.254.169.254/latest/meta-data/")).isPresent();
    }

    @Test
    @DisplayName("loopback é recusado, por nome e por número")
    void loopback() throws Exception {
        assertThat(refuse("https://127.0.0.1/admin")).isPresent();
        assertThat(refuse("https://localhost:8443/actuator")).isPresent();
        assertThat(refuse("https://[::1]/admin")).isPresent();
    }

    @Test
    @DisplayName("redes privadas são recusadas nas três faixas")
    void redesPrivadas() throws Exception {
        assertThat(refuse("https://10.0.0.5/internal")).isPresent();
        assertThat(refuse("https://192.168.1.10/")).isPresent();
        assertThat(refuse("https://172.16.0.9/")).isPresent();
    }

    @Test
    @DisplayName("IPv6 de uso local e faixa de provedor também")
    void outrasFaixas() throws Exception {
        assertThat(refuse("https://[fd00::1]/")).isPresent();
        assertThat(refuse("https://100.64.0.1/")).isPresent();
    }

    @Test
    @DisplayName("A MENSAGEM NÃO DEVOLVE O ENDEREÇO RESOLVIDO")
    void mensagemNaoVazaResolucao() throws Exception {
        // A mensagem fica gravada na entrega e é legível por quem cadastrou. Repetir o IP resolvido
        // seria entregar exatamente a resposta que a checagem existe para negar.
        var motivo = refuse("https://10.0.0.5/internal").orElseThrow();

        assertThat(motivo).doesNotContain("10.0.0.5");
        assertThat(motivo).contains("privada");
    }

    @Test
    @DisplayName("endereço público passa")
    void publicoPassa() throws Exception {
        assertThat(refuse("https://93.184.216.34/hook")).isEmpty();
    }

    @Test
    @DisplayName("NOME QUE NÃO RESOLVE NÃO É RECUSA DE SEGURANÇA")
    void nomeInexistentePassa() throws Exception {
        // É destino inalcançável, e quem trata isso é o backoff. Confundir os dois tornaria
        // indistinguível "o DNS caiu" de "tentaram sondar a rede interna".
        assertThat(refuse("https://nao-existe-mesmo-" + System.nanoTime() + ".invalid/hook")).isEmpty();
    }

    @Test
    @DisplayName("destino sem host é recusado")
    void semHost() throws Exception {
        assertThat(refuse("https:///caminho")).isPresent();
    }
}
