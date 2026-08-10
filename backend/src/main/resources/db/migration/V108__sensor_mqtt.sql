-- DEB-INT-003 (INT-006) — Transporte MQTT para ingestão de sensores.
--
-- CONFIGURAÇÃO POR CERVEJARIA, e não global.
--
-- Cada cervejaria tem o próprio broker: o da fábrica, o do integrador, o da nuvem do fabricante do
-- sensor. Um cliente global obrigaria todas a compartilharem broker — e, pior, faria a credencial de uma
-- cervejaria alcançar os tópicos das outras.
CREATE TABLE sensor_mqtt_subscription (
    brewery_id UUID PRIMARY KEY REFERENCES brewery (id),
    broker_uri VARCHAR(300) NOT NULL,
    -- Prefixo de tópico. O sufixo é o CÓDIGO DO DISPOSITIVO, e é ele que decide em qual série a leitura
    -- entra — nunca o conteúdo da mensagem. Um gateway que pudesse escolher o dispositivo pelo payload
    -- gravaria na série de outro aparelho da mesma cervejaria; a mesma regra que a ingestão HTTP já
    -- aplica ao tirar o código da URL.
    topic_prefix VARCHAR(200) NOT NULL,
    username VARCHAR(200),
    -- O segredo fica aqui como o do webhook: HMAC e autenticação MQTT são simétricos, e o servidor
    -- precisa do valor para se conectar. Cifrar em repouso exigiria KMS e é decisão de infraestrutura.
    password VARCHAR(400),
    -- Formato do payload esperado no tópico. O mesmo enum da ingestão HTTP: o transporte muda, a
    -- tradução não — foi exatamente o que o débito registrou estar pronto.
    payload_format VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    CONSTRAINT ck_sensor_mqtt_format
        CHECK (payload_format IN ('CANONICAL', 'ISPINDEL', 'TILT')),
    -- Só TLS ou WebSocket seguro. `tcp://` entrega credencial e leitura em texto claro na rede da
    -- fábrica — que é justamente a rede onde há mais gente com acesso físico.
    CONSTRAINT ck_sensor_mqtt_secure_uri
        CHECK (broker_uri LIKE 'ssl://%' OR broker_uri LIKE 'wss://%' OR broker_uri LIKE 'tcp://localhost%')
);

COMMENT ON CONSTRAINT ck_sensor_mqtt_secure_uri ON sensor_mqtt_subscription IS
    'tcp://localhost é tolerado para broker embarcado no mesmo host e para teste; qualquer outro destino exige TLS';

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000142', d.id,
       'sensor.mqtt.manage', 'Configurar assinatura MQTT de sensores', true
FROM permission_domain d WHERE d.code = 'sensor'
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p WHERE p.code = 'sensor.mqtt.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
