package br.com.brew.brassia.sensor.application.port.outbound;

import br.com.brew.brassia.sensor.domain.SensorReading;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência das leituras (INT-001). */
public interface ReadingRepository {

    /**
     * Insere a leitura, ou não insere nada se aquela mensagem já foi gravada.
     *
     * <p><strong>A decisão de idempotência é do banco, não de uma consulta anterior.</strong> Verificar
     * antes e inserir depois deixa uma janela entre as duas operações — e é exatamente nela que cai o
     * reenvio de um gateway que mandou a mesma mensagem duas vezes em milissegundos. A restrição única
     * decide, e este método devolve o que aconteceu.
     *
     * @return {@code true} quando a leitura entrou; {@code false} quando já existia.
     */
    boolean insertIfAbsent(SensorReading reading);

    Optional<SensorReading> byMessageId(UUID breweryId, UUID deviceId, String messageId);

    List<SensorReading> inWindow(UUID breweryId, UUID deviceId, Instant from, Instant to, int limit);
}
