# Revisão dos padrões fornecidos

Os documentos anexados foram usados como referência, mas pertenciam a outro produto. Este arquivo registra o que foi aproveitado e o que foi alterado para evitar que uma IA misture decisões.

## Mantido

- simplicidade, KISS/YAGNI e proibição de abstração sem necessidade;
- controller sem regra e sem acesso direto ao repository;
- módulos de negócio e proibição de repository cruzado;
- REST com recursos plurais, OpenAPI, paginação e erros consistentes;
- Flyway forward-only, `TIMESTAMPTZ`, constraints e índices;
- testes de regra, queries customizadas, integrações e fluxos críticos;
- páginas com loading, vazio, erro, acesso negado e feedback claro.

## Alterado

- estrutura totalmente flat foi mantida apenas para CRUD; domínios cervejeiros críticos usam hexagonal;
- vários módulos Maven foram substituídos inicialmente por um Maven + Spring Modulith;
- Java 21/Angular 21 foram atualizados para Java 25 LTS/Angular 22;
- Testcontainers deixou de ser opcional para persistência crítica;
- Karma/Jasmine foi substituído por Vitest em projeto Angular novo;
- erro genérico foi substituído por Problem Details RFC 9457;
- JWT em `localStorage` foi rejeitado; web usa sessão/cookie seguro via BFF;
- `Service` público entre módulos foi trocado por API publicada/evento para reduzir acoplamento.
