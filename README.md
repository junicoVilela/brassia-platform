# BrassIA — brassia-platform

**Inteligência em cada lote.** Plataforma inteligente de gestão cervejeira da Brew.

Este repositório reúne a especificação executável (fonte da verdade) e os
projetos de código do produto. Foi inicializado a partir do
*BrassIA AI Development Kit v6*: o pacote de especificação (`.ai/`, `docs/`,
`contracts/`, `database/`, `sprints/`) foi trazido para dentro do repositório e o
`scaffold/` de referência foi promovido para `backend/` e `frontend/`. A
documentação (ADRs, diagramas, roadmap e referências) foi consolidada sob `docs/`.

> Estado atual: **esqueleto estruturado**. O código em `backend/` e `frontend/`
> é a fatia de referência do scaffold, ainda não os projetos oficiais gerados.
> Execute `sprints/sprint-00-foundation/RUNBOOK.md` para gerar os projetos reais,
> banco migrado, contratos, observabilidade e CI verde.

## Identidade canônica

- Produto: **BrassIA** — Plataforma inteligente de gestão cervejeira.
- Repositório: `brassia-platform`.
- Backend / application name: `brassia-api` — pacote raiz `br.com.brew.brassia`.
- Frontend: `brassia-web`.
- Banco, schema e usuário local: `brassia`, `brassia`, `brassia_app`.

## Stack (baseline)

- Backend: Java 25 LTS + Spring Boot 4.1, monólito modular (Spring Modulith).
- Frontend: Angular 22 standalone, Signals, zoneless, Vitest.
- Dados: PostgreSQL 18. Migrations com Flyway.
- Ver `docs/23_VERSION_POLICY.md` para as versões fixadas.

> ⚠️ Pré-requisito: o backend exige **JDK 25**. Confirme com `java -version`
> antes de compilar; a máquina de desenvolvimento pode estar em uma versão anterior.

## Estrutura

```text
brassia-platform/
├── backend/       # brassia-api (Spring Boot, referência do scaffold)
├── frontend/      # brassia-web (Angular, referência do scaffold)
├── infra/         # ambiente local e observabilidade
├── e2e/           # jornadas críticas de ponta a ponta
├── contracts/     # OpenAPI, eventos e schemas
├── database/      # DDL inicial por domínio
├── sprints/       # planejamento executável por sprint
├── docs/          # documentação viva (arquitetura, domínio, regras, segurança, IA, testes, operação)
│   ├── adr/           # decisões arquiteturais
│   ├── architecture/  # diagramas Mermaid
│   ├── roadmap/       # MVP, riscos, backlog e board de sprints
│   └── reference/     # blueprint e catálogo funcional consolidados
└── .ai/           # contexto, regras e templates para agentes de IA
```

Arquivos de raiz: `README.md`, `AGENTS.md` e `CLAUDE.md` (pontos de entrada).
`CONTRIBUTING.md` e `SECURITY.md` ficam em `.github/`; `CURSOR.md` em `.cursor/`.

## Ambiente local

```bash
cp .env.example .env          # ajuste os segredos locais
docker compose up -d          # PostgreSQL 18 (compose.yaml)
```

Consulte `docs/19_LOCAL_ENVIRONMENT.md` para subida, reset e diagnóstico.

## Como trabalhar (leia antes de codar)

1. `AGENTS.md`, `.ai/PROJECT_CONTEXT.md` e `.ai/DEVELOPMENT_RULES.md`.
2. `docs/22_ARCHITECTURE_DECISION_GUIDE.md` e `docs/23_VERSION_POLICY.md`.
3. A pasta da sprint ativa em `sprints/` — trabalhe **uma história por vez**,
   entregando a fatia vertical completa (domínio, aplicação, persistência,
   API, frontend e testes).
4. Feche pelo `.ai/DEFINITION_OF_DONE.md` e pelo `ACCEPTANCE.md` da sprint.

## Próximo passo

Executar `sprints/sprint-00-foundation/RUNBOOK.md` para transformar este
esqueleto nos projetos oficiais executáveis.
