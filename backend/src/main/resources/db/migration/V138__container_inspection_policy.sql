-- DUV-CON-001 — de quanto em quanto tempo a casa inspeciona cada tipo de vasilhame.
--
-- O SISTEMA NÃO TRAZ NÚMERO NENHUM DE FÁBRICA, e isso é a decisão. A periodicidade de inspeção de vaso de
-- pressão vem de norma e do tipo do contêiner; escrever aqui "cinco anos" faria o sistema AFIRMAR
-- conformidade que ninguém verificou, num equipamento cuja falha é física. Prazo errado por excesso é
-- risco; por falta, é frota parada sem motivo — nos dois casos o sistema teria inventado a resposta.
--
-- O QUE MUDA: antes, quem inspecionava precisava saber de cabeça até quando a inspeção valia e digitar a
-- data. Agora o sistema SUGERE a partir do que a casa cadastrou — e continua aceitando outra, porque a
-- inspeção que encontra um problema pode encurtar o prazo. Sugestão é conveniência, e não autoridade.
CREATE TABLE container_inspection_policy (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    kind VARCHAR(10) NOT NULL,
    interval_months INTEGER NOT NULL,
    note VARCHAR(300),
    updated_by UUID NOT NULL REFERENCES security_user (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_inspection_policy_kind CHECK (kind IN ('KEG', 'CASK', 'GROWLER')),
    -- Zero seria "inspecionar sempre", que na prática é não ter política — e a ausência já se representa
    -- não cadastrando a linha.
    CONSTRAINT ck_inspection_policy_interval CHECK (interval_months >= 1)
);

-- UMA POLÍTICA POR TIPO, por casa. Keg de pressão e growler não seguem o mesmo prazo, e uma política
-- única obrigaria a cervejaria a adotar o menor dos dois para todos.
CREATE UNIQUE INDEX ux_inspection_policy ON container_inspection_policy (brewery_id, kind);

-- NENHUMA LINHA É INSERIDA AQUI. Sem política, o vasilhame continua exigindo inspeção válida para ser
-- enchido, e a data continua vindo de quem inspeciona: a ausência não afrouxa regra nenhuma, ela só
-- deixa de sugerir.

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000176', '11111111-0000-0000-0000-000000000037',
     'container.inspection-policy.manage', 'Definir a periodicidade de inspeção por tipo', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'container.inspection-policy.manage'
ON CONFLICT (group_id, permission_id) DO NOTHING;
