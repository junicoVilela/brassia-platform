-- INT-006: de que jeito cada dispositivo fala.
--
-- O formato é ATRIBUTO DO DISPOSITIVO, não da mensagem. Deixar o payload declarar o próprio formato seria
-- confiar num campo que o firmware preenche — e um firmware atualizado que mudasse a declaração passaria a
-- ser interpretado de outro jeito sem que ninguém decidisse isso. Pelo mesmo motivo a unidade já morava no
-- cadastro desde a V98.
--
-- CANONICAL como default porque é o que os dispositivos existentes (cadastrados antes desta migration) já
-- usam: eles falam pelo endpoint que recebe o formato da casa.
ALTER TABLE sensor_device
    ADD COLUMN payload_format VARCHAR(20) NOT NULL DEFAULT 'CANONICAL';

ALTER TABLE sensor_device
    ADD CONSTRAINT ck_sensor_payload_format
    CHECK (payload_format IN ('CANONICAL', 'ISPINDEL', 'TILT'));
