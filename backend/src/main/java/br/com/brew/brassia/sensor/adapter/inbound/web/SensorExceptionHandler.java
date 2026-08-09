package br.com.brew.brassia.sensor.adapter.inbound.web;

import br.com.brew.brassia.sensor.domain.InactiveDeviceException;
import br.com.brew.brassia.sensor.domain.UnknownDeviceException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas dos sensores (INT-001). O {@code @Order} vence o catch-all do advice global. */
@Order(0)
@RestControllerAdvice
class SensorExceptionHandler {

    /**
     * Dispositivo desconhecido.
     *
     * <p>404, e a mesma resposta para "não existe" e "é de outra cervejaria": distinguir as duas
     * responderia a pergunta "este código existe em algum lugar do sistema?" para quem não deveria poder
     * fazê-la. O código enviado volta na resposta porque quem configura um gateway precisa ver o que
     * mandou — normalmente o erro é uma letra trocada na etiqueta.
     */
    @ExceptionHandler(UnknownDeviceException.class)
    ProblemDetail handleUnknownDevice(UnknownDeviceException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_sensor_device",
                "Este dispositivo não está cadastrado nesta cervejaria.");
    }

    /**
     * Dispositivo pausado ou revogado.
     *
     * <p>409 e não 403: a credencial está correta e a permissão existe — o que mudou foi o estado do
     * dispositivo. 403 mandaria quem opera procurar o problema nas permissões, que é o lugar errado.
     *
     * <p>O estado vai na resposta porque as duas providências são opostas: pausado volta com um comando,
     * revogado exige cadastrar outro dispositivo.
     */
    @ExceptionHandler(InactiveDeviceException.class)
    ProblemDetail handleInactiveDevice(InactiveDeviceException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "sensor_device_inactive",
                "Este dispositivo não está aceitando leituras.");
        problem.setProperty("status", ex.status().name());
        return problem;
    }
}
