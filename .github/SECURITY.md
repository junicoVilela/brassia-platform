# Política de segurança

Não registrar vulnerabilidade real em backlog público. O relato deve conter impacto, cenário mínimo, componente, versão e forma segura de reprodução, sem dados reais de cervejaria.

## Regras para o desenvolvimento

- Segredos entram por gerenciador/variável de ambiente e nunca por arquivo versionado.
- Dependências são fixadas, verificadas e atualizadas em mudanças isoladas.
- Autorização, tenant, upload, exportação, integrações e ferramentas de IA recebem testes negativos.
- Achado crítico ou alto bloqueia release até correção ou aceite formal de risco.
- Logs e dumps usados em diagnóstico precisam ser sanitizados e ter retenção limitada.
