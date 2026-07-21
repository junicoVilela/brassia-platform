# Backlog — Sprint 10


## MTR-001 — Cadastro metrológico

**Objetivo:** Registrar faixa, resolução, precisão, calibração e padrões.

**Critérios específicos:**

- Instrumento vencido bloqueia ponto crítico; certificado permanece.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## MTR-002 — Correção de leitura

**Objetivo:** Aplicar temperatura/curva sem apagar valor original.

**Critérios específicos:**

- Resultado mostra fórmula e versão; original é imutável.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## QLT-001 — Plano de controle

**Objetivo:** Definir parâmetro, faixa, frequência e ação por produto/etapa.

**Critérios específicos:**

- Medição fora da faixa abre desvio conforme severidade.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## QLT-002 — Não conformidade e CAPA

**Objetivo:** Conter, investigar, agir e verificar eficácia.

**Critérios específicos:**

- Status e prazos controlados; encerramento exige verificação.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.



## SEN-001 — Sessão sensorial

**Objetivo:** Criar amostras cegas, ficha, resultado e comparação.

**Critérios específicos:**

- Resultado não aparece antes do fechamento; vínculo ao lote preservado.

- Operação respeita estado, permissão, `brewery_id` e concorrência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, outra cervejaria e repetição.
