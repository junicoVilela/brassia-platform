# Estrutura recomendada do repositório de código

```text
brassia-platform/
├── backend/
│   ├── pom.xml                    # um módulo Maven inicialmente
│   └── src/main/java/br/com/brew/brassia/
│       ├── BrassiaApplication.java
│       ├── shared/                # somente capacidades técnicas mínimas
│       ├── recipe/                # módulo Spring Modulith
│       ├── production/
│       ├── inventory/
│       └── ...
├── frontend/
│   └── src/app/
│       ├── core/                  # sessão, HTTP, config e shell
│       ├── shared/                # UI e utilitários realmente reutilizados
│       └── features/<module>/     # pages, ui, data-access e domain
├── infra/                         # ambiente local e observabilidade
├── e2e/                           # jornadas críticas
├── docs/                          # documentação viva e ADRs
└── contracts/                     # OpenAPI, eventos e schemas
```

Um único módulo Maven reduz configuração e tempo de build para um desenvolvedor. Spring Modulith verifica ciclos e acessos entre pacotes. Separar módulos Maven apenas quando houver ganho medido: build incremental insuficiente, necessidade de artefato reutilizável, limites repetidamente violados ou equipes independentes. Mesmo então, continua um monólito implantável.

O frontend organiza rotas por feature e não replica a hexagonal do backend. Contratos gerados não substituem tipos, estado e políticas de apresentação da feature.
