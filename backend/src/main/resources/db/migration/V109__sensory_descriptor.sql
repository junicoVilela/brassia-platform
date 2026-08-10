-- SEN-002 — Biblioteca de descritores e off-flavors.
--
-- A LICENÇA É COLUNA, NÃO OBSERVAÇÃO.
--
-- O critério pede que "conteúdo licenciado respeite atribuição e nível de permissão". Um campo de texto
-- dizendo "ver licença" não respeita nada: depende de alguém ler antes de copiar o descritor para um
-- relatório que sai da cervejaria. Modelado como coluna, a permissão viaja com o dado — quem exporta sabe,
-- sem consultar ninguém, se pode incluir o limiar e se precisa imprimir a atribuição.
CREATE TABLE sensory_descriptor (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(20) NOT NULL,
    source_name VARCHAR(200) NOT NULL,
    source_reference VARCHAR(500),
    license_tier VARCHAR(30) NOT NULL,
    source_attribution VARCHAR(500),
    -- Limiar de percepção. NULO quando a licença da fonte não autoriza publicá-lo: o limiar é resultado
    -- de trabalho experimental caro, e é por ele que os catálogos de referência cobram. Descrever
    -- "papelão" é vocabulário comum; afirmar o valor do limiar é reproduzir a medição de alguém.
    perception_threshold NUMERIC(12, 4),
    threshold_unit VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    CONSTRAINT uq_sensory_descriptor_code UNIQUE (brewery_id, code),
    CONSTRAINT ck_sensory_descriptor_category CHECK (category IN ('ATTRIBUTE', 'OFF_FLAVOR')),
    CONSTRAINT ck_sensory_descriptor_license
        CHECK (license_tier IN ('OWN', 'ATTRIBUTION_REQUIRED', 'LICENSED_INTERNAL_ONLY')),
    -- Licença que exige atribuição não fica sem o texto dela. O banco recusa junto com o domínio: a
    -- regra vale para quem entra pela aplicação e para quem entra por carga direta.
    CONSTRAINT ck_sensory_descriptor_attribution CHECK (
        license_tier = 'OWN' OR source_attribution IS NOT NULL
    ),
    -- Limiar só com licença que o autoriza, e nunca sem unidade: 0,1 pode ser µg/L, mg/L ou ppm, e a
    -- diferença entre eles é de mil vezes.
    CONSTRAINT ck_sensory_descriptor_threshold_licensed CHECK (
        perception_threshold IS NULL OR license_tier <> 'LICENSED_INTERNAL_ONLY'
    ),
    CONSTRAINT ck_sensory_descriptor_threshold_unit CHECK (
        perception_threshold IS NULL OR threshold_unit IS NOT NULL
    )
);

-- Sinônimos em tabela própria e não em array: a busca por termo é o caminho quente — quem anota na mesa
-- de prova digita e espera achar —, e um índice sobre linhas resolve isso sem depender de operador de
-- array. Guardados normalizados (sem acento, minúsculas) porque "cartonado" e "Cartonádo" são o mesmo
-- termo para quem escreveu.
CREATE TABLE sensory_descriptor_synonym (
    descriptor_id UUID NOT NULL REFERENCES sensory_descriptor (id) ON DELETE CASCADE,
    term VARCHAR(120) NOT NULL,
    normalized_term VARCHAR(120) NOT NULL,
    PRIMARY KEY (descriptor_id, normalized_term)
);

CREATE INDEX ix_sensory_synonym_normalized ON sensory_descriptor_synonym (normalized_term);

-- HIPÓTESES, e o nome da tabela é a garantia.
--
-- O critério diz que causa e ação corretiva são hipóteses, não diagnóstico automático. Chamar esta tabela
-- de `sensory_descriptor_cause` faria o mesmo dado significar outra coisa para quem escreve a consulta e,
-- depois, para quem lê a tela — e a diferença desaparece na hora em que alguém lê "diacetil → parada de
-- fermentação" e vai mexer no tanque.
CREATE TABLE sensory_descriptor_hypothesis (
    descriptor_id UUID NOT NULL REFERENCES sensory_descriptor (id) ON DELETE CASCADE,
    possible_cause VARCHAR(300) NOT NULL,
    -- Como confirmar. Obrigatório: dizer "pode ser infecção" sem dizer como verificar deixa quem lê com
    -- a preocupação e sem o próximo passo.
    suggested_check VARCHAR(300) NOT NULL,
    likelihood VARCHAR(20) NOT NULL,
    PRIMARY KEY (descriptor_id, possible_cause),
    CONSTRAINT ck_sensory_hypothesis_likelihood
        CHECK (likelihood IN ('COMMON', 'OCCASIONAL', 'RARE'))
);

-- Vínculo com estilo, para o scoresheet oferecer o vocabulário do que se está provando.
CREATE TABLE sensory_style_descriptor (
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    style_code VARCHAR(40) NOT NULL,
    descriptor_id UUID NOT NULL REFERENCES sensory_descriptor (id) ON DELETE CASCADE,
    -- Esperado no estilo, ou desvio para aquele estilo. O MESMO descritor muda de papel conforme o
    -- estilo: banana é atributo numa Weissbier e desvio numa Pilsen, e um vocabulário que não distingue
    -- isso ensina errado justamente no treinamento.
    expected BOOLEAN NOT NULL,
    PRIMARY KEY (brewery_id, style_code, descriptor_id)
);

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000143', d.id,
       'sensory.descriptor.read', 'Consultar biblioteca de descritores', false
FROM permission_domain d WHERE d.code = 'sensory'
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_permission (id, domain_id, code, name, critical)
SELECT '22222222-0000-0000-0000-000000000144', d.id,
       'sensory.descriptor.write', 'Manter biblioteca de descritores', false
FROM permission_domain d WHERE d.code = 'sensory'
ON CONFLICT (code) DO NOTHING;

INSERT INTO group_permission (group_id, permission_id)
SELECT '33333333-0000-0000-0000-000000000001', p.id
FROM security_permission p
WHERE p.code IN ('sensory.descriptor.read', 'sensory.descriptor.write')
ON CONFLICT (group_id, permission_id) DO NOTHING;
