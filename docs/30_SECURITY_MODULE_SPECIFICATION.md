# Especificação consolidada do módulo Segurança

O módulo `security` substitui o provedor externo de identidade e é uma fronteira de negócio do monólito modular. Ele cuida de identidade, autenticação, autorização e evidências de segurança; os demais módulos apenas recebem um `SecurityPrincipal` confiável e solicitam decisões de permissão.

## Funcionalidades da tela enviada

1. **Usuários**
   - cadastro, convite, verificação de e-mail, ativação, bloqueio, desbloqueio e desativação;
   - vínculos com uma ou mais cervejarias;
   - reset administrativo sem visualizar/definir senha do usuário;
   - revogação de sessões e fatores na desativação.
2. **Grupos de acesso**
   - grupos de sistema e customizados;
   - associação de usuários, permissões e escopos;
   - vigência, cópia controlada e comparação entre grupos.
3. **Domínios e permissões**
   - catálogo hierárquico por módulo/capacidade;
   - ações granulares, criticidade e descrição do impacto;
   - pesquisa de “quem possui esta permissão”.
4. **Escopos de acesso**
   - cervejaria inteira, módulo ou recurso específico;
   - restrição adicional por ownership/estado quando o caso de uso exigir;
   - seleção de cervejaria ativa sem confiar em parâmetro do cliente.
5. **Histórico de login**
   - sucesso/falha, horário, dispositivo, localização aproximada opcional e motivo seguro;
   - filtros, retenção, pseudonimização e alerta de evento suspeito;
   - visibilidade do próprio histórico e visão administrativa autorizada.
6. **Auditoria de segurança**
   - trilha append-only de autenticação, autorização e administração;
   - ator, alvo, resultado, justificativa, trace e diff mascarado;
   - exportação controlada e retenção configurada.
7. **Política de senha**
   - comprimento, blocklist, histórico opcional e parâmetros do encoder;
   - sem regras arbitrárias de composição e sem expiração periódica por padrão;
   - upgrade transparente do hash após login bem-sucedido.
8. **Sessões ativas**
   - sessão no servidor, dispositivo, criação, último uso e expiração;
   - encerrar uma, as demais ou todas; revogar automaticamente em eventos críticos;
   - timeout inativo/absoluto e reautenticação recente.
9. **Acessos temporários**
   - permissão, escopo, justificativa, início, fim, solicitante e aprovador;
   - expiração e revogação automáticas;
   - dupla aprovação configurável para funções críticas.

## Melhorias adicionais recomendadas

10. **MFA e passkeys** — WebAuthn/passkeys preferenciais, TOTP alternativo e códigos de recuperação; reduz impacto de senha roubada.
11. **Recuperação e verificação de conta** — tokens por hash, expiração, uso único, mensagens genéricas e notificação; evita enumeração e takeover.
12. **Dispositivos confiáveis** — usuário nomeia/revoga dispositivos e recebe aviso de novo acesso; melhora controle sem vincular sessão rigidamente ao IP móvel.
13. **Credenciais de serviço/API keys** — identidades técnicas separadas, chave mostrada uma vez, hash/prefixo, escopo, owner, expiração, rotação e revogação.
14. **Proteção adaptativa** — throttling progressivo por conta/IP/dispositivo, detecção de credential stuffing e CAPTCHA somente quando houver risco.
15. **Alertas e painel de risco** — novos dispositivos, rajadas de falha, elevação de privilégio, ação negada repetida e chave próxima de expirar.
16. **Revisão de acessos** — campanhas com responsável, prazo, decisão e evidência para remover acessos órfãos/excessivos.
17. **Segregação de funções** — impede solicitação e aprovação pela mesma pessoa e combinações incompatíveis configuradas.
18. **Administração delegada** — administrador atua apenas na própria cervejaria/escopo; impersonação não entra no MVP e, se necessária, exige banner, motivo, prazo e auditoria.
19. **Conta de emergência** — credencial break-glass offline, monitorada, com MFA forte e procedimento de uso/teste; destinada a falha administrativa, não ao uso diário.
20. **Privacidade e retenção** — minimização, pseudonimização, expurgo agendado e exportação autorizada de dados de login/auditoria.
21. **Provedores de identidade e SSO** — cadastro/teste/ativação de SAML 2.0 e OIDC, descoberta por domínio, metadados, certificados, mapeamento e health status.
22. **Provisionamento corporativo** — SCIM 2.0 para usuários/grupos, desprovisionamento imediato e reconciliação; LDAP/AD apenas como conector legado.
23. **Certificados e metadados** — validade, rotação com sobreposição, alerta de expiração e rollback de configuração.

## O que deliberadamente não será construído agora

- servidor OAuth completo sem cliente externo real;
- criptografia, gerador de token, protocolo MFA ou sessão caseiros;
- perguntas secretas como recuperação;
- JWT no navegador;
- bloqueio permanente por falhas, que permitiria negação de serviço;
- biometria própria: passkeys delegam verificação ao autenticador/plataforma.
