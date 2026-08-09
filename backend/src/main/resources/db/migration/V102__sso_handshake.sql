-- SEC-B07: o aperto de mão de um login SSO iniciado pelo nosso lado.
--
-- Um login federado é uma conversa que sai da aplicação, passa por um terceiro e volta. Entre a ida e a
-- volta não há NADA ligando as duas pontas: o navegador que volta pode ser outro, a resposta pode ter sido
-- fabricada, e a mesma resposta pode voltar duas vezes. Esta tabela é o que amarra.
CREATE TABLE sso_handshake (
    id UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES federation_provider (id) ON DELETE CASCADE,
    -- Contra CSRF de login. Sem ele, um atacante inicia um fluxo com a própria conta e induz a vítima a
    -- completá-lo — a vítima fica logada como o atacante e digita dados dele achando que são seus.
    state VARCHAR(64) NOT NULL,
    -- Contra replay do token. Viaja ao provedor e volta dentro do token assinado; um token capturado e
    -- reenviado depois traz o nonce de outra conversa.
    nonce VARCHAR(64) NOT NULL,
    -- PKCE. O verificador NUNCA sai daqui — só o desafio derivado dele vai ao provedor. É essa assimetria
    -- que faz o PKCE valer: quem rouba o código no redirect não consegue trocá-lo por token.
    code_verifier VARCHAR(64) NOT NULL,
    -- Só caminho interno, garantido no domínio. URL absoluta faria do login um redirecionador aberto: um
    -- link para o nosso domínio que, depois de autenticar, joga a pessoa num site de terceiro.
    redirect_after_login VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- Uso único. Sem isso, a mesma resposta do provedor — capturada do histórico do navegador, de um log de
    -- proxy ou do cabeçalho Referer — cria uma sessão nova a cada reenvio.
    consumed_at TIMESTAMPTZ,
    CONSTRAINT uq_sso_handshake_state UNIQUE (state)
);

-- O índice da limpeza e da busca da volta. `state` já é único; este serve para varrer os vencidos.
CREATE INDEX ix_sso_handshake_created ON sso_handshake (created_at)
    WHERE consumed_at IS NULL;

-- O vínculo entre identidade externa e conta local já existe (V18, external_identity). O que falta é
-- registrar POR QUE ele foi criado, que é o que distingue um vínculo legítimo de um provisionamento
-- automático quando alguém for auditar meses depois.
ALTER TABLE external_identity
    ADD COLUMN IF NOT EXISTS provisioned BOOLEAN NOT NULL DEFAULT false;
