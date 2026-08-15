-- SAL-001 — o que a cervejaria vende, por onde vende, e por quanto vendia em cada data.
--
-- PRODUTO NÃO É LOTE, E É A DECISÃO QUE ORGANIZA O RESTO. "IPA lata 473 ml" é identidade comercial:
-- existe antes da primeira brassa, sobrevive a todos os lotes e é o que aparece numa lista de preço. O
-- packaging_finished_lot é a coisa física. Se fossem a mesma tabela, cada envase criaria um item de
-- catálogo novo e a lista de preço precisaria ser refeita a cada brassa.

CREATE TABLE sales_product (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    -- Sempre em maiúsculas (normalizado no domínio): "ipa-473" e "IPA-473" são o mesmo código no mundo
    -- real, e tratá-los como dois deixaria a cervejaria com dois produtos que são um.
    sku VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    recipe_id UUID NOT NULL REFERENCES recipe (id),
    -- Sem chave estrangeira, seguindo a convenção do packaging_plan (V67): a embalagem vem do catálogo
    -- e é referenciada por id em todo o módulo de envase sem FK. Divergir aqui criaria uma regra que só
    -- vale nesta tabela, e o próximo a mexer não saberia qual das duas seguir.
    container_id UUID NOT NULL,
    -- Não se apaga produto: descontinua. Pedido antigo aponta para ele, e um pedido cujo item não
    -- existe mais é um histórico que ninguém consegue explicar.
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sales_product_sku CHECK (length(btrim(sku)) > 0),
    CONSTRAINT ck_sales_product_name CHECK (length(btrim(name)) > 0)
);

CREATE UNIQUE INDEX ux_sales_product_sku ON sales_product (brewery_id, sku);
CREATE INDEX ix_sales_product_brewery ON sales_product (brewery_id, active, name);

-- CANAL É TABELA, E NÃO ENUM — mesma decisão da atividade de mão de obra (V117). A segmentação de uma
-- cervejaria que vende no próprio taproom e para dois distribuidores não é a de uma que exporta e atende
-- rede de supermercado. Um enum imporia a divisão de quem escreveu o código, e a primeira cervejaria com
-- um canal a mais precisaria de uma migration para poder vender.
CREATE TABLE sales_channel (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sales_channel_code CHECK (length(btrim(code)) > 0)
);

CREATE UNIQUE INDEX ux_sales_channel_code ON sales_channel (brewery_id, code);

-- A LINHA DO TEMPO DO PREÇO. Um pedido feito em março tem que continuar explicável em dezembro, e isso
-- exige saber quanto o produto custava em março — a mesma razão que faz o consentimento ser um livro na
-- CRM-001. Guardar só o preço atual transformaria todo pedido antigo num número sem origem.
CREATE TABLE sales_price_entry (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    product_id UUID NOT NULL REFERENCES sales_product (id),
    channel_id UUID NOT NULL REFERENCES sales_channel (id),
    -- Dinheiro com moeda explícita, que é critério transversal da sprint. Quatro casas porque preço
    -- unitário de item barato some inteiro num arredondamento para centavo; o arredondamento para
    -- dinheiro de verdade acontece no total do pedido (SAL-002).
    amount NUMERIC(14, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    -- A plataforma NÃO calcula imposto — motor fiscal está fora do escopo da sprint. Mas precisa saber
    -- se o número já o contém, senão alguém compara preço com imposto contra preço sem e conclui errado.
    tax_included BOOLEAN NOT NULL,
    valid_from DATE NOT NULL,
    -- Nulo é "até segunda ordem", e é o estado normal do preço vigente.
    valid_to DATE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sales_price_positive CHECK (amount > 0),
    CONSTRAINT ck_sales_price_period CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_sales_price_currency CHECK (currency = upper(currency))
);

-- A GARANTIA DE VERDADE DA INVARIANTE, e não a checagem do domínio.
--
-- O domínio recusa sobreposição, mas checagem prévia não sobrevive a duas requisições simultâneas — e
-- sobreposição de preço é exatamente o que duas telas abertas produzem. Se dois preços valem no mesmo
-- dia, "quanto custa hoje?" tem duas respostas e o sistema escolhe uma pela ordem em que leu as linhas.
--
-- daterange [] porque as duas pontas são INCLUSIVAS: quem compra no último dia paga o preço daquele
-- dia. Um preço fechado em 28/02 e outro começando em 01/03 são adjacentes e não conflitam.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE sales_price_entry
    ADD CONSTRAINT ex_sales_price_no_overlap
    EXCLUDE USING gist (
        brewery_id WITH =,
        product_id WITH =,
        channel_id WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    );

CREATE INDEX ix_sales_price_lookup
    ON sales_price_entry (brewery_id, product_id, channel_id, valid_from);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000035', NULL, 'sales', 'Produtos e preços', 40)
ON CONFLICT (id) DO NOTHING;

-- Consultar é trabalho de quem vende; mexer em catálogo e em PREÇO é decisão comercial, e por isso a
-- alçada de preço é separada da de produto: cadastrar um SKU novo não é o mesmo ato de mudar quanto a
-- cervejaria cobra por ele.
INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000152', '11111111-0000-0000-0000-000000000035',
     'sales.catalog.read', 'Consultar produtos, canais e preços', false),
    ('22222222-0000-0000-0000-000000000153', '11111111-0000-0000-0000-000000000035',
     'sales.catalog.manage', 'Cadastrar produtos e canais', false),
    ('22222222-0000-0000-0000-000000000154', '11111111-0000-0000-0000-000000000035',
     'sales.price.manage', 'Definir preço de venda', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sales.catalog.read', 'sales.catalog.manage', 'sales.price.manage')
ON CONFLICT (group_id, permission_id) DO NOTHING;
