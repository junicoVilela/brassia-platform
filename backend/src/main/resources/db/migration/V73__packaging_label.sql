-- PKG-004: rótulo e ficha do lote.
-- Nada no rótulo é digitado: cada campo vem de uma fonte rastreável (lote, plano, receita
-- publicada, controle de frescor) e a prévia mostra de onde veio cada valor.
-- O TEMPLATE (layout) é versionado e vive SEPARADO da REGRA REGULATÓRIA (quais campos são
-- obrigatórios): misturar os dois faria uma troca de layout derrubar silenciosamente um campo
-- exigido por lei, e o lote inteiro sairia irregular.

-- Salvar um template ACRESCENTA uma versão; a anterior nunca é reescrita, porque o rótulo
-- impresso mês passado precisa continuar explicável.
CREATE TABLE packaging_label_template (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    version INTEGER NOT NULL,
    fields TEXT NOT NULL,
    note VARCHAR(200),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_label_template_version UNIQUE (brewery_id, code, version),
    CONSTRAINT ck_label_template_version CHECK (version >= 1),
    CONSTRAINT ck_label_template_fields CHECK (length(fields) > 0)
);

CREATE INDEX ix_label_template_code ON packaging_label_template (brewery_id, code, version DESC);

-- Quais campos a lei exige depende do país e da categoria da bebida: a lista é da cervejaria,
-- o sistema não decide regulação por ela.
CREATE TABLE packaging_label_rule (
    brewery_id UUID PRIMARY KEY,
    required_fields TEXT NOT NULL,
    CONSTRAINT ck_label_rule_fields CHECK (length(required_fields) > 0)
);

-- Rótulo é material controlado: a reimpressão exige motivo, e cada tiragem guarda a versão do
-- template usada para o rótulo antigo continuar explicável depois do layout mudar.
CREATE TABLE packaging_label_print (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES packaging_plan (id),
    brewery_id UUID NOT NULL,
    template_id UUID NOT NULL REFERENCES packaging_label_template (id),
    template_code VARCHAR(40) NOT NULL,
    template_version INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    reprint BOOLEAN NOT NULL,
    reason VARCHAR(200),
    printed_by UUID NOT NULL,
    printed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_label_print_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_label_print_version CHECK (template_version >= 1),
    -- Reimpressão sem motivo não explica por que sobraram rótulos fora do lote.
    CONSTRAINT ck_label_print_reason CHECK (reprint = false OR reason IS NOT NULL)
);

CREATE INDEX ix_label_print_plan ON packaging_label_print (brewery_id, plan_id, printed_at DESC);
