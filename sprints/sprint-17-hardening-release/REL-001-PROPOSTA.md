# REL-001 — proposta de reabertura em formato reduzido

**Situação.** A história está fora de escopo desde a `DEC-REL-008`: `infra/backup/` saiu do repositório a
pedido do mantenedor. A consequência foi registrada e não suavizada — **não há RPO nem RTO de dados
medidos**, e `docs/21_DATA_RETENTION_BACKUP.md` continua afirmando que backup sem teste de restauração não
é controle válido. O documento e o repositório discordam desde então.

Este texto não pede a reabertura. Ele descreve **o que custaria**, em três tamanhos, para a decisão ser
sobre preço e não sobre princípio.

---

## O que a especificação original pedia

> RPO/RTO medidos; procedimento reproduzível e auditado.

Três coisas, e é a terceira que carrega o custo: *reproduzível* implicava script versionado, ambiente
isolado e manutenção continuada desse script a cada mudança de schema.

---

## Três tamanhos

### A — Ensaio único, manual, documentado (o mais barato)

**O que se faz:** uma restauração, uma vez, com cronômetro, em ambiente descartável, e uma página
registrando o que aconteceu.

**O que entrega:** RPO e RTO **medidos uma vez**, com data e condições declaradas. Um procedimento escrito
que outra pessoa consegue repetir lendo.

**O que não entrega:** reprodutibilidade automática. O número envelhece — daqui a seis meses ele descreve
um banco menor e um schema antigo, e o texto precisa dizer isso.

**Custo:** algumas horas, sem manutenção continuada. Não entra no CI, não quebra build, não pede
manutenção a cada migration.

**Por que ainda vale:** um número medido uma vez é incomparavelmente melhor que nenhum. Hoje a resposta
para "em quanto tempo você volta do zero?" é *não sabemos* — e é essa resposta, não o valor do número, que
inviabiliza a entrada em produção com responsabilidade.

### B — Ensaio único + roteiro versionado, sem automação

Tudo do A, mais o procedimento como runbook no repositório, ao lado do `deploy-rollback.md`, com o critério
de conferência escrito. Continua sendo executado à mão.

**O que muda:** a próxima pessoa não reconstrói o raciocínio; ela segue o roteiro. O runbook fica sujeito a
envelhecer, mas envelhece **visivelmente**, junto do outro.

**Custo:** o A mais algumas horas de escrita.

### C — O escopo original

Script versionado, ambiente isolado reproduzível, conferência automatizada, execução periódica. É o que a
`DEC-REL-008` removeu.

**Custo:** o de manter uma peça de infraestrutura viva a cada mudança de schema — que foi, presumivelmente,
a razão de removê-lo.

---

## O que já foi aprendido, e não precisa ser aprendido de novo

Isto veio de **rodar** o ensaio removido, e não de escrevê-lo. Custaria o mesmo tempo outra vez:

- **`n_live_tup` não serve para conferir integridade.** É estimativa do autovacuum e vem **zerada** num
  banco recém-restaurado: a conferência acusaria divergência em todas as tabelas, sempre. O correto é
  `COUNT(*)` exato, via `query_to_xml` quando se quer varrer todas as tabelas de uma vez.
- **As ferramentas do PostgreSQL têm de rodar em container da mesma imagem do servidor.** Versão de
  `pg_dump` diferente da do servidor falha de formas que parecem corrupção e não são.

Qualquer um dos três tamanhos começa com essas duas coisas resolvidas.

---

## Recomendação

**O tamanho A**, e a razão é o que ele destrava em relação ao que custa. O bloqueio real de hoje não é a
ausência de automação — é a ausência de **qualquer** número. Um ensaio único medido, com as condições
declaradas honestamente ("banco de N GB, ambiente local, em tal data"), fecha a divergência entre
`docs/21_DATA_RETENTION_BACKUP.md` e a realidade, e dá a quem for operar uma expectativa fundamentada em
vez de um silêncio.

Se a decisão for **não reabrir**, a alternativa honesta não é deixar como está: é alterar a
`docs/21_DATA_RETENTION_BACKUP.md` para declarar que a casa opera **sem** teste de restauração e assume
esse risco conscientemente. Hoje o documento afirma um controle que não existe, e essa é a pior das três
situações — pior que não ter o controle, porque quem lê acredita que ele está lá.
