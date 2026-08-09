-- INT-001: ingestão de leituras de sensor.
--
-- Duas tabelas com naturezas opostas, e a diferença explica quase todas as decisões abaixo. O dispositivo
-- é cadastro: poucas linhas, muda raramente, tem versão e é auditado. A leitura é telemetria: muitas linhas
-- por dia, nunca muda, e o que importa nela é ser gravada uma vez só e ser lida por janela de tempo.

CREATE TABLE sensor_device (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- Identidade EXTERNA do aparelho: é o que o firmware manda e o que está escrito na etiqueta colada no
    -- tanque. Guardado normalizado (maiúsculas) porque "ispindel-01" e "ISPINDEL-01" são o mesmo aparelho
    -- para quem instalou — sem isso viram dois cadastros, duas séries e nenhuma completa.
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    measure VARCHAR(20) NOT NULL,
    -- A unidade é ATRIBUTO DO DISPOSITIVO, não da mensagem. Um firmware atualizado que passasse a mandar
    -- Fahrenheit sem avisar trocaria a escala da série histórica inteira sem nenhum sinal; aqui a mensagem
    -- divergente é recusada, não convertida.
    unit VARCHAR(10) NOT NULL,
    equipment_id UUID REFERENCES equipment (id),
    -- Frequência esperada, em segundos. É a RÉGUA do atraso: 30 s de espera não significam nada num
    -- dispositivo horário e significam uma janela inteira perdida num de 15 s. Nulo = sem régua, e então o
    -- atraso é medido e informado mas não julgado.
    expected_interval_seconds INTEGER,
    status VARCHAR(20) NOT NULL,
    registered_by UUID NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_sensor_measure CHECK (measure IN ('DENSITY', 'TEMPERATURE', 'PRESSURE', 'FLOW')),
    CONSTRAINT ck_sensor_device_status CHECK (status IN ('ACTIVE', 'PAUSED', 'REVOKED')),
    CONSTRAINT ck_sensor_interval CHECK (expected_interval_seconds IS NULL OR expected_interval_seconds > 0),
    -- Um código por cervejaria. É esta restrição que decide o cadastro concorrente do mesmo aparelho —
    -- duas requisições simultâneas passariam as duas por uma consulta prévia feita em código.
    CONSTRAINT uq_sensor_device_code UNIQUE (brewery_id, code)
);

CREATE INDEX ix_sensor_device_equipment ON sensor_device (brewery_id, equipment_id)
    WHERE equipment_id IS NOT NULL;

-- A leitura. IMUTÁVEL — medição está entre o que o AGENTS.md põe fora do alcance de UPDATE e DELETE.
-- Um sensor que se corrige manda outra leitura; a anterior continua sendo o que ele disse na hora.
CREATE TABLE sensor_reading (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    device_id UUID NOT NULL REFERENCES sensor_device (id),
    -- Identidade da MENSAGEM, carimbada pelo dispositivo. Não é hash do conteúdo de propósito: um hash
    -- trataria duas medições legitimamente idênticas — sensor parado, mesmo segundo, mesmo valor — como
    -- repetição, e descartaria uma leitura verdadeira. A repetição a reconhecer é a de transporte (o
    -- dispositivo não recebeu o ACK e reenviou), e só ele sabe que é o mesmo envio.
    message_id VARCHAR(80) NOT NULL,
    measure VARCHAR(20) NOT NULL,
    -- NUMERIC, nunca float: o AGENTS.md proíbe ponto flutuante para persistência de precisão, e uma
    -- densidade de 1.0483 perde significado no último dígito, que é justamente onde mora a diferença entre
    -- fermentação terminada e travada.
    value NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(10) NOT NULL,
    -- OS DOIS RELÓGIOS. Guardar só o nosso transformaria leituras represadas por queda de rede numa rajada
    -- de medições simultâneas que nunca existiu; guardar só o do dispositivo deixaria um relógio errado
    -- reescrever a história sem sobrar nada com que comparar.
    measured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    quality VARCHAR(20) NOT NULL,
    quality_reason VARCHAR(200),
    -- Atraso derivado na escrita e GRAVADO, não calculado na leitura. O motivo é que a régua muda: se
    -- alguém reconfigurar o intervalo esperado do dispositivo amanhã, uma leitura de hoje não pode passar a
    -- ser "atrasada" retroativamente — ela foi julgada contra a régua que valia quando chegou.
    delay_seconds BIGINT NOT NULL,
    late BOOLEAN NOT NULL,
    CONSTRAINT ck_sensor_reading_measure CHECK (measure IN ('DENSITY', 'TEMPERATURE', 'PRESSURE', 'FLOW')),
    CONSTRAINT ck_sensor_reading_quality CHECK (quality IN ('GOOD', 'OUT_OF_RANGE', 'FUTURE_CLOCK')),
    -- Qualidade boa não tem motivo; qualidade ruim sempre tem. Sem isso, uma leitura sinalizada sem
    -- explicação obrigaria quem investiga a adivinhar o que houve.
    CONSTRAINT ck_sensor_reading_reason CHECK (
        (quality = 'GOOD' AND quality_reason IS NULL)
        OR (quality <> 'GOOD' AND quality_reason IS NOT NULL)),
    -- Medida do futuro produz atraso negativo, e ele é preservado: normalizar para zero apagaria a
    -- evidência do relógio adiantado, que é o que FUTURE_CLOCK precisa que fique visível. Por isso não há
    -- CHECK de delay_seconds >= 0. O que há é a garantia de que negativo nunca é "atrasado".
    CONSTRAINT ck_sensor_reading_late CHECK (delay_seconds >= 0 OR late = false),
    -- A RESTRIÇÃO QUE FAZ A IDEMPOTÊNCIA. Ela, e não uma consulta prévia em código: verificar antes e
    -- inserir depois deixa uma janela entre as duas operações, e é exatamente nela que cai o reenvio de um
    -- gateway que despachou a mesma mensagem duas vezes em milissegundos — o cenário para o qual a
    -- idempotência existe.
    CONSTRAINT uq_sensor_reading_message UNIQUE (device_id, message_id)
);

-- O índice da consulta real: leituras de um dispositivo numa janela, mais recentes primeiro.
CREATE INDEX ix_sensor_reading_window ON sensor_reading (brewery_id, device_id, measured_at DESC);

-- Índice parcial das sinalizadas. É pequeno (a maioria das leituras é GOOD) e serve à pergunta que se faz
-- de verdade: "o que deu errado nos sensores esta semana?".
CREATE INDEX ix_sensor_reading_flagged ON sensor_reading (brewery_id, device_id, measured_at DESC)
    WHERE quality <> 'GOOD' OR late = true;

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000027', NULL, 'sensor', 'Sensores', 32)
ON CONFLICT (id) DO NOTHING;

-- Quatro alçadas, e a separação entre elas é deliberada.
--
-- `sensor.reading.ingest` é a do DISPOSITIVO, não a de uma pessoa: ela pertence à conta de serviço com que
-- o gateway autentica. Uma pessoa não precisa dela, e um gateway comprometido que a tivesse junto com as
-- outras poderia revogar os sensores que denunciariam o próprio comportamento.
--
-- `sensor.device.revoke` é separada de `sensor.device.manage` porque dizer que uma série deixou de valer é
-- ato de confiança, não de instalação — quem parafusa o sensor no tanque não decide que o histórico dele
-- é suspeito.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000120', '11111111-0000-0000-0000-000000000027',
     'sensor.reading.ingest', 'Enviar leitura de sensor', false),
    ('22222222-0000-0000-0000-000000000121', '11111111-0000-0000-0000-000000000027',
     'sensor.reading.read', 'Consultar leituras de sensor', false),
    ('22222222-0000-0000-0000-000000000122', '11111111-0000-0000-0000-000000000027',
     'sensor.device.manage', 'Cadastrar e pausar dispositivo', false),
    ('22222222-0000-0000-0000-000000000123', '11111111-0000-0000-0000-000000000027',
     'sensor.device.revoke', 'Revogar dispositivo — descontinua a identidade', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sensor.reading.ingest', 'sensor.reading.read',
                 'sensor.device.manage', 'sensor.device.revoke')
ON CONFLICT (group_id, permission_id) DO NOTHING;
