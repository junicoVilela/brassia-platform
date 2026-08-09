# Sprint 21 — Conectores externos

## Objetivo

Importar dados de serviços cervejeiros de terceiros sob credencial e escopo explícitos do usuário, com
sincronização observável e conflito resolvido por decisão humana.

## Módulos

integration

## Dependências

Sprint 04 (pipeline canônico BeerJSON/BeerXML), Sprint 15 (inbox/idempotency, outbox e assinatura HMAC),
Sprint 17 publicada.

## Histórias

- `INT-004` — Conector Brewfather API v2
- `INT-005` — Conector Brewer's Friend API v1
- `INT-007` — Central de sincronização e conflitos

## Origem

As três histórias nasceram na Sprint 15 e foram movidas para cá por decisão registrada em
`sprints/sprint-15-integrations-pwa/STATUS.md` (DEC-INT-001). O motivo é de verificabilidade, não de
prioridade: os critérios de aceite de INT-004 e INT-005 exigem exercitar paginação, rate limit, backoff,
timeout e revogação contra a API real, e isso depende de credencial de terceiro que o projeto não possui.
Implementá-las só contra dublê marcaria a história como concluída sem que o critério tivesse sido cumprido.

INT-007 acompanha porque existe para exibir execuções, cursores, falhas, rate limit e conflitos **desses**
conectores — sem eles, é uma tela sem conteúdo.

## Entregáveis técnicos

- Cofre de credencial por cervejaria, com mascaramento na leitura
- Cursor de sincronização retomável
- Modelo de prévia: criar, atualizar, ignorar, conflitar
- Backoff com respeito a rate limit e revogação

## Riscos que precisam de teste

- credencial vazando em log, evento ou exportação
- retry perdendo ou repetindo o cursor
- campo externo desconhecido truncado em silêncio
- conflito sobrescrevendo receita local
- rate limit do provedor tratado como falha permanente

## Pré-condição de execução

Não iniciar sem credencial de teste válida dos provedores. Sem ela, os critérios de paginação, rate limit,
backoff, timeout e revogação não são verificáveis e a sprint não pode ser aceita.

## Fora do escopo

Escrita nos provedores externos, importação de sessões antes de contrato documentado e testes de conflito,
e qualquer conector que não seja Brewfather ou Brewer's Friend.
