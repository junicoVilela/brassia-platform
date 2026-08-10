package br.com.brew.brassia.sensor.adapter.outbound.persistence;

import br.com.brew.brassia.sensor.application.port.outbound.MqttSubscriptionRepository;
import br.com.brew.brassia.sensor.domain.PayloadFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Assinaturas MQTT em PostgreSQL (DEB-INT-003). */
@Repository
class JdbcMqttSubscriptionRepository implements MqttSubscriptionRepository {

    private final JdbcClient jdbc;

    JdbcMqttSubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Subscription> enabled() {
        return jdbc.sql("""
                SELECT brewery_id, broker_uri, topic_prefix, username, password, payload_format
                FROM sensor_mqtt_subscription
                WHERE enabled
                ORDER BY brewery_id
                """)
                .query((rs, n) -> new Subscription(
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("broker_uri"),
                        rs.getString("topic_prefix"),
                        rs.getString("username"),
                        rs.getString("password"),
                        PayloadFormat.valueOf(rs.getString("payload_format"))))
                .list();
    }
}
