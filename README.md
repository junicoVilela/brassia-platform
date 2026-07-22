# BrassIA — brassia-platform

**Inteligência em cada lote.** Plataforma inteligente de gestão cervejeira da Brew.

Este repositório reúne a especificação executável (fonte da verdade) e os
projetos de código do produto. Foi inicializado a partir do
*BrassIA AI Development Kit v6*: o pacote de especificação (`.ai/`, `docs/`,
`contracts/`, `database/`, `sprints/`) foi trazido para dentro do repositório e o
`scaffold/` de referência foi promovido para `backend/` e `frontend/`. A
documentação (ADRs, diagramas, roadmap e referências) foi consolidada sob `docs/`.

> Estado atual: **Sprint 00 (fundação) concluída**. Backend Spring Boot 4.1 e
> frontend Angular 22 são projetos reais e executáveis; PostgreSQL 18 com migrations
> Flyway, Problem Details (RFC 9457), auditoria/observabilidade e CI (GitHub Actions)
> verdes. Débitos e ressalvas em `sprints/sprint-00-foundation/ACCEPTANCE.md`.
> Próximo: Sprint 01 (segurança e cadastro de cervejaria).

## Refatorações de qualidade (SOLID / Clean Code)

Iniciativa a partir da revisão do `AuthenticationController` — todos os PRs
mesclados no `main`, CI verde em cada etapa:

| PR | Commit | Escopo |
|----|--------|--------|
| [#28](https://github.com/junicoVilela/brassia-platform/pull/28) | `2d6673e` | Extrai a orquestração de login do controller para um caso de uso (`PerformLoginUseCase`); dependências do controller de 8 → 5 |
| [#29](https://github.com/junicoVilela/brassia-platform/pull/29) | `a3bdd9e` | Elimina `ResponseEntity<?>` (interface selada `LoginResponse`) e centraliza o mapeamento `result → DTO` em static factories `from(...)` |
| [#30](https://github.com/junicoVilela/brassia-platform/pull/30) | `917aacf` | Registra o padrão de mapeamento em DTOs no `AGENTS.md` |
| [#31](https://github.com/junicoVilela/brassia-platform/pull/31) | `34f69ad` | Aplica o princípio no frontend: montagem de request do groups-page movida para o modelo |

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

Iniciar a **Sprint 01** (segurança e cadastro de cervejaria) a partir de
`sprints/sprint-01-security-brewery/`.

## Licença

Software **proprietário** — © Brew. Todos os direitos reservados. Este repositório
é público apenas para fins operacionais; nenhum arquivo de licença de código aberto
é concedido. O conteúdo (código, especificação, blueprints e documentação) não pode
ser copiado, reutilizado ou redistribuído sem autorização expressa.
