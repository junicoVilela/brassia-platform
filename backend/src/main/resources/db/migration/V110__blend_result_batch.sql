-- BLD-001 (DEC-BLD-003 resolvida) — o blend passa a produzir lote, e o volume passa a se mover.
--
-- A PREMISSA CAIU. A V105 registrou que origem e destino eram lotes pré-existentes porque
-- `production_batch.order_id` era NOT NULL e inventar uma ordem sintética criaria uma ordem que ninguém
-- programou — que apareceria no planejamento, que o custeio tentaria ratear e que a aderência contaria
-- como desvio. A decisão de negócio veio: blend produz lote novo. A ordem sintética continua recusada; o
-- que muda é a coluna.
--
-- POR QUE `origin` EXISTE, SE A AUSÊNCIA DE ORDEM JÁ DIRIA ISSO. Um nulo diz "não tem", não diz "por quê".
-- Lote sem ordem poderia ser resultado de blend ou defeito de importação, e as duas coisas exigem reação
-- oposta. A coluna nomeia a origem e o CHECK impede a combinação sem sentido nos dois sentidos.
ALTER TABLE production_batch ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE production_batch
    ADD COLUMN origin VARCHAR(12) NOT NULL DEFAULT 'BREW_ORDER';

ALTER TABLE production_batch
    ADD CONSTRAINT ck_production_batch_origin CHECK (
        (origin = 'BREW_ORDER' AND order_id IS NOT NULL)
        OR (origin = 'BLEND' AND order_id IS NULL)
    );

-- O UNIQUE vira índice parcial. No PostgreSQL nulos já são distintos entre si, então a restrição antiga
-- não impediria vários lotes de blend — mas ela também não DIRIA isso a quem lesse o schema. O índice
-- parcial declara a regra que sempre valeu: uma ordem gera no máximo um lote; lote sem ordem não disputa.
--
-- Sobre o custo: `production_batch` cresce um registro por lote, não por medição. A varredura é barata
-- aqui, e o `CONCURRENTLY` que o ensaio de REL-004 recomendou continua valendo para as tabelas que
-- crescem sem teto (`production_measurement`, `audit_event`), não para esta.
ALTER TABLE production_batch DROP CONSTRAINT uq_production_batch_order;

CREATE UNIQUE INDEX uq_production_batch_order
    ON production_batch (brewery_id, order_id)
    WHERE order_id IS NOT NULL;

-- Ajuste de volume envasável.
--
-- O volume envasável é DERIVADO desde a PRD-005: vem da transferência, ou do planejado quando ainda não
-- houve transferência. Guardar um saldo numa coluna criaria um segundo número que diverge do primeiro no
-- dia em que alguém corrigir uma transferência — por isso o blend também não guarda saldo: ele registra o
-- movimento, e o saldo continua sendo conta.
--
-- O sinal está no número aqui, e não num campo de lado, porque este registro é o próprio ajuste: -200 L é
-- o fato. No `blend_movement` o lado existe porque lá o número descreve uma quantidade movida entre dois
-- lugares, e o sentido é do movimento, não do número.
CREATE TABLE production_volume_adjustment (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    batch_id UUID NOT NULL REFERENCES production_batch (id),
    delta_liters NUMERIC(12, 3) NOT NULL,
    source VARCHAR(12) NOT NULL,
    source_ref UUID NOT NULL,
    actor_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_volume_adjustment_source CHECK (source IN ('BLEND')),
    -- Ajuste de zero litro não é ajuste: seria uma linha de auditoria fingindo ser movimento de cerveja.
    CONSTRAINT ck_volume_adjustment_not_zero CHECK (delta_liters <> 0),
    -- O mesmo lote não é ajustado duas vezes pela mesma operação. É o que torna a execução repetível sem
    -- ser cumulativa: uma segunda chamada esbarra no banco, não numa checagem prévia que duas requisições
    -- simultâneas atravessariam juntas.
    CONSTRAINT uq_volume_adjustment_source UNIQUE (batch_id, source, source_ref)
);

CREATE INDEX ix_volume_adjustment_batch ON production_volume_adjustment (brewery_id, batch_id);

-- Saída planejada que ainda não é lote.
--
-- Ela não pode viver em `blend_movement` porque lá a chave primária é (operação, lado, lote) e o lote
-- ainda não existe: o resultado nasce na EXECUÇÃO, nunca na simulação. Uma coluna de lote anulável na
-- chave primária não é expressável, e criar o lote na simulação contradiria a DEC-BLD-002 — antes de
-- executar, nenhuma cerveja se tocou.
--
-- `recipe_id` é declarado por quem planeja, não inferido da origem predominante. Uma união de 60% de IPA
-- com 40% de Stout não é "uma IPA": herdar a receita da maior parte faria o rótulo imprimir o ABV e o
-- estilo da IPA sobre uma cerveja que não é ela. Quem planeja diz o que o resultado é, porque é isso que
-- vai ser vendido.
CREATE TABLE blend_planned_output (
    operation_id UUID NOT NULL REFERENCES blend_operation (id) ON DELETE CASCADE,
    seq INTEGER NOT NULL,
    recipe_id UUID NOT NULL,
    liters NUMERIC(12, 3) NOT NULL,
    -- Preenchido na execução. Nulo enquanto a operação não executou é o estado honesto: o lote não existe.
    created_batch_id UUID REFERENCES production_batch (id),
    PRIMARY KEY (operation_id, seq),
    CONSTRAINT ck_blend_planned_output_positive CHECK (liters > 0),
    CONSTRAINT ck_blend_planned_output_seq CHECK (seq >= 1),
    -- Um lote de resultado pertence a uma saída planejada e só a ela.
    CONSTRAINT uq_blend_planned_output_batch UNIQUE (created_batch_id)
);

-- O TANQUE DO LOTE DE RESULTADO.
--
-- Cerveja está sempre em algum lugar, e "em algum lugar" é a informação que liga sensor, fermentação e
-- ocupação de vaso ao lote. Sem tanque, o lote de blend existiria com volume e sem endereço: telemetria não
-- teria a quem se ligar, e o fermentador que o recebeu continuaria aparecendo como livre para o próximo.
--
-- A ocupação já é derivada da transferência desde a PRD-005, e ali há um comentário que vale repetir: uma
-- segunda tabela dizendo onde o lote está divergiria da primeira. Por isso o enchimento do blend entra como
-- transferência, e não como conceito novo.
--
-- O QUE OBRIGA A MUDANÇA: `og_sg` era NOT NULL. Um blend não tem OG — ninguém mediu densidade inicial de
-- uma mistura de cervejas prontas, e inventar 1.0500 para satisfazer a coluna criaria um número que
-- pareceria medido. A coluna passa a aceitar nulo, e o CHECK amarra o nulo ao tipo: transferência de brassa
-- continua exigindo OG, enchimento de blend não tem onde declarar um.
ALTER TABLE production_transfer
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'BREW_TRANSFER';

ALTER TABLE production_transfer ALTER COLUMN og_sg DROP NOT NULL;

ALTER TABLE production_transfer
    ADD CONSTRAINT ck_production_transfer_kind CHECK (kind IN ('BREW_TRANSFER', 'BLEND_FILL'));

ALTER TABLE production_transfer
    ADD CONSTRAINT ck_production_transfer_og CHECK (
        (kind = 'BREW_TRANSFER' AND og_sg IS NOT NULL)
        OR (kind = 'BLEND_FILL' AND og_sg IS NULL)
    );

-- O tanque da saída planejada é declarado junto com o volume: as duas respostas descrevem o mesmo ato de
-- quem planeja — quanta cerveja, e para onde ela vai.
ALTER TABLE blend_planned_output ADD COLUMN equipment_id UUID NOT NULL;
