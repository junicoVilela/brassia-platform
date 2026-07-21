# Arquitetura de autenticação e segurança web

Para a aplicação Angular no mesmo domínio, o módulo interno autentica credenciais e cria uma sessão opaca no servidor. O navegador recebe cookie `HttpOnly`, `Secure`, `SameSite` e `__Host-`, com proteção CSRF e rotação de identificador. Não há access/refresh token em `localStorage`.

Integrações máquina-a-máquina usam inicialmente credenciais de serviço e API keys escopadas; segredo é mostrado uma vez e somente seu hash é persistido. OAuth2 Authorization Server não entra no baseline: só será avaliado por ADR quando clientes externos, consentimento ou federação justificarem esse protocolo. Permissão é verificada no caso de uso com tenant, ação, estado e ownership; anotação de controller é defesa adicional.

## Quando o BFF vale a pena

A sessão no servidor reduz exposição de credenciais e centraliza rotação, revogação e logout. O custo é manter sessão e CSRF, aceito neste produto web. Uma SPA puramente bearer ou um servidor OAuth só deve surgir por ADR e requisito concreto de integração.
