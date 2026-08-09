package br.com.brew.brassia.integration.adapter.outbound.http;

import br.com.brew.brassia.integration.application.port.outbound.WebhookSender;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Entrega o webhook por HTTP (INT-002).
 *
 * <p><strong>Timeouts curtos, e curtos de propósito.</strong> Um destino lento não pode segurar a janela
 * do despachante: cinquenta entregas por rodada com dez segundos de espera cada seriam oito minutos preso
 * num destino que não responde, enquanto as outras cervejarias esperam. A entrega que estourou o tempo
 * volta pelo backoff — não se perde, só não atrapalha.
 *
 * <p><strong>Redirecionamento é recusado.</strong> {@code NEVER} não é conservadorismo: seguir um 302
 * mandaria o corpo assinado — e os cabeçalhos — para um endereço que ninguém cadastrou e que a assinatura
 * do destino original não cobre. Um destino comprometido poderia redirecionar os eventos da cervejaria
 * para onde quisesse.
 */
@Component
class HttpWebhookSender implements WebhookSender {

    private final HttpClient client;
    private final Duration requestTimeout;

    HttpWebhookSender(
            @Value("${brassia.integration.connect-timeout:5s}") Duration connectTimeout,
            @Value("${brassia.integration.request-timeout:10s}") Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Result send(URI endpoint, Map<String, String> headers, String body) {
        try {
            var builder = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);

            // A resposta é descartada (discarding): o corpo de um webhook não nos diz nada e pode ser
            // enorme. O que importa é o status.
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            var status = response.statusCode();

            // 2xx é aceite. Qualquer outra coisa é falha e entra no backoff — inclusive 3xx, que aqui
            // significa "o destino quer que a gente vá para outro lugar" e nós não vamos.
            return status >= 200 && status < 300
                    ? Result.ok(status)
                    : Result.rejected(status, "destino respondeu " + status);
        } catch (IOException e) {
            // Timeout, DNS, conexão recusada, TLS. Sem status: o destino não respondeu, e a distinção
            // entre "respondeu 500" e "não respondeu" aponta lados diferentes do problema.
            //
            // Só o TIPO do erro é registrado, nunca a mensagem: ela pode conter a URL inteira, e o
            // caminho de um webhook às vezes carrega token.
            return Result.unreachable(e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.unreachable("interrompido");
        }
    }
}
