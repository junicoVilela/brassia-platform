# Regras de dependência e verificação

## Backend

- Módulo pode depender apenas da API publicada de outro módulo.
- `domain` depende apenas de Java e tipos de domínio permitidos.
- `application` depende de `domain` e portas; nunca de controller/JPA.
- Adapters dependem de application/domain; adapters não se chamam diretamente.
- Nenhum módulo lê tabela ou repository de outro módulo.
- Ciclos entre módulos bloqueiam o build.

Spring Modulith deve executar `ApplicationModules.of(BrassiaApplication.class).verify()` na CI. Testes `@ApplicationModuleTest` validam cada módulo isoladamente. ArchUnit adicional só é criado para regras que o Modulith não cobre.

## Frontend

- `core` não depende de `features`.
- `shared` não depende de `features` nem de `core` específico.
- Uma feature não importa internals de outra; usa rota, contrato público ou capability compartilhada.
- `ui` não chama HTTP; `pages` orquestram; `data-access` concentra IO/estado remoto.
- ESLint boundaries bloqueia importações profundas e ciclos.

Exceção temporária exige comentário com issue e data de remoção; exceção permanente exige ADR.
