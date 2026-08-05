-- FDS-001: matriz de alergênicos — ingrediente, equipamento compartilhado e limpeza.
--
-- O vocabulário é DA CASA, não da plataforma. Uma lista fixa de alergênicos no código seria uma
-- classificação regulatória embutida, que envelhece com a norma e difere por país — exatamente o
-- risco que o README da sprint chama de "regra regulatória desatualizada". A cervejaria cadastra
-- os alergênicos que declara e a plataforma cuida de propagá-los; PKG-004-A pedia "ligar a fonte",
-- e a fonte é a declaração de quem assina o rótulo.
--
-- A distinção que estrutura o resto: NÃO DECLARADO ≠ DECLARADO SEM ALERGÊNICO. Por isso a
-- declaração é uma linha própria, separada dos alergênicos declarados: um ingrediente com linha em
-- `food_safety_ingredient_declaration` e nenhuma em `food_safety_ingredient_allergen` foi declarado
-- como isento; um ingrediente sem linha nenhuma é uma lacuna. Tratar lacuna como isenção é o erro
-- que imprime "não contém glúten" numa cerveja de cevada.

-- Vocabulário de alergênicos declarados pela cervejaria.
CREATE TABLE food_safety_allergen (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    CONSTRAINT uq_food_safety_allergen UNIQUE (brewery_id, code)
);

-- A declaração do ingrediente. A EXISTÊNCIA da linha é o dado: ela diz "alguém respondeu".
CREATE TABLE food_safety_ingredient_declaration (
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    ingredient_id UUID NOT NULL REFERENCES catalog_ingredient (id),
    declared_at TIMESTAMPTZ NOT NULL,
    declared_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (brewery_id, ingredient_id)
);

CREATE TABLE food_safety_ingredient_allergen (
    brewery_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    allergen_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (brewery_id, ingredient_id, allergen_code),
    FOREIGN KEY (brewery_id, ingredient_id)
        REFERENCES food_safety_ingredient_declaration (brewery_id, ingredient_id) ON DELETE CASCADE,
    FOREIGN KEY (brewery_id, allergen_code) REFERENCES food_safety_allergen (brewery_id, code)
);

-- Equipamento dedicado: a linha existe quando a casa declarou dedicação. Sem linha, o equipamento
-- é COMPARTILHADO — que é o estado natural de uma cervejaria pequena e o que torna a troca de
-- produto uma decisão de segurança em vez de rotina.
CREATE TABLE food_safety_equipment_dedication (
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    equipment_id UUID NOT NULL REFERENCES equipment (id),
    declared_at TIMESTAMPTZ NOT NULL,
    declared_by UUID NOT NULL,
    PRIMARY KEY (brewery_id, equipment_id)
);

-- Dedicação sem alergênico nenhum é a linha "livre de alergênicos" — a mais restritiva que existe,
-- e a razão pela qual a dedicação também é uma linha separada do conjunto.
CREATE TABLE food_safety_equipment_allergen (
    brewery_id UUID NOT NULL,
    equipment_id UUID NOT NULL,
    allergen_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (brewery_id, equipment_id, allergen_code),
    FOREIGN KEY (brewery_id, equipment_id)
        REFERENCES food_safety_equipment_dedication (brewery_id, equipment_id) ON DELETE CASCADE,
    FOREIGN KEY (brewery_id, allergen_code) REFERENCES food_safety_allergen (brewery_id, code)
);

-- Eficácia declarada do POP: quais alergênicos aquele procedimento remove. Referencia o POP por
-- CÓDIGO, e não por id de versão, pelo mesmo motivo que a matriz de compatibilidade (CLN-002) já
-- faz: a eficácia é do procedimento, não da revisão do texto dele.
CREATE TABLE food_safety_procedure_allergen (
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    procedure_code VARCHAR(40) NOT NULL,
    allergen_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (brewery_id, procedure_code, allergen_code),
    FOREIGN KEY (brewery_id, allergen_code) REFERENCES food_safety_allergen (brewery_id, code)
);

INSERT INTO permission_domain (id, parent_id, code, name, sort_order) VALUES
    ('11111111-0000-0000-0000-000000000021', NULL, 'food-safety', 'Segurança de alimentos', 26)
ON CONFLICT (id) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical) VALUES
    ('22222222-0000-0000-0000-000000000092', '11111111-0000-0000-0000-000000000021',
     'foodsafety.allergen.read', 'Consultar a matriz de alergênicos', false),
    ('22222222-0000-0000-0000-000000000093', '11111111-0000-0000-0000-000000000021',
     'foodsafety.allergen.write', 'Declarar alergênicos de ingrediente, equipamento e POP', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('foodsafety.allergen.read', 'foodsafety.allergen.write')
ON CONFLICT (group_id, permission_id) DO NOTHING;
