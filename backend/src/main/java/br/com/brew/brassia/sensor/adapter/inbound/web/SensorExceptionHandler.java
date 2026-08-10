package br.com.brew.brassia.sensor.adapter.inbound.web;

import br.com.brew.brassia.sensor.domain.InactiveDeviceException;
import br.com.brew.brassia.sensor.domain.UnknownDeviceException;
import br.com.brew.brassia.sensor.domain.UnknownEquipmentException;
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
     * Equipamento informado no cadastro não existe (OBS-INT-001).
     *
     * <p><strong>400 e não 404</strong>, apesar de ser "não encontrado": o recurso da requisição é o
     * dispositivo que está sendo criado, e ele não existe mesmo — o que está errado é um <em>campo do
     * corpo</em>. Responder 404 faria quem integra concluir que a rota está errada e procurar o problema no
     * lugar onde ele não está.
     *
     * <p>O campo vai na resposta porque é a única informação acionável: sem ele, "equipamento inexistente"
     * num corpo com meia dúzia de campos ainda deixa a pessoa adivinhando qual conserta.
     */
    @ExceptionHandler(UnknownEquipmentException.class)
    ProblemDetail handleUnknownEquipment(UnknownEquipmentException ex) {
        var problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "unknown_equipment",
                "O equipamento informado não existe nesta cervejaria.");
        problem.setProperty("field", "equipmentId");
        return problem;
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
