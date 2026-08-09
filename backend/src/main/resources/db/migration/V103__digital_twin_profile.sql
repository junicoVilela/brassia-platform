-- DTW-001: perfil aprendido do histórico de uma receita.
--
-- Um perfil é um RESUMO DATADO DE UMA AMOSTRA NOMEADA, não uma verdade sobre a cervejaria. As três coisas
-- juntas — o que saiu, quando foi calculado e quais lotes entraram — são o que o tornam auditável:
-- qualquer pessoa refaz a conta e chega ao mesmo número, ou aponta que a amostra tinha um lote que não
-- deveria estar lá.

CREATE TABLE twin_profile (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    recipe_id UUID NOT NULL,
    -- VERSIONADO, NUNCA SOBRESCRITO. Um perfil calculado em maio guiou decisões em maio; recalcular em
    -- agosto e apagar o anterior faria essas decisões parecerem tomadas sobre números que nunca
    -- existiram. É a mesma regra da receita publicada e do documento indexado.
    version INTEGER NOT NULL,
    computed_by UUID NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_twin_profile_version CHECK (version > 0),
    CONSTRAINT uq_twin_profile_version UNIQUE (brewery_id, recipe_id, version)
);

-- A versão mais recente por receita é a consulta real da tela.
CREATE INDEX ix_twin_profile_latest ON twin_profile (brewery_id, recipe_id, version DESC);

-- A estimativa de cada métrica.
--
-- Linha separada por métrica em vez de colunas fixas: acrescentar uma grandeza aprendida passa a ser
-- inserir linhas, não alterar a tabela — e perfis antigos continuam válidos sem a coluna nova, que é
-- exatamente o que "versionado" precisa significar.
CREATE TABLE twin_profile_estimate (
    profile_id UUID NOT NULL REFERENCES twin_profile (id) ON DELETE CASCADE,
    metric VARCHAR(40) NOT NULL,
    -- Nulos quando não houve amostra suficiente. AUSÊNCIA DECLARADA É INFORMAÇÃO: sem a linha, quem lê
    -- concluiria que a perda é zero em vez de que ela não foi estimada.
    mean NUMERIC(14, 4),
    standard_deviation NUMERIC(14, 4),
    lower_bound NUMERIC(14, 4),
    upper_bound NUMERIC(14, 4),
    sample_size INTEGER NOT NULL,
    confidence VARCHAR(20) NOT NULL,
    PRIMARY KEY (profile_id, metric),
    CONSTRAINT ck_twin_metric CHECK (metric IN ('VOLUME_YIELD_PERCENT', 'TRANSFER_LOSS_LITERS')),
    CONSTRAINT ck_twin_confidence CHECK (confidence IN ('INSUFFICIENT', 'LOW', 'MODERATE', 'HIGH')),
    CONSTRAINT ck_twin_sample CHECK (sample_size >= 0),
    -- Média presente exatamente quando há estimativa. Uma média com confiança INSUFFICIENT seria um
    -- número que o próprio registro diz não valer; uma média ausente com qualquer outra confiança seria
    -- uma estimativa sem estimativa.
    CONSTRAINT ck_twin_estimate_coherent CHECK (
        (confidence = 'INSUFFICIENT' AND mean IS NULL)
        OR (confidence <> 'INSUFFICIENT' AND mean IS NOT NULL
            AND lower_bound IS NOT NULL AND upper_bound IS NOT NULL)),
    -- A faixa não pode estar invertida. Largura zero é legítima: o processo repetiu exatamente.
    CONSTRAINT ck_twin_bounds CHECK (
        lower_bound IS NULL OR upper_bound IS NULL OR lower_bound <= upper_bound)
);

-- Os lotes que o perfil leu.
--
-- Não é metadado: é o que torna o número REPRODUTÍVEL. Sem esta tabela, "rendimento de 92%" é uma
-- afirmação sem contra o que conferir — e quem quisesse excluir um lote anômalo da amostra não teria como
-- mostrar que o excluiu.
CREATE TABLE twin_profile_sample (
    profile_id UUID NOT NULL REFERENCES twin_profile (id) ON DELETE CASCADE,
    batch_id UUID NOT NULL,
    PRIMARY KEY (profile_id, batch_id)
);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000029', NULL, 'digitaltwin', 'Perfil aprendido', 34)
ON CONFLICT (id) DO NOTHING;

-- Calcular é separado de ler porque calcular ESCOLHE A AMOSTRA — e a amostra é o que decide o número.
-- Quem pode escolher quais lotes entram no perfil que vai guiar o planejamento tem uma alçada diferente
-- de quem só consulta o resultado.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000126', '11111111-0000-0000-0000-000000000029',
     'digitaltwin.profile.read', 'Consultar perfil aprendido', false),
    ('22222222-0000-0000-0000-000000000127', '11111111-0000-0000-0000-000000000029',
     'digitaltwin.profile.compute', 'Calcular perfil — escolhe a amostra que define o número', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('digitaltwin.profile.read', 'digitaltwin.profile.compute')
ON CONFLICT (group_id, permission_id) DO NOTHING;
