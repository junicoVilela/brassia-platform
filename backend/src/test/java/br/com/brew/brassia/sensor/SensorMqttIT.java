package br.com.brew.brassia.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Ingestão por MQTT contra um broker real (DEB-INT-003).
 *
 * <p><strong>Broker de verdade, e o critério de remoção exigia isso por um motivo.</strong> Entrega,
 * reentrega e recusa de credencial são comportamentos do <em>protocolo</em>: um dublê que chama o
 * callback direto prova que o callback funciona, não que a mensagem atravessa a rede, o QoS e a sessão.
 *
 * <p>O que se verifica aqui é o que só o transporte real mostra: que o dispositivo é decidido pelo
 * <strong>tópico</strong>, que a mesma mensagem entregue duas vezes (QoS 1) não vira duas leituras, e que
 * o assinante sobrevive a uma mensagem malformada em vez de morrer calado.
 */
@SpringBootTest
@Testcontainers
class SensorMqttIT {

    private static final String TOPIC_PREFIX = "brassia/leituras";
    private static final String DEVICE_CODE = "TANQUE-MQTT-1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Container
    static HiveMQContainer broker =
            new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2024.3"));

    static UUID breweryId;
    static UUID deviceId;

    @Autowired JdbcClient jdbc;
    @Autowired br.com.brew.brassia.sensor.adapter.inbound.mqtt.SensorMqttSubscriber subscriber;

    @BeforeEach
    void setUp() {
        breweryId = jdbc.sql("SELECT id FROM brewery ORDER BY name LIMIT 1").query(UUID.class).single();
        garantirAssinatura();
        deviceId = garantirDispositivo();
        // O assinante conecta na subida do bean, quando ainda não havia assinatura no banco. `refresh()`
        // reconcilia — e existe porque configurar um broker novo em produção não pode exigir reinício.
        subscriber.refresh();
    }

    @Test
    @DisplayName("mensagem publicada no tópico do dispositivo vira leitura")
    void entregaBasica() {
        publicar(DEVICE_CODE, canonico("r-" + UUID.randomUUID(), "18.5"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(leiturasDo(deviceId)).isPositive());
    }

    @Test
    @DisplayName("QoS 1: a MESMA mensagem entregue duas vezes não vira duas leituras")
    void reentregaNaoDuplica() {
        // QoS 1 entrega ao menos uma vez, então duplicata é esperada — não é anomalia. A idempotência
        // está no banco, decidida pelo externalReadingId, e é ela que o teste exercita.
        var readingId = "r-" + UUID.randomUUID();
        var antes = leiturasDo(deviceId);

        publicar(DEVICE_CODE, canonico(readingId, "19.0"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(leiturasDo(deviceId)).isEqualTo(antes + 1));

        publicar(DEVICE_CODE, canonico(readingId, "19.0"));
        // Espera ativa para dar chance de a duplicata ser gravada, se a idempotência falhar.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(leiturasDo(deviceId)).isEqualTo(antes + 1));
    }

    @Test
    @DisplayName("O DISPOSITIVO VEM DO TÓPICO, não do corpo")
    void dispositivoVemDoTopico() {
        // O payload aponta para outro aparelho. Se o corpo mandasse, a leitura entraria na série errada —
        // e um gateway comprometido escreveria em qualquer dispositivo da cervejaria.
        var outro = garantirDispositivo("TANQUE-MQTT-2");
        var antesOutro = leiturasDo(outro);
        var antesAlvo = leiturasDo(deviceId);

        publicar(DEVICE_CODE, """
                {"deviceId":"TANQUE-MQTT-2","externalReadingId":"r-%s","measuredAt":"%s",
                 "temperatureC":21.0}
                """.formatted(UUID.randomUUID(), java.time.Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(leiturasDo(deviceId)).isEqualTo(antesAlvo + 1));
        assertThat(leiturasDo(outro))
                .as("o aparelho citado no corpo não pode receber a leitura")
                .isEqualTo(antesOutro);
    }

    @Test
    @DisplayName("MENSAGEM MALFORMADA não derruba o assinante")
    void malformadaNaoDerruba() {
        // Um assinante morto perde tudo em silêncio, que é o pior estado possível: parece saudável.
        publicar(DEVICE_CODE, "isto não é json");

        var readingId = "r-" + UUID.randomUUID();
        var antes = leiturasDo(deviceId);
        publicar(DEVICE_CODE, canonico(readingId, "20.0"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(leiturasDo(deviceId))
                        .as("o assinante segue vivo depois da mensagem inválida")
                        .isEqualTo(antes + 1));
    }

    // --- infraestrutura ---

    /** Publica com o MESMO cliente do assinante: um protocolo só, exercitado dos dois lados. */
    private void publicar(String deviceCode, String payload) {
        try {
            var client = new org.eclipse.paho.mqttv5.client.MqttAsyncClient(
                    "tcp://" + broker.getHost() + ":" + broker.getMqttPort(),
                    "publicador-" + UUID.randomUUID(),
                    new org.eclipse.paho.mqttv5.client.persist.MemoryPersistence());
            client.connect().waitForCompletion();
            var message = new org.eclipse.paho.mqttv5.common.MqttMessage(
                    payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish(TOPIC_PREFIX + "/" + deviceCode, message).waitForCompletion();
            client.disconnect().waitForCompletion();
            client.close();
        } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
            throw new IllegalStateException("falha ao publicar no broker de teste", e);
        }
    }

    /**
     * Payload canônico.
     *
     * <p>O {@code deviceId} é obrigatório no formato e serve para CONFERÊNCIA — quem escolhe o dispositivo
     * é o tópico. O teste `dispositivoVemDoTopico` existe justamente para provar que os dois podem
     * divergir e que o tópico vence.
     */
    private static String canonico(String readingId, String temperatura) {
        return """
                {"deviceId":"%s","externalReadingId":"%s","measuredAt":"%s","temperatureC":%s}
                """.formatted(DEVICE_CODE, readingId, java.time.Instant.now(), temperatura);
    }

    private int leiturasDo(UUID device) {
        return jdbc.sql("SELECT count(*) FROM sensor_reading WHERE device_id = :device")
                .param("device", device).query(Integer.class).single();
    }

    private void garantirAssinatura() {
        jdbc.sql("""
                INSERT INTO sensor_mqtt_subscription (brewery_id, broker_uri, topic_prefix,
                        payload_format, enabled, created_by)
                VALUES (:brewery, :uri, :prefix, 'CANONICAL', true, :by)
                ON CONFLICT (brewery_id) DO UPDATE SET broker_uri = EXCLUDED.broker_uri
                """)
                .param("brewery", breweryId)
                .param("uri", "tcp://localhost:" + broker.getMqttPort())
                .param("prefix", TOPIC_PREFIX)
                .param("by", UUID.randomUUID())
                .update();
    }

    private UUID garantirDispositivo() {
        return garantirDispositivo(DEVICE_CODE);
    }

    private UUID garantirDispositivo(String code) {
        var existente = jdbc.sql("SELECT id FROM sensor_device WHERE brewery_id = :b AND code = :c")
                .param("b", breweryId).param("c", code).query(UUID.class).optional();
        if (existente.isPresent()) {
            return existente.get();
        }
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO sensor_device (id, brewery_id, code, name, measure, unit,
                        expected_interval_seconds, status, registered_at, registered_by)
                VALUES (:id, :brewery, :code, :name, 'TEMPERATURE', 'C', 300, 'ACTIVE', now(), :by)
                """)
                .param("id", id).param("brewery", breweryId).param("code", code)
                .param("name", "Sensor MQTT " + code).param("by", UUID.randomUUID())
                .update();
        return id;
    }
}
