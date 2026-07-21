# Backlog — Sprint 01


## SEC-001 — Usuários e ciclo da conta

**Objetivo:** Cadastrar, convidar, verificar, ativar, bloquear e desativar contas internas.

**Critérios específicos:**

- E-mail normalizado é único; convite é de uso único; mudança crítica é auditada; desativação revoga sessões.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-002 — Login e sessão segura

**Objetivo:** Autenticar credenciais e criar sessão opaca no PostgreSQL com cookie seguro.

**Critérios específicos:**

- Cookie __Host- é HttpOnly/Secure/SameSite; CSRF ativo; sessão gira no login e elevação; logout revoga servidor e cliente.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-003 — Política e histórico de senha

**Objetivo:** Aplicar encoder adaptativo, blocklist de senhas comprometidas e histórico configurável.

**Critérios específicos:**

- Senha nunca é logada; erro é genérico; política aceita Unicode e frases; não há expiração periódica sem indício de comprometimento.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-004 — Grupos, domínios e permissões

**Objetivo:** Criar catálogo hierárquico de ações e grupos que agregam permissões.

**Critérios específicos:**

- Negação por padrão; menor privilégio; alteração é auditada; grupo não concede ação inexistente/inativa.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-005 — Escopos de acesso

**Objetivo:** Restringir concessões por cervejaria, módulo e recurso quando necessário.

**Critérios específicos:**

- brewery_id do corpo não é autoridade; tenant cruzado é negado; RBAC e escopo são avaliados no caso de uso.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-006 — Sessões, dispositivos e histórico de login

**Objetivo:** Listar sessões/dispositivos, revogar uma ou todas e registrar tentativas de acesso.

**Critérios específicos:**

- Usuário encerra sessões; admin atua somente com permissão; histórico mascara IP/UA conforme retenção e não expõe sessão.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-007 — Auditoria de segurança

**Objetivo:** Registrar eventos append-only de autenticação, autorização e administração.

**Critérios específicos:**

- Evento contém ator, alvo, resultado, motivo, trace e diff mascarado; nunca contém senha, token ou segredo MFA.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-008 — Acesso temporário

**Objetivo:** Conceder permissão/escopo com justificativa, vigência, aprovação e revogação.

**Critérios específicos:**

- Acesso expira automaticamente; concessão crítica pode exigir dupla aprovação; uso e revogação são auditados.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-009 — MFA, passkeys e recuperação

**Objetivo:** Oferecer WebAuthn/passkeys preferencialmente, TOTP alternativo e códigos de recuperação.

**Critérios específicos:**

- Admin exige MFA; recuperação não usa pergunta secreta; códigos são hash/single-use; alteração de fator exige reautenticação recente.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-010 — Recuperação e verificação de conta

**Objetivo:** Implementar confirmação de e-mail e redefinição por token aleatório, curto e de uso único.

**Critérios específicos:**

- Resposta/timing não enumeram usuário; token é armazenado por hash; reset não autentica automaticamente e pode revogar sessões.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-011 — Credenciais de serviço e API keys

**Objetivo:** Criar identidades técnicas com chaves escopadas, expiração, rotação e revogação.

**Critérios específicos:**

- Segredo aparece uma vez; banco guarda hash/prefixo; toda chamada é atribuível; chave humana não é reutilizada por integração.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-012 — Proteção e alertas

**Objetivo:** Aplicar limitação progressiva por conta/IP/dispositivo e detectar eventos de risco.

**Critérios específicos:**

- Credential stuffing é desacelerado; bloqueio não vira DoS permanente; alertas têm severidade, estado e evidência sem dado sensível.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-013 — Revisão de acessos e segregação

**Objetivo:** Revisar periodicamente privilégios e impedir combinações críticas configuradas.

**Critérios específicos:**

- Revisão tem responsável/prazo/evidência; acesso órfão é removido; exceção tem justificativa e validade.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-014 — Federação SAML 2.0

**Objetivo:** Configurar o sistema como Service Provider/Relying Party para SSO corporativo.

**Critérios específicos:**

- Metadata/certificados são validados e rotacionáveis; assertion verifica issuer/audience/destination/tempo/assinatura; identidade liga por provider+subject, nunca por e-mail isolado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-015 — Federação OpenID Connect

**Objetivo:** Adicionar login OIDC por Authorization Code para provedores modernos.

**Critérios específicos:**

- Discovery, state, nonce, PKCE e redirect exato são validados; issuer+sub identifica a conta; login termina em sessão interna.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEC-016 — SCIM e diretório corporativo

**Objetivo:** Provisionar usuários/grupos por SCIM 2.0 e oferecer LDAP/AD somente como adapter legado.

**Critérios específicos:**

- Desprovisionamento revoga sessões; grupo externo passa por allowlist; SCIM é idempotente; LDAP usa TLS e bind de menor privilégio.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## BRW-001 — Cadastrar cervejaria

**Objetivo:** Criar dados, fuso, unidades e políticas iniciais.

**Critérios específicos:**

- Código é único; datas exibem fuso configurado; auditoria criada.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## BRW-002 — Preferências operacionais

**Objetivo:** Configurar unidades, moeda, limites e política de estoque.

**Critérios específicos:**

- Valor inválido é rejeitado; mudança não altera snapshots antigos.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
