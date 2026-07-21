# Sprint 00 — Bootstrap, fundação e qualidade

## Objetivo

Criar o repositório remoto, gerar os projetos, configurar o ambiente e entregar todos os quality gates verdes.

## Módulos

platform, shared mínimo, audit

## Dependências

Nenhuma

## Histórias

- `FND-000` — Repositório Git remoto e governança
- `FND-001` — Projetos iniciais e estrutura modular
- `FND-002` — Ambiente local
- `FND-003` — Pipeline CI
- `FND-004` — Contrato de erros
- `FND-005` — Auditoria e observabilidade base
- `FND-006` — Baseline de versões
- `FND-007` — Validação reproduzível e encerramento

## Entregáveis técnicos

- Criar/vincular repositório remoto e política mínima
- Gerar Java/Angular e convenções
- Verificar módulos com Spring Modulith
- Configurar Flyway/Testcontainers
- Criar CI e análise de dependências
- Validar por checkout limpo e publicar evidências

## Riscos que precisam de teste

- repositório remoto incorreto ou sobrescrito
- fronteira de módulo
- segredo em configuração
- migration inconsistente
- build não reproduzível fora da máquina original

## Fora do escopo

Funcionalidades de sprints posteriores, refatorações sem vínculo e infraestrutura não necessária para o objetivo.
