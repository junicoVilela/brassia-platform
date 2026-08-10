package br.com.brew.brassia.sensor.adapter.inbound.mqtt;

import br.com.brew.brassia.sensor.application.port.inbound.AdapterIngestionCommands;
import br.com.brew.brassia.sensor.application.port.outbound.MqttSubscriptionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClientPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recebe leituras por MQTT e entrega à ingestão (DEB-INT-003).
 *
 * <p><strong>Este adaptador não traduz nada.</strong> A conversão de payload, a idempotência, a qualidade
 * e o atraso já acontecem em {@code AdapterIngestionCommands} sem saber por onde a mensagem chegou — foi
 * exatamente o que a pendência registrou estar pronto. O que existe aqui é transporte: conectar, assinar,
 * e chamar.
 *
 * <p><strong>O dispositivo vem do TÓPICO, nunca do corpo.</strong> É a mesma regra que a ingestão HTTP
 * aplica ao tirar o código da URL: um gateway que pudesse escolher o dispositivo pelo payload gravaria na
 * série de outro aparelho da mesma cervejaria.
 *
 * <p><strong>QoS 1, e não 0 nem 2.</strong> Com 0 uma leitura se perde no reconnect sem ninguém saber; com
 * 2 o custo de handshake não se paga para um dado que já é idempotente do nosso lado. Em 1 a mensagem pode
 * chegar duas vezes — e chegar duas vezes é inofensivo aqui, porque a idempotência está no banco, decidida
 * por {@code messageId} com {@code ON CONFLICT DO NOTHING}.
 */
public final class SensorMqttSubscriber implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(SensorMqttSubscriber.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int QOS_AT_LEAST_ONCE = 1;

    private final MqttSubscriptionRepository subscriptions;
    private final AdapterIngestionCommands ingestion;
    private final List<Connection> connections = new ArrayList<>();

    public SensorMqttSubscriber(MqttSubscriptionRepository subscriptions,
            AdapterIngestionCommands ingestion) {
        this.subscriptions = subscriptions;
        this.ingestion = ingestion;
    }

    private record Connection(MqttAsyncClient client, MqttSubscriptionRepository.Subscription config) {}

    /**
     * Conecta e assina, uma conexão por cervejaria.
     *
     * <p>Uma falha de conexão <strong>não derruba as outras</strong>: o broker de uma cervejaria fora do ar
     * é um problema dela, e propagar isso pararia a ingestão de todas. O erro é registrado e a assinatura
     * daquela cervejaria fica sem cliente até a próxima subida.
     */
    public synchronized void start() {
        for (var config : subscriptions.enabled()) {
            try {
                connections.add(connect(config));
            } catch (MqttException e) {
                // Só o tipo e a cervejaria: a URI do broker carrega usuário e às vezes senha.
                log.warn("assinatura MQTT indisponível para a cervejaria {}: {}",
                        config.breweryId(), e.getClass().getSimpleName());
            }
        }
    }

    private Connection connect(MqttSubscriptionRepository.Subscription config) throws MqttException {
        MqttClientPersistence persistence = new MemoryPersistence();
        var client = new MqttAsyncClient(config.brokerUri(),
                "brassia-" + config.breweryId(), persistence);
        client.setCallback(this);

        var options = new MqttConnectionOptions();
        // Sessão limpa: mensagens acumuladas enquanto estivemos fora chegariam com atraso de horas, e a
        // ingestão as marcaria como atrasadas — ruído que não descreve o processo. O que interessa é o
        // agora; o histórico já está no banco.
        options.setCleanStart(true);
        options.setAutomaticReconnect(true);
        if (config.username() != null && !config.username().isBlank()) {
            options.setUserName(config.username());
            options.setPassword(config.password() == null ? new byte[0]
                    : config.password().getBytes(StandardCharsets.UTF_8));
        }
        client.connect(options).waitForCompletion();
        client.subscribe(config.topicFilter(), QOS_AT_LEAST_ONCE).waitForCompletion();
        log.info("MQTT assinado para a cervejaria {} em {}", config.breweryId(), config.topicFilter());
        return new Connection(client, config);
    }

    /**
     * Reconcilia as conexões com o que está configurado agora.
     *
     * <p><strong>Não é conveniência de teste: é a lacuna óbvia de conectar só na subida.</strong> Sem isso,
     * uma cervejaria que configura o broker hoje só começa a receber leituras no próximo reinício da
     * aplicação — e ninguém liga o serviço de novo para isso.
     *
     * <p>Derruba tudo e reconecta, em vez de calcular a diferença: o número de cervejarias com MQTT é
     * pequeno, a operação é rara, e um algoritmo de diferença aqui teria mais estados do que a coisa que
     * ele evita.
     */
    public synchronized void refresh() {
        stop();
        start();
    }

    public synchronized void stop() {
        for (var connection : connections) {
            try {
                connection.client().disconnect().waitForCompletion();
                connection.client().close();
            } catch (MqttException e) {
                log.debug("desconexão MQTT com erro: {}", e.getClass().getSimpleName());
            }
        }
        connections.clear();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        var connection = connections.stream()
                .filter(c -> c.config().deviceCodeOf(topic) != null)
                .findFirst()
                .orElse(null);
        if (connection == null) {
            return;
        }
        var deviceCode = connection.config().deviceCodeOf(topic);
        try {
            var payload = JSON.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {});
            ingestion.ingest(new AdapterIngestionCommands.Request(
                    connection.config().breweryId(), deviceCode, payload));
        } catch (Exception e) {
            // Uma mensagem malformada NÃO pode derrubar o assinante: o próximo aparelho da fila continua
            // publicando, e um assinante morto perde tudo em silêncio. O erro fica no log, sem o corpo —
            // que pode ser grande e vir de fonte não confiável.
            // O TIPO e a MENSAGEM da exceção, não o corpo recebido: a mensagem diz qual regra recusou
            // (unidade divergente, dispositivo inativo, payload sem campo), e é o que quem opera precisa.
            // O corpo, esse, vem de fonte não confiável e pode ser grande.
            log.warn("mensagem MQTT descartada no tópico {}: {} — {}",
                    topic, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    public void disconnected(MqttDisconnectResponse response) {
        log.info("MQTT desconectado: {}", response.getReturnCode());
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        log.warn("erro MQTT: {}", exception.getClass().getSimpleName());
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        // Só publicamos se fôssemos produtor; aqui somos apenas assinante.
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        if (reconnect) {
            // Reconexão automática não reassina sozinha quando a sessão é limpa: sem isto, o cliente
            // volta conectado e MUDO — o pior estado possível, porque parece saudável.
            connections.stream()
                    .filter(c -> c.client().getCurrentServerURI().equals(serverUri))
                    .forEach(this::resubscribe);
        }
    }

    private void resubscribe(Connection connection) {
        try {
            connection.client().subscribe(connection.config().topicFilter(), QOS_AT_LEAST_ONCE);
        } catch (MqttException e) {
            log.warn("reassinatura MQTT falhou para {}: {}",
                    connection.config().breweryId(), e.getClass().getSimpleName());
        }
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        // Autenticação estendida (AUTH) não é usada; usuário e senha bastam.
    }
}
