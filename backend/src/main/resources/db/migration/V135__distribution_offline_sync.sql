-- MOB-001 — sincronização do aplicativo de distribuição: idempotência e conflito explícito.
--
-- O IDENTIFICADOR É DO APARELHO, E NÃO DO SERVIDOR. É o que torna o reenvio seguro: offline não há como
-- pedir um número, e sem um id que o dispositivo gere sozinho, "sincronizar" duas vezes num sinal ruim
-- registra duas entregas para o mesmo cliente.
CREATE TABLE distribution_sync_operation (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- Gerado no aparelho, antes de o servidor saber que a operação existe.
    client_operation_id UUID NOT NULL,
    device_id UUID NOT NULL,
    load_id UUID NOT NULL REFERENCES distribution_load (id),
    stop_id UUID NOT NULL REFERENCES distribution_load_stop (id),
    -- DUAS HORAS, e as duas importam. `occurred_at` é do aparelho: quando a cerveja desceu.
    -- `received_at` é do servidor: quando a informação chegou. Usar a do servidor para o fato colocaria
    -- toda entrega offline no momento em que o caminhão voltou ao depósito — e ninguém entregou nada no
    -- pátio às seis da tarde.
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    -- A ordem é a do APARELHO: aplicar fora dela entregaria antes de despachar.
    sequence INTEGER NOT NULL,
    status VARCHAR(12) NOT NULL,
    -- A prova criada, quando a operação entrou. No reenvio, é ela que volta — e não uma nova.
    result_id UUID,
    reason VARCHAR(500),
    CONSTRAINT ck_sync_status CHECK (status IN ('APPLIED', 'DUPLICATE', 'CONFLICTED', 'REJECTED')),
    CONSTRAINT ck_sync_sequence CHECK (sequence >= 1),
    -- Recusa e conflito precisam de motivo: sem ele o entregador fica com um item vermelho na tela e
    -- nada a fazer.
    CONSTRAINT ck_sync_reason CHECK (
        status IN ('APPLIED', 'DUPLICATE') OR length(btrim(coalesce(reason, ''))) > 0),
    -- Aplicada tem resultado; o resto pode não ter.
    CONSTRAINT ck_sync_result CHECK (status <> 'APPLIED' OR result_id IS NOT NULL)
);

-- A IDEMPOTÊNCIA MORA AQUI, e não numa checagem. Duas requisições simultâneas do mesmo aparelho — o
-- retry automático do aplicativo enquanto o sinal vai e volta — passariam por qualquer SELECT prévio.
-- Por dispositivo, e não global: dois aparelhos podem sortear o mesmo UUID sem que isso signifique nada.
CREATE UNIQUE INDEX ux_sync_operation ON distribution_sync_operation (device_id, client_operation_id);

-- A fila de quem precisa decidir: conflitos abertos, do mais antigo para o mais recente.
CREATE INDEX ix_sync_conflicts ON distribution_sync_operation (brewery_id, received_at)
    WHERE status = 'CONFLICTED';

CREATE INDEX ix_sync_load ON distribution_sync_operation (load_id, sequence);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000175', '11111111-0000-0000-0000-000000000038',
     'distribution.sync.write', 'Sincronizar operações do aplicativo de distribuição', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'distribution.sync.write'
ON CONFLICT (group_id, permission_id) DO NOTHING;
