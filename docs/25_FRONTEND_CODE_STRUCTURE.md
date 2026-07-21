# Estrutura de código frontend

O frontend usa arquitetura **feature-first**, não uma cópia da hexagonal do backend.

```text
frontend/src/app/
├── core/
│   ├── auth/                       # sessão BFF, guards e contexto
│   ├── http/                       # interceptors e Problem Details
│   ├── config/
│   └── layout/
├── shared/
│   ├── ui/                         # componentes reutilizados e acessíveis
│   ├── util/
│   └── types/
└── features/
    └── recipes/
        ├── recipes.routes.ts
        ├── domain/
        │   ├── recipe.model.ts
        │   └── recipe.rules.ts
        ├── data-access/
        │   ├── recipes.api.ts
        │   └── recipes.store.ts
        ├── pages/
        │   ├── recipe-list-page/
        │   └── recipe-editor-page/
        └── ui/
            ├── recipe-form/
            └── ingredient-table/
```

## Uso das ferramentas

- Standalone components e lazy routes são o padrão.
- Signals/computed: estado local, derivado e de tela.
- RxJS: HTTP, debounce, cancelamento, websocket e combinação temporal.
- Store/facade: somente para fluxo compartilhado por várias páginas; não criar store por padrão.
- NgRx/Signal Store: somente se concorrência de efeitos, cache e estado global ficarem difíceis de manter.
- Typed/Signal Forms: formulários; validação final continua no backend.
- `OnPush`/zoneless: padrão do projeto, depois de verificar compatibilidade das bibliotecas.
- Vitest: unidade/componentes; Playwright: jornadas E2E críticas.
- Angular Material/CDK + design tokens CSS: base inicial. Não misturar dois design systems completos.

## Regras

Component não chama `HttpClient` diretamente, não contém regra cervejeira autoritativa e não manipula token. `data-access` converte DTOs da API em modelos da feature. `shared` recebe um componente apenas depois de reutilização real. Toda página trata carregamento, vazio, erro, conflito, offline, acesso negado e sucesso quando aplicável.
