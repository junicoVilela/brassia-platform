# Status — Sprint 19

Estado: **ATIVA desde 2026-08-15** — escolhida como próxima sprint. Nenhuma história iniciada.

| História | Estado | Evidência |
|---|---|---|
| CRM-001 | A fazer | — |
| SAL-001 | A fazer | — |
| SAL-002 | A fazer | — |
| SAL-003 | A fazer | — |
| FCST-001 | A fazer | — |
| INT-008 | A fazer | — |

Ordem prevista: **CRM-001 → SAL-001 → SAL-002 → SAL-003 → FCST-001 → INT-008**. Não é arbitrária — pedido
precisa de cliente e de produto com preço, e portal B2B precisa de pedido. A previsão de demanda vem
depois porque ela lê histórico de pedido, e antes disso não há histórico nenhum para ler.

## Decisões e bloqueios

### DEC-SPR-019 — Por que a 19, e o que ela assume que ainda não é verdade

**A escolha real era entre a 18 e a 19.** A Sprint 20 depende explicitamente da 19, então não podia vir
antes; a 21 segue bloqueada por falta de credencial de teste dos provedores (DEC-INT-001).

**Escolhida a 19, por três motivos:**

- **É a única que destrava outra coisa.** A Sprint 20 (contêineres e distribuição) depende dela. A 18 não
  destrava nada — adiá-la custa só o adiamento dela mesma.
- **A 18 é a que aponta para fora.** Biblioteca pública, link compartilhado, fork, denúncia e moderação
  colocam dado de cervejaria fora da cervejaria. Construir exposição pública sobre um release que ninguém
  validou é a combinação de riscos que menos se quer ter — ainda mais com a restauração não medida
  (REL-001). A 19 é interna: cliente, pedido, preço, previsão.
- **As dependências técnicas dela acabaram de amadurecer.** A 19 pede "Sprints 05, 06 e 13 maduras", e a
  Sprint 17 fechou justamente os débitos que faltavam na 13 — mão de obra no custo (CST-001-A), perda
  esperada com desvio (CST-002-A) e o dossiê do lote em PDF (RPT-001-A).

**A ressalva, que não deve ser lida como formalidade.** Tanto a 18 quanto a 19 declaram `Sprint 17
publicada` como dependência, e o `AI_EXECUTION.md` desta sprint diz, com todas as letras: *"implemente a
Sprint 19 como módulo opcional **após o núcleo estar em produção**"*. **O núcleo não está em produção.**
A Sprint 17 encerrou sem declarar o release pronto, porque REL-001 (restauração medida) ficou fora de
escopo e o ciclo em homologação da REL-005 continua aberto.

**A premissa assumida, explícita para poder ser derrubada:** desenvolver a 19 não exige produção;
*publicá-la* exige. O trabalho pode andar, e nenhuma história desta sprint deve ir para produção antes de
o release ser validado. Se o mantenedor discordar, o custo de reverter é zero — nenhuma linha foi escrita
até aqui, e esta decisão é o único artefato.

**O que destravaria de verdade** continua sendo do mantenedor, e não desta sprint: reabrir a REL-001 e
rodar o ciclo de homologação da REL-005.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
