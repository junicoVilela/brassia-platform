# Backlog — Sprint 02


## CAT-001 — Ingredientes tipados

**Objetivo:** Cadastrar maltes, lúpulos, leveduras, sais, adjuntos e embalagens.

**Critérios específicos:**

- Campos específicos por tipo; unidade de uso e compra são validadas.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## EQP-001 — Perfil de equipamento

**Objetivo:** Cadastrar capacidade, perdas, eficiência e evaporação.

**Critérios específicos:**

- Volume acima da capacidade é rejeitado; histórico preserva versão.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## EQP-002 — Manutenção e calibração

**Objetivo:** Planejar indisponibilidade e instrumentos associados.

**Critérios específicos:**

- Equipamento indisponível não pode ser reservado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## WTR-001 — Fontes e laudos

**Objetivo:** Registrar composição, data, método e origem da água.

**Critérios específicos:**

- Íons/unidades válidos; laudo antigo permanece disponível.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## WTR-002 — Perfil e mistura de água

**Objetivo:** Simular combinação de fontes e alvo mineral.

**Critérios específicos:**

- Balanço fecha; resultado informa entradas e método.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
