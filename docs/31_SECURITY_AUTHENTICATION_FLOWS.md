# Fluxos de autenticação e sessão

## Login com senha

1. Angular obtém cookie CSRF e envia identificador, senha e token CSRF por HTTPS.
2. Backend normaliza identificador, aplica rate limit e executa comparação com trabalho equivalente para reduzir enumeração.
3. Conta, política e contexto são validados; falha retorna mensagem genérica.
4. Se MFA for exigido, cria desafio curto no servidor; ainda não existe sessão autenticada completa.
5. Após fator válido, Spring Security gira o identificador e Spring Session persiste a sessão no PostgreSQL.
6. Cookie `__Host-brew_session` é `Secure`, `HttpOnly`, `SameSite`, `Path=/` e não possui `Domain`.
7. Histórico e auditoria recebem evento seguro; o hash é atualizado se o encoder indicar upgrade.

## Passkey/WebAuthn

O backend gera challenge aleatório e guarda estado curto. O navegador usa `navigator.credentials`; servidor verifica challenge, origem, RP ID, assinatura e contador com biblioteca mantida. Chave privada nunca sai do autenticador. HTTPS é obrigatório fora de `localhost`.

## Recuperação de senha

1. A resposta é sempre neutra e o tempo não denuncia existência da conta.
2. Token aleatório é enviado por canal verificado; banco guarda somente hash, tipo, expiração e uso.
3. A troca valida token, política, rate limit e uso único; não autentica automaticamente.
4. Usuário é notificado e escolhe/segue política para revogar todas as sessões.
5. Pergunta secreta nunca é usada como único fator.

## Mudança sensível

Senha, e-mail, MFA, grupos, escopos, API keys, exportação de auditoria e ações operacionais críticas exigem sessão recente. Após mudança de privilégio, o cache de autorização é invalidado e as sessões afetadas são rotacionadas ou revogadas.

## Timeouts de baseline

Começar com 30 minutos de inatividade e 12 horas absolutas para usuários comuns; administradores podem usar limites menores. “Lembrar dispositivo” não elimina MFA indefinidamente e tem revogação própria. Valores são política versionada, testados com a operação real.
