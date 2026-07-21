# Arquitetura

## Decisão principal

Monólito modular, um backend Spring Boot implantável e uma aplicação Angular. Os módulos são pacotes de negócio diretos sob o pacote raiz e têm limites verificados por Spring Modulith. Extração para serviço só ocorre após ADR com evidência de escala, isolamento, disponibilidade ou ciclo de entrega independente.

## Hexagonal pragmática

Aplicar hexagonal completa em `recipe`, `production`, `inventory`, `sanitation`, `fermentation`, `packaging`, `quality`, `traceability`, `costing` e `ai`, onde existem invariantes, cálculos, máquinas de estado ou integrações. Nesses módulos:

- `domain`: agregados, entidades, valores, políticas e eventos, sem Spring/JPA/HTTP.
- `application`: casos de uso, comandos, consultas, transação e portas.
- `adapter/inbound`: REST, jobs, eventos, importadores e CLI.
- `adapter/outbound`: JPA, storage, e-mail, sensores e provedores de IA.
- `config`: composição do framework.

Módulos de apoio predominantemente CRUD podem usar `api`, `application` e `infrastructure`. Não criar interface, porta, entidade duplicada ou mapper apenas para “parecer hexagonal”. Ao crescer em regra/variação, o módulo migra por fatias.

## Fluxo

Interface → adapter de entrada → caso de uso → domínio → porta → adapter de saída. Eventos externos usam Outbox. Consultas pesadas podem usar read models sem violar ownership.

## Stack de referência

Java 25 LTS, Spring Boot 4.1, Spring Modulith 2.1, Maven 3.9.16 Wrapper, PostgreSQL 18, Flyway, OpenAPI 3.1, Angular 22, Node 24 LTS, TypeScript 6.0, Vitest, armazenamento S3 compatível, Docker Compose e OpenTelemetry. Versões são verificadas em `docs/23_VERSION_POLICY.md`; Redis, broker, busca vetorial e Kubernetes são opcionais e entram por ADR.
