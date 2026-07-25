# Status — Sprint 01-D

Estado: EM ANDAMENTO

Contexto: fecha os débitos de backend descobertos ao entregar o frontend de segurança (01-B/01-C). Ao concluir cada história, o débito correspondente nas Sprints 01-B/01-C é removido e a tela simplificada.

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| SEC-B04 | Concluída | IA | #71 | GET /service-accounts/{id}/credentials (sem segredo) + tela lista/revoga credenciais persistidas. Remove o débito do SEC-F09. |
| SEC-B01 | Concluída | IA | #72 | GET /totp/status (mfaEnabled + recoveryCodesRemaining); Minha conta indica ativo/inativo no load. Remove o débito do SEC-F01. |
| SEC-B03 | A fazer | — | — | Filtros e paginação da auditoria. |
| SEC-B06 | A fazer | — | — | Identidades externas vinculadas (leitura). |
| SEC-B05 | A fazer | — | — | Administração de mapeamentos SCIM. |
| SEC-B02 | A fazer | — | — | Origem mascarada no histórico (opcional; requer migration). |

## Decisões e bloqueios

- Endpoints aditivos; não alterar contratos existentes. Migration só onde inevitável (SEC-B02).
- SEC-B07 (SSO no browser) fica na Sprint 15; QR inline do MFA é débito de frontend (SEC-F01).

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
