package br.com.brew.brassia.sensor.application.port.outbound;

import br.com.brew.brassia.sensor.domain.PayloadFormat;
import java.util.List;
import java.util.UUID;

/** Assinaturas MQTT configuradas, uma por cervejaria (DEB-INT-003). */
public interface MqttSubscriptionRepository {

    List<Subscription> enabled();

    /**
     * @param topicPrefix o sufixo do tópico é o CÓDIGO DO DISPOSITIVO. É ele que decide em qual série a
     *                    leitura entra — nunca o conteúdo da mensagem, pelo mesmo motivo que a ingestão
     *                    HTTP tira o código da URL.
     */
    record Subscription(UUID breweryId, String brokerUri, String topicPrefix, String username,
            String password, PayloadFormat payloadFormat) {

        /** O filtro que o cliente assina: tudo abaixo do prefixo, um nível. */
        public String topicFilter() {
            return topicPrefix.endsWith("/") ? topicPrefix + "+" : topicPrefix + "/+";
        }

        /** Extrai o código do dispositivo de um tópico concreto. */
        public String deviceCodeOf(String topic) {
            var base = topicPrefix.endsWith("/") ? topicPrefix : topicPrefix + "/";
            return topic.startsWith(base) ? topic.substring(base.length()) : null;
        }
    }
}
