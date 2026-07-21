# Threat model inicial do módulo Segurança

| Ameaça | Controles principais | Teste obrigatório |
|---|---|---|
| Enumeração de usuários | mensagem genérica, timing aproximado, rate limit | conta inexistente e senha errada têm resposta equivalente |
| Credential stuffing/brute force | MFA, blocklist, throttling progressivo, alertas | rajadas por conta/IP/dispositivo sem bloqueio permanente |
| Sequestro/fixação de sessão | cookie seguro, rotação, timeout, revogação, TLS | ID muda no login/privilégio e cookie não é acessível ao JavaScript |
| CSRF | token, SameSite e validação de origem quando aplicável | comando sem/errado CSRF é negado |
| XSS e roubo de credencial | CSP, encoding, sem token no storage, HttpOnly | payload armazenado/refletido e inspeção de storage |
| Escalada de privilégio | negar por padrão, caso de uso valida permissão/escopo/tenant | usuário comum tenta ação crítica e tenant cruzado |
| Reset/MFA takeover | token hash/single-use, reautenticação, notificação e recovery seguro | token expirado/reusado e troca de fator sem sessão recente |
| API key vazada | hash, prefixo, escopo, expiração, rotação, owner e alerta | segredo não reaparece e revogação é imediata |
| Insider/admin abusivo | segregação, acesso temporário, auditoria e revisão | autoaprovação e exclusão/alteração de auditoria são negadas |
| Vazamento em log/backup | mascaramento, secrets scanning, backup cifrado e acesso mínimo | scanner confirma ausência de padrões sensíveis |
| Supply chain | BOM gerenciado, lockfile, SBOM, dependabot/renovate e assinatura onde viável | build reprodutível e vulnerabilidade alta bloqueia release |

Revisar o modelo em cada novo canal de autenticação, integração externa, mudança de armazenamento de segredo ou ampliação multi-tenant.
