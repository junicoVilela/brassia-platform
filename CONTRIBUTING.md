# Como contribuir

1. Selecione uma história da sprint ativa e registre o responsável.
2. Crie uma branch curta no padrão `feat/ID-descricao`, `fix/ID-descricao` ou `chore/ID-descricao`.
3. Antes de editar, valide invariantes, módulo proprietário, contrato, migration, eventos, segurança e testes.
4. Entregue uma fatia vertical pequena e mantenha commits focados.
5. Execute testes, análise estática, inspeção arquitetural, migrations e build reproduzível.
6. Abra o pull request usando o modelo em `.github/pull_request_template.md`.

Commits devem explicar intenção e consequência. Mudança de arquitetura exige ADR; alteração incompatível exige plano de migração. Não usar o pull request para introduzir refatoração, biblioteca ou infraestrutura sem vínculo com a história.
