-- COM-005 — avaliação e denúncia. A REVISÃO fica de fora, e é decisão registrada.
--
-- O QUE NÃO ESTÁ AQUI, E POR QUÊ. "Executar moderação auditada" pressupõe um papel ACIMA das
-- cervejarias: o autor não pode julgar denúncia contra a própria receita, e hoje todo principal tem uma
-- cervejaria. Existe o grupo de sistema ADMINISTRATORS sem cervejaria, mas usá-lo como moderador global
-- significa dar a alguém o poder de esconder publicação de qualquer casa — decisão de modelo de
-- segurança, e não detalhe de implementação. O mantenedor decidiu deixar para uma história própria
-- (DUV-COM-001). O agregado AbuseReport já sabe ser revisado; nenhum endpoint expõe isso.

-- UMA NOTA POR PESSOA, e ela se troca em vez de acumular. Deixar a mesma pessoa avaliar duas vezes
-- transformaria a média numa contagem de quem insistiu mais — o jeito mais simples de manipular
-- reputação sem robô nenhum. A chave primária composta é a garantia; o UPSERT é a consequência.
CREATE TABLE community_rating (
    publication_id UUID NOT NULL REFERENCES community_published_recipe (id),
    user_id UUID NOT NULL REFERENCES security_user (id),
    -- Escala fechada de 1 a 5. Zero seria "não avaliou", que é a ausência de linha — permitir zero faria
    -- "sem opinião" e "péssima" virarem o mesmo número na média.
    value SMALLINT NOT NULL,
    rated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (publication_id, user_id),
    CONSTRAINT ck_rating_value CHECK (value BETWEEN 1 AND 5)
);

CREATE INDEX ix_rating_publication ON community_rating (publication_id);

CREATE TABLE community_report (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES community_published_recipe (id),
    reporter_user_id UUID NOT NULL REFERENCES security_user (id),
    reason VARCHAR(12) NOT NULL,
    note VARCHAR(1000),
    reported_at TIMESTAMPTZ NOT NULL,
    -- Preparados para a revisão que ainda não tem quem a execute (DUV-COM-001). Nulos até lá, e o
    -- CHECK garante que uma revisão pela metade não existe.
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    outcome VARCHAR(10),
    outcome_note VARCHAR(1000),
    CONSTRAINT ck_report_reason CHECK (reason IN ('ABUSE', 'PLAGIARISM', 'SPAM', 'OTHER')),
    CONSTRAINT ck_report_outcome CHECK (outcome IS NULL OR outcome IN ('UPHELD', 'DISMISSED')),
    -- "Outro" sem explicação não é denúncia, é ruído: ninguém revisa o que não foi dito.
    CONSTRAINT ck_report_other_note CHECK (reason <> 'OTHER' OR length(btrim(coalesce(note, ''))) > 0),
    -- Ou não foi revisada, ou foi com quem, quando e desfecho. O meio-termo seria uma decisão sem
    -- responsável — e o critério da história pede moderação AUDITADA.
    CONSTRAINT ck_report_review CHECK (
        (reviewed_at IS NULL AND reviewed_by IS NULL AND outcome IS NULL)
        OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL AND outcome IS NOT NULL))
);

-- A MESMA PESSOA NÃO DENUNCIA A MESMA PUBLICAÇÃO DUAS VEZES PELO MESMO MOTIVO. Não é limite de opinião:
-- a contagem de denúncias é sinal, e um sinal que a mesma pessoa consegue repetir deixa de medir a
-- comunidade e passa a medir a insistência.
CREATE UNIQUE INDEX ux_report_once ON community_report (publication_id, reporter_user_id, reason);

-- A fila de quem um dia revisar, e a lista que o autor vê sobre o próprio conteúdo.
CREATE INDEX ix_report_open ON community_report (publication_id, reported_at DESC)
    WHERE reviewed_at IS NULL;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000164', '11111111-0000-0000-0000-000000000036',
     'community.rating.write', 'Avaliar e denunciar publicações', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code = 'community.rating.write'
ON CONFLICT (group_id, permission_id) DO NOTHING;
