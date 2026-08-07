-- RPT-003: relatórios salvos e entrega programada.
--
-- AQUI TEM TABELA, e a diferença com as três histórias anteriores é o ponto. Indicador, variação e
-- dossiê são sobre o presente e se derivam. Uma definição de relatório é um ACORDO: alguém decidiu
-- que este recorte, com esta periodicidade, vai para estas pessoas. Acordo não se deriva de nada —
-- ele foi feito, tem autor e data, e some se não for guardado.
--
-- A execução também se guarda, e pelo mesmo motivo: o que foi entregue, para quem e quando é
-- passado. Refazer a consulta amanhã daria outro número, e o destinatário recebeu o de ontem.

CREATE TABLE reporting_saved_report (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    name VARCHAR(120) NOT NULL,
    -- Qual relatório: hoje só o painel operacional e o dossiê de lote sabem se produzir.
    kind VARCHAR(40) NOT NULL,
    -- A versão da DEFINIÇÃO, não do relatório. Editar filtros sobe a versão, e a execução guarda
    -- contra qual versão rodou: meses depois dá para dizer que aquele PDF saiu do recorte antigo.
    definition_version INTEGER NOT NULL,
    -- Filtros como JSON porque cada tipo de relatório tem os seus; validá-los é do tipo, não daqui.
    filters JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- O fuso é da definição e não do servidor: "todo dia 1º às 6h" é 6h na fábrica. Sem isto, a
    -- cervejaria de Manaus receberia o relatório de madrugada e o mês fecharia no dia errado.
    timezone VARCHAR(60) NOT NULL,
    format VARCHAR(20) NOT NULL,
    schedule VARCHAR(20) NOT NULL,
    -- Por quantos dias o artefato fica disponível. Retenção é decisão de quem define, não do
    -- sistema: relatório de auditoria fica meses, painel semanal fica dias.
    retention_days INTEGER NOT NULL,
    -- O proprietário técnico: é COM A ALÇADA DELE que a execução roda, resolvida no momento da
    -- execução. Não há execução com privilégio de sistema.
    owner_user_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_saved_report_name UNIQUE (brewery_id, name),
    CONSTRAINT ck_saved_report_kind CHECK (kind IN ('DASHBOARD', 'BATCH_REPORT')),
    CONSTRAINT ck_saved_report_format CHECK (format IN ('JSON')),
    CONSTRAINT ck_saved_report_schedule CHECK (schedule IN ('MANUAL', 'DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_saved_report_retention CHECK (retention_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_saved_report_version CHECK (definition_version >= 1)
);

CREATE INDEX ix_saved_report_brewery ON reporting_saved_report (brewery_id, name);
CREATE INDEX ix_saved_report_due ON reporting_saved_report (brewery_id, active, schedule)
    WHERE active AND schedule <> 'MANUAL';

-- Destinatários. Autorizados, não livres: só usuário da plataforma entra, porque só de usuário se
-- sabe a alçada. Mandar para um e-mail digitado à mão seria entregar dado da fábrica a um endereço
-- que ninguém verificou — e é exatamente o vazamento que "destinatários autorizados" evita.
CREATE TABLE reporting_saved_report_recipient (
    report_id UUID NOT NULL REFERENCES reporting_saved_report (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (report_id, user_id)
);

-- Execução: o que foi produzido, com qual versão da definição e sob qual alçada.
CREATE TABLE reporting_report_run (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES reporting_saved_report (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    definition_version INTEGER NOT NULL,
    -- Chave de idempotência: a execução programada de um período só acontece uma vez. Uma falha de
    -- ENTREGA não pode gerar dado novo — ela reenvia o artefato que já existe.
    idempotency_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- Por que não rodou, quando não rodou. O caso que importa: o dono perdeu a alçada.
    refusal_reason VARCHAR(500),
    content JSONB,
    period_from TIMESTAMPTZ,
    period_to TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_report_run_idempotency UNIQUE (report_id, idempotency_key),
    CONSTRAINT ck_report_run_status CHECK (status IN ('SUCCEEDED', 'REFUSED', 'FAILED')),
    -- Execução bem-sucedida tem conteúdo e prazo; recusada tem motivo. Nunca as duas coisas.
    CONSTRAINT ck_report_run_outcome CHECK (
        (status = 'SUCCEEDED' AND content IS NOT NULL AND expires_at IS NOT NULL
            AND refusal_reason IS NULL)
        OR (status <> 'SUCCEEDED' AND content IS NULL AND refusal_reason IS NOT NULL))
);

CREATE INDEX ix_report_run_report ON reporting_report_run (brewery_id, report_id, executed_at DESC);

-- O link de download é temporário: um token com prazo, e não o id da execução. O id vive no banco
-- para sempre; o link tem de morrer. Cada uso é auditado — é o mesmo cuidado da exportação manual
-- (RPT-001), pela mesma razão: a partir do download o documento está fora do sistema.
CREATE TABLE reporting_download_token (
    token VARCHAR(64) PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reporting_report_run (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    -- Para quem o link foi emitido. Link de um não serve para outro.
    user_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_download_token_run ON reporting_download_token (run_id);

-- Entrega, uma linha por destinatário e por execução. A chave primária composta é a idempotência:
-- reentregar não duplica, atualiza. Sem isso, uma falha parcial de envio reenviaria para quem já
-- tinha recebido.
CREATE TABLE reporting_report_delivery (
    run_id UUID NOT NULL REFERENCES reporting_report_run (id) ON DELETE CASCADE,
    brewery_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    detail VARCHAR(500),
    attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    PRIMARY KEY (run_id, user_id),
    CONSTRAINT ck_report_delivery_status CHECK (status IN ('PENDING', 'DELIVERED', 'REFUSED')),
    CONSTRAINT ck_report_delivery_attempts CHECK (attempts >= 0)
);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000109', '11111111-0000-0000-0000-000000000024',
     'reporting.saved.read', 'Consultar relatórios salvos', false),
    ('22222222-0000-0000-0000-000000000110', '11111111-0000-0000-0000-000000000024',
     'reporting.saved.manage', 'Criar e programar relatórios salvos', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('reporting.saved.read', 'reporting.saved.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
