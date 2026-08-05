-- FDS-002: quarentena — bloquear um lote e o que veio dele durante a investigação.
--
-- A tabela guarda a ORIGEM, e só ela. Os descendentes são derivados do grafo no momento da
-- pergunta, pelo mesmo motivo que a genealogia (TRC-001) é derivada: um envase criado depois da
-- abertura precisa nascer bloqueado, e uma lista de descendentes congelada na abertura não o
-- conheceria. Contenção que envelhece é contenção furada — e materializá-la seria trocar a
-- correção pela economia de uma travessia de seis saltos.
--
-- Não há coluna de "alcance", "descendentes" nem "itens bloqueados". A ausência delas é a decisão.
CREATE TABLE traceability_quarantine (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- Tipo do nó da genealogia (BATCH, PACKAGING_PLAN, STOCK_LOT…). Sem FK: o nó pode morar em
    -- qualquer um dos cinco módulos que alimentam o grafo, e uma FK por tipo amarraria a
    -- rastreabilidade às tabelas alheias, que é justamente o que a porta LineageSource evita.
    node_type VARCHAR(30) NOT NULL,
    node_id UUID NOT NULL,
    -- Rótulo congelado: descreve o que foi bloqueado no dia em que foi. Um lote renomeado depois
    -- não reescreve a história da investigação.
    origin_label VARCHAR(200),
    reason VARCHAR(500) NOT NULL,
    opened_by UUID NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    released_by UUID,
    released_at TIMESTAMPTZ,
    -- Liberar exige justificativa: é a metade da alçada que a permissão sozinha não dá.
    release_justification VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_quarantine_status CHECK (status IN ('OPEN', 'RELEASED')),
    CONSTRAINT ck_quarantine_release CHECK (
        (status = 'OPEN' AND released_by IS NULL AND released_at IS NULL AND release_justification IS NULL)
        OR (status = 'RELEASED' AND released_by IS NOT NULL AND released_at IS NOT NULL
            AND release_justification IS NOT NULL))
);

-- Uma quarentena aberta por nó. A segunda partiria a investigação em duas, e liberar uma delas
-- daria a impressão de que o lote foi liberado.
CREATE UNIQUE INDEX uq_quarantine_open_node
    ON traceability_quarantine (brewery_id, node_type, node_id)
    WHERE status = 'OPEN';

-- Todo bloqueio começa lendo as quarentenas abertas da cervejaria; é a consulta do caminho quente.
CREATE INDEX ix_quarantine_open ON traceability_quarantine (brewery_id, status, opened_at DESC);

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000094', '11111111-0000-0000-0000-000000000020',
     'traceability.quarantine.read', 'Consultar quarentenas e o que elas alcançam', false),
    ('22222222-0000-0000-0000-000000000095', '11111111-0000-0000-0000-000000000020',
     'traceability.quarantine.open', 'Abrir quarentena de um lote', true),
    ('22222222-0000-0000-0000-000000000096', '11111111-0000-0000-0000-000000000020',
     'traceability.quarantine.release', 'Liberar quarentena — alçada própria, separada da abertura', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('traceability.quarantine.read', 'traceability.quarantine.open',
                 'traceability.quarantine.release')
ON CONFLICT (group_id, permission_id) DO NOTHING;
