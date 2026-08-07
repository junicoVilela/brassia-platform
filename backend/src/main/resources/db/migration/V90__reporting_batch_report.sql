-- RPT-001: relatório do lote.
--
-- NÃO HÁ TABELA. O relatório é consolidação: plano, execução, qualidade, custo e genealogia já
-- estão guardados em quem os produziu, e o dossiê os junta a cada pedido. Guardar o documento
-- criaria uma versão salva que discordaria da produção no dia seguinte — e, pior, seria essa a
-- versão levada a auditor.
--
-- O que fica registrado é a EXPORTAÇÃO, e ela fica na auditoria, não aqui. Ler o relatório é
-- consulta; exportar tira o documento de dentro do sistema, e a partir dali ele vive num e-mail ou
-- num pen drive. Por isso são duas alçadas: quem lê na tela não necessariamente pode levar embora.
INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000024', NULL, 'reporting', 'Relatórios', 29)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000106', '11111111-0000-0000-0000-000000000024',
     'reporting.batch.read', 'Consultar o relatório do lote', false),
    ('22222222-0000-0000-0000-000000000107', '11111111-0000-0000-0000-000000000024',
     'reporting.batch.export', 'Exportar o relatório do lote — o documento sai do sistema', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('reporting.batch.read', 'reporting.batch.export')
ON CONFLICT (group_id, permission_id) DO NOTHING;
