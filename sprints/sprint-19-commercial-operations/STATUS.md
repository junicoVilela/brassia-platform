# Status — Sprint 19

Estado: **ATIVA desde 2026-08-15** — escolhida como próxima sprint. Nenhuma história iniciada.

| História | Estado | Evidência |
|---|---|---|
| CRM-001 | Em execução — domínio e testes prontos | `crm/domain/*`, 26 testes unitários |
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

### DEC-CRM-001 (CRM-001) — Pessoa e organização são coisas diferentes, e a separação é o desenho

**O problema que a separação resolve.** O aceite pede que "cliente, consentimento e retenção sejam
auditáveis" e o plano de testes pede "anonimização". Num modelo com uma tabela só, atender um pedido de
exclusão obriga a escolher entre **apagar a pessoa** e **destruir o histórico comercial** — e não existe
resposta boa para essa escolha. Por isso são dois agregados:

- **`Customer` é a organização compradora** — dado de negócio. Bar, restaurante e distribuidor não têm
  direito ao esquecimento; pedido, nota e custo precisam continuar apontando para eles. **Não se apaga:
  desativa-se**, porque é o histórico de expedição que um recall percorre para saber a quem avisar.
- **`Contact` é a pessoa** — dado pessoal, com prazo e apagamento. **Anonimizar mantém a casca**: o
  identificador continua, e com ele continuam as expedições que apontam para cá. É a diferença entre
  "esta entrega foi para alguém que pediu para ser esquecido" e um buraco que ninguém sabe explicar.

**Consentimento é por finalidade, e finalidade tem base legal.** Esta é a regra que mais mudou o
desenho. Se todo contato exigisse consentimento, revogar a permissão de receber oferta comercial
derrubaria junto o aviso de que a entrega saiu — e a cervejaria ficaria proibida de cumprir o que
vendeu. Então `TRANSACTIONAL` se apoia em **contrato** e não é revogável; `MARKETING` e `SURVEY` se
apoiam em **consentimento** e são revogáveis em separado. O domínio **recusa** registrar consentimento
para finalidade contratual, justamente para que ninguém possa "revogá-lo" depois.

**O histórico é um livro que só cresce, e a consulta é datada.** A pergunta que a auditoria faz não é
"ela aceita?", é **"ela aceitava quando mandamos aquilo?"**. Só um registro append-only responde. Duas
consequências que viraram teste: decisão posterior não contamina a consulta do passado, e a ordem que
vale é a **do mundo** (`at`), não a da digitação — decisão tomada por telefone na segunda pode ser
registrada na quarta, depois de outra.

**Silêncio não é permissão.** Quem nunca decidiu nada não é contactável. Mas "nunca perguntamos" e "ela
recusou" continuam distinguíveis no histórico, porque levam a ações opostas quando alguém revisa a base.

**Retenção é decisão da casa**, no mesmo espírito da PRM-001 e da `CapaPolicy`: sem política, **nada
expira**. Não anonimizar por falta de decisão é reversível; anonimizar cedo demais não é — o dado não
volta, e com ele vai embora o contato que talvez fosse preciso numa convocação de recall.

**Entregue nesta fatia:** `Customer`, `Contact`, `ConsentLedger`, `ConsentEntry`, `ContactPurpose`,
`LegalBasis`, `ConsentDecision`, `RetentionPolicy` e `ContactAnonymizedException` — **26 testes
unitários**, sem banco, sem adaptador e sem tela. O que falta: migration, portas, casos de uso,
endpoints com Problem Details, teste de isolamento por cervejaria e a tela.

### DEC-CRM-002 (CRM-001) — O documento do cliente não é validado, e isso é decisão

`taxId` é texto livre, sem verificação de CNPJ ou CPF. **Cliente estrangeiro não tem CNPJ**, e recusar
cadastro por formato seria a plataforma decidindo com quem a cervejaria pode vender. Além disso o
cadastro costuma nascer antes de o documento chegar, e travar aqui empurraria o vendedor a inventar um
número — que é pior que campo vazio, porque parece preenchido.

Se um dia a emissão fiscal entrar (INT-008), a validação nasce **lá**, onde o formato realmente importa e
onde o provedor homologado já a exige. Validar aqui seria antecipar uma regra da nota para o cadastro.

### DUV-CRM-001 (CRM-001) — Três perguntas que não invento, registradas em vez de decididas

Conforme o rito do projeto, ambiguidade que altera regra de negócio vira pergunta e não invenção:

1. **Existe prazo mínimo de retenção que a cervejaria não pode escolher abaixo?** Nota fiscal tem
   guarda legal de anos, mas ela é do *pedido*, não do *contato*. Hoje o domínio aceita qualquer prazo
   positivo. Se houver piso, ele é da casa ou da lei — e precisa vir de alguém que saiba qual.
2. **O que conta como "último relacionamento" para o relógio da retenção?** O último pedido? A última
   entrega? Uma conversa registrada? A escolha muda quem é anonimizado e quando. Deixei o domínio
   recebendo a data pronta, para que essa decisão fique fora dele até ser tomada.
3. **Anonimização é ato humano ou varredura automática?** O domínio suporta os dois — `dueFor` responde
   quem venceu, sem executar nada. Automatizar sem revisão apaga contato de cliente ativo que só ficou
   um ano sem comprar; exigir revisão manual faz a fila crescer até ninguém olhar. É decisão de operação.

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
