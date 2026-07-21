package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fluxo real de CSRF do SPA num container servlet (RANDOM_PORT), via HttpClient
 * do JDK: obtém o cookie XSRF-TOKEN e reenvia o valor CRU no header. Regressão do
 * handler XOR padrão, que rejeitava o token cru com 403 (login do frontend quebrado).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CsrfLoginIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void loginAcceptsRawCookieCsrfToken() throws Exception {
        var csrf = http.send(HttpRequest.newBuilder(uri("/api/v1/security/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(csrf.statusCode()).isEqualTo(204);

        var xsrf = extractCookie(csrf.headers().allValues("set-cookie"), "XSRF-TOKEN");
        assertThat(xsrf).as("cookie XSRF-TOKEN").isNotBlank();

        var login = http.send(HttpRequest.newBuilder(uri("/api/v1/security/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + xsrf)
                        .header("X-XSRF-TOKEN", xsrf)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).as("login body: %s", login.body()).isEqualTo(200);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String extractCookie(List<String> setCookies, String name) {
        return setCookies.stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> {
                    var value = c.substring((name + "=").length());
                    var semi = value.indexOf(';');
                    return semi < 0 ? value : value.substring(0, semi);
                })
                .findFirst().orElse("");
    }
}
