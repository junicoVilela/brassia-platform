# Dados e persistência

PostgreSQL com banco e schema `brassia`. Módulos são proprietários de suas tabelas; FKs podem preservar integridade, mas repositórios não atravessam limites.

- UUID para identidade técnica; códigos humanos separados.
- `numeric` para quantidades e dinheiro; unidade em coluna explícita.
- UTC em `timestamptz`; `brewery_id` obrigatório em dados tenant.
- `version` para optimistic locking.
- `created_at/by`, `updated_at/by` e `deleted_at/by` onde exclusão lógica fizer sentido.
- Ledgers, medições, auditoria e outbox são append-only na aplicação.
- Índices começam por `brewery_id` quando a consulta é tenant-scoped.
