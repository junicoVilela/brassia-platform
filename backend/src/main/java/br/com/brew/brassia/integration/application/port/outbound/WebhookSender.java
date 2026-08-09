package br.com.brew.brassia.integration.application.port.outbound;

import java.net.URI;
import java.util.Map;

/**
 * A fronteira HTTP de saída (INT-002).
 *
 * <p>Existe como porta por um motivo concreto e não por simetria: <strong>o teste precisa exercitar o
 * retry sem depender de um servidor de verdade cair.</strong> Falha de rede, timeout e 500 são justamente
 * o que a história pede para tratar, e são o que menos se consegue provocar de forma confiável contra um
 * endereço real.
 */
public interface WebhookSender {

    Result send(URI endpoint, Map<String, String> headers, String body);

    /**
     * O que aconteceu na tentativa.
     *
     * <p>{@code status} é nulo quando não houve resposta — timeout, DNS, conexão recusada. A distinção
     * importa: "o destino respondeu 500" e "o destino não respondeu" apontam para lados diferentes do
     * problema.
     */
    record Result(boolean success, Integer status, String error) {

        public static Result ok(int status) {
            return new Result(true, status, null);
        }

        public static Result rejected(int status, String error) {
            return new Result(false, status, error);
        }

        public static Result unreachable(String error) {
            return new Result(false, null, error);
        }
    }
}
