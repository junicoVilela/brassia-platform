-- COM-002 — o link revogável, e a correção de uma fronteira que a COM-001 deixou frouxa.
--
-- O QUE A COM-001 DEIXOU FROUXO. Lá, a visibilidade LINK era legível por QUALQUER usuário autenticado que
-- soubesse o identificador — isto é semântica de UNLISTED, e não de LINK. A partir daqui, LINK exige um
-- token válido; UNLISTED continua sendo "abre por endereço direto, sem segredo". A correção mora na
-- consulta de leitura, e este comentário existe para o próximo a ler não achar que sempre foi assim.
--
-- SÓ O HASH. Como no token de conta e no segredo do webhook: o valor legível aparece uma vez, na
-- criação, e nunca mais. Um link vazado do banco seria acesso concedido sem que ninguém tivesse
-- compartilhado nada.

CREATE TABLE community_share_link (
    id UUID PRIMARY KEY,
    brewery_id UUID NOT NULL REFERENCES brewery (id),
    publication_id UUID NOT NULL REFERENCES community_published_recipe (id),
    -- Único: dois links com o mesmo token seriam o mesmo link com dois donos, e revogar um deixaria o
    -- outro aberto.
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    permission VARCHAR(12) NOT NULL,
    -- Para o autor lembrar a quem deu: "pro Bruno avaliar". Opcional, e é o que torna a revogação uma
    -- decisão informada em vez de um chute entre seis linhas iguais.
    label VARCHAR(120),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- Nulo é "sem prazo", e é legítimo. O que não é opcional é poder cortar.
    expires_at TIMESTAMPTZ,
    -- Revogar é o arrependimento; expirar é o prazo combinado. As duas coisas existem porque são
    -- diferentes, e não há "desrevogar": o motivo de revogar costuma ser que o link chegou a quem não
    -- devia, e reabrir seria reabrir exatamente aquilo.
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_share_permission CHECK (permission IN ('READ', 'COMMENT')),
    CONSTRAINT ck_share_expiry CHECK (expires_at IS NULL OR expires_at > created_at)
);

-- A busca do acesso: pelo hash, e só os que ainda valem. Parcial porque link revogado nunca é
-- consultado para abrir nada — ele só aparece na lista do autor, que usa o outro índice.
CREATE INDEX ix_share_link_token ON community_share_link (token_hash)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_share_link_publication ON community_share_link (brewery_id, publication_id, created_at DESC);

-- Criar e revogar link é do mesmo dono que publica: quem decide o que sai decide para quem sai.
-- Não nasce permissão nova — `community.recipe.publish` já é a alçada de "eu respondo por esta receita
-- lá fora", e fatiá-la em duas faria a tela pedir dois papéis para uma decisão só.
