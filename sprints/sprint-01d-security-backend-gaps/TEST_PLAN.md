# Plano de testes — Sprint 01-D

## Obrigatórios

- Integração com PostgreSQL/Testcontainers e migrations desde banco vazio.
- Autorização sem permissão e acesso a recurso de outra cervejaria (isolamento tenant).
- Contrato HTTP e Problem Details RFC 9457.
- `ModularityTest` (Spring Modulith) verde.

## Foco desta sprint

- Nenhuma leitura vaza segredo (credenciais) nem dado pessoal em claro (IP/UA).
- Filtros/paginação de auditoria respeitam o escopo da cervejaria.
- Mapeamentos SCIM e identidades externas escopados por provedor/cervejaria.
- Frontend correspondente deixa de usar o workaround (client-side/sessão).
