# brassia-web

Frontend Angular 22 do BrassIA — standalone, zoneless, Signals, feature-first.

## Pré-requisitos

- Node 24 LTS (ver `.nvmrc`: 24.15.0) e npm.

## Comandos

```bash
npm ci        # instalação reproduzível (usa package-lock.json)
npm start     # ng serve — dev server em http://localhost:4200
npm run build # build de produção
npm test      # testes unitários (Vitest, via @angular/build:unit-test)
```

O dev server faz proxy para a API? Ainda não: configure `proxy.conf.json` quando
o backend expuser rotas consumidas pela UI. A API roda por padrão em `:8080` no
perfil local do backend (sobrescreva com `SERVER_PORT` se a porta estiver em uso).

## Estrutura (feature-first)

```text
src/app/
├── app.ts / app.config.ts / app.routes.ts   # shell e composição raiz
├── core/                                     # HTTP, sessão e utilitários transversais
│   └── http/                                 # ProblemDetails (RFC 9457) + interceptor
└── features/<módulo>/                        # pages, ui, data-access e domain
    └── recipes/                              # fatia de referência
```

Não replicar a hexagonal do backend aqui. Sem NgRx, Tailwind ou PWA na Sprint 00
sem uma história que justifique.
