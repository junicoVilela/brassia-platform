# Runbook — Deploy, migration e retorno (REL-004)

Procedimento operacional para colocar uma versão em produção e para voltar quando ela não se sustenta.

> **A regra que organiza tudo aqui:** o banco não volta. A aplicação volta.
> `docs/20_RELEASE_MIGRATION.md` define expand/contract, e a consequência prática é que *rollback* é
> sempre rollback **da aplicação** — o schema segue em frente. Um runbook que promete desfazer migration
> promete o que não vai conseguir cumprir no dia em que precisar.

---

## Antes de qualquer coisa: pré-requisitos

| Item | Como conferir |
|---|---|
| Backup restaurável **de hoje** | Restauração ensaiada nas últimas 24 h, pelo `restore-drill.md`. O ensaio é manual, e a conferência que vale é a de integridade — dump que abre sem erro não é backup íntegro |
| Credencial do coletor de métricas | `/actuator/prometheus` passou a exigir autenticação (REL-003). Sem isso o painel fica cego **exatamente durante o deploy** |
| Artefato imutável promovido | O mesmo artefato que passou em homologação. Rebuild entre ambientes invalida o teste |
| Janela e responsável pela decisão de reverter | Nome, não papel. "A equipe decide" significa que ninguém decide às 3 h |

---

## 1. Ensaio em cópia representativa

Nunca aplique migration nova direto em produção sem medir o tempo de lock numa cópia com volume real.

```bash
# 1. Restaure o backup mais recente num PostgreSQL isolado (porta e volume próprios) e anote o tempo.
#    Confira a integridade comparando contagem por tabela com a origem e a versão do Flyway: um dump que
#    restaura sem erro mas volta com menos linhas restaurou um banco diferente.

# 2. Aplique as migrations da versão nova contra a cópia e cronometre
JAVA_HOME=~/.sdkman/candidates/java/25.0.3-tem ./mvnw -pl backend spring-boot:run \
  --spring.datasource.url=jdbc:postgresql://localhost:5544/brassia
```

**O que observar, e por quê:**

- **Tempo de cada migration.** Uma que leva 40 s numa base de teste pode levar 40 min em produção.
- **`ACCESS EXCLUSIVE` em tabela grande.** `ALTER TABLE ... ADD COLUMN` com `DEFAULT` não-volátil é
  barato no PostgreSQL moderno; `ADD CONSTRAINT` sem `NOT VALID` faz varredura completa e **bloqueia
  escrita**. Em `production_measurement` ou `audit_event`, isso é indisponibilidade.
- **Índice sem `CONCURRENTLY`.** `CREATE INDEX` bloqueia escrita na tabela durante toda a criação.

### Como medir o bloqueio, e não só o tempo

Tempo de migration e tempo de escrita bloqueada **não são a mesma medida**, e é a segunda que descreve o
impacto. Uma migration de 148 ms pode ter deixado a escrita parada por 143 ms ou por 3 ms, e a diferença
está em qual lock ela pegou. `flyway_schema_history.execution_time` responde a primeira pergunta; a segunda
só se responde escrevendo na tabela **enquanto** a migration roda:

```sql
-- probe.sql — uma escrita real na tabela suspeita, repetida durante a migration.
-- O maior "Time:" do log é quanto tempo a aplicação teria ficado esperando.
\timing on
INSERT INTO production_measurement (id, brewery_id, batch_id, kind, measured_value, unit,
                                    recorded_at, recorded_by, source)
SELECT gen_random_uuid(), b.brewery_id, b.id, 'TEMPERATURE', 19.5, 'C', now(),
       gen_random_uuid(), 'MANUAL'
FROM production_batch b LIMIT 1;
\watch i=0.05
```

Rode a sonda contra a cópia, aplique as migrations uma versão por vez (`-target=`) e guarde o maior tempo
por versão. Aplicar tudo de uma vez mede o conjunto e não diz **qual** migration parou a escrita — que é
justamente a que precisa ser reescrita ou movida para janela.

---

## 2. Deploy

1. **Anuncie a janela.** Mesmo deploy sem downtime muda latência durante o rollout.
2. **Aplique as migrations** — pela aplicação subindo, que é como o Flyway roda aqui.
3. **Suba uma instância** e observe antes de escalar:
   - `/actuator/health` responde 200
   - taxa de erro por endpoint no painel (precisa da credencial do coletor)
   - `flyway_schema_history`: a última linha tem `success = true`
4. **Escale o restante** só depois que a primeira instância estiver estável por alguns minutos.

---

## 3. Quando algo dá errado

### Árvore de decisão

```
A aplicação nova está falhando?
├── SIM, e o schema novo é compatível com a versão anterior (expand)
│   └── ROLLBACK DA APLICAÇÃO. Volte o artefato. O schema fica. É o caminho normal.
│
├── SIM, e a versão anterior NÃO entende o schema novo
│   └── FORWARD-FIX. Não há volta: publique correção. Ver abaixo.
│
└── NÃO — os dados é que estão errados
    └── FORWARD-FIX com migration corretiva. Restaurar backup DESCARTA tudo que
        entrou desde o backup, e quase nunca é o que se quer.
```

### Rollback da aplicação

```bash
# Promova o artefato anterior. Nenhuma ação no banco.
kubectl rollout undo deployment/brassia-api      # ou o equivalente do seu orquestrador
```

Funciona porque expand/contract garante que a versão anterior continua entendendo o schema — **desde que
a release não tenha incluído um `contract`**. Migration que remove coluna ou aperta constraint quebra a
versão anterior, e aí este caminho não existe. É por isso que remoção vai em release **posterior**,
separada da que parou de usar a estrutura.

### Forward-fix de banco

Quando não dá para voltar:

1. **Pare a escrita** na área afetada, se der (feature flag; não use autorização para isso).
2. **Escreva a migration corretiva** como uma migration nova, com número novo.
   **Migration publicada não é editada** — editar uma que já rodou faz o checksum divergir e o Flyway
   recusa subir, em produção, no meio do incidente.
3. **Ensaie na cópia restaurada** antes de aplicar. Sempre. Inclusive às 3 h.
4. Aplique e confirme por consulta, não por ausência de erro.

### Restaurar backup — o último recurso

Só quando houve **perda ou corrupção de dados** que a correção não alcança.

Valide o backup numa cópia isolada **antes** de apontar produção para ele — o procedimento inteiro está
em `restore-drill.md`, com a conferência de integridade que separa "o dump abriu" de "o backup está
íntegro". Restaurar direto sobre produção um backup que não se sabe íntegro troca um incidente por dois.

Custo real: tudo que entrou entre o backup e o incidente **se perde** — é a definição do RPO. Decisão de
pessoa nomeada, nunca automática.

**Ordem de grandeza do tempo** (execução de 2026-08-19, banco de 1,5 GB em máquina de desenvolvimento):
restauração 45 s, aplicação de pé 11 s depois. Produção tem outro volume e outro disco — o número serve
para dimensionar a decisão, não para prometer a janela.

---

## 4. Depois

- [ ] Relatório do ensaio de restauração anexado à release
- [ ] Medição de jornadas (`infra/perf/measure-journeys.sh`) comparada à do release anterior
- [ ] Release notes com histórias, migrations, mudanças de contrato e riscos
- [ ] Se houve incidente: o que faltava neste runbook? Corrija **aqui**, agora.

---

## Registro de ensaios

| Data | Versão | Migrations | Maior lock | RTO medido | Resultado |
|---|---|---|---|---|---|
| 2026-08-10 | `dc80b4e` (sprints 15–16) | V98 → V109 | **143 ms** — escrita em `production_measurement` durante a `V100` | **9,8 s** (retorno da versão anterior até `/actuator/health` 200) | OK — ver ensaio 2026-08-10 abaixo |

> Tabela vazia é um estado honesto: significa que nenhum ensaio foi feito ainda. Preenchê-la é o que
> transforma este runbook de documento em controle — `REL-004` só fecha com pelo menos uma linha aqui.

### Ensaio 2026-08-10 — o que foi medido e o que ficou de fora

**Ambiente.** PostgreSQL 18.4 em contêiner local, isolado na porta 5544 — **não é cópia de produção**, e
essa é a limitação que acompanha todo número abaixo. Baseline em `V97` (release anterior, commit `bcdbd09`),
depois `infra/perf/seed-representative-dataset.sql` com 3.000 lotes, **1.500.000 medições** e 1.000.000 de
eventos de auditoria — as três tabelas que crescem sem teto. Migrations aplicadas uma por vez com
`flyway -target=`, com a sonda de escrita acima rodando durante cada uma.

**Uma migration destoou, e só uma.** Onze das doze ficaram entre 15 ms e 52 ms, com bloqueio de escrita
indistinguível do ruído da sonda (3–13 ms, contra ~6 ms de piso). A `V100` levou **148 ms** e parou a
escrita por **143 ms**.

**O custo não estava onde a leitura sugere.** A `V100` faz duas coisas: `ADD COLUMN` sem default e um
índice único **parcial** (`WHERE client_request_id IS NOT NULL`). O predicado exclui todas as 1,5 milhão de
linhas existentes — o índice nasce vazio. Repetindo só o `CREATE INDEX` isolado: **104,6 ms**, com a sonda
bloqueada por 59 ms. **O índice parcial é barato no que grava e caro no que varre**: o predicado decide o
tamanho do índice, não o trabalho de construí-lo. A tabela é lida inteira de qualquer jeito, com lock que
bloqueia escrita durante toda a leitura.

**A consequência prática é de escala, não deste número.** 143 ms em 1,5 milhão de linhas é aceitável em
qualquer janela. O mesmo `CREATE INDEX` numa `production_measurement` com 15 milhões custa ~1,4 s de escrita
parada, e é aí que ele precisa de `CONCURRENTLY` — que o Flyway exige rodar fora de transação
(`-- flyway:executeInTransaction=false`). **Não é dívida desta release**: é o gatilho para a próxima
migration que indexar essa tabela.

**O retorno da aplicação foi exercido de verdade**, não deduzido do documento. O artefato anterior
(`bcdbd09`, que conhece 97 migrations) subiu contra o schema em `V109`:

- O Flyway **avisa e prossegue** — `Schema "public" has a version (109) that is newer than the latest
  available migration (97)`. Vale saber que é WARN e não ERROR: quem vir essa linha no meio de um incidente
  vai achar que é a causa, e não é.
- O `ddl-auto: validate` do Hibernate passou: colunas e tabelas a mais não quebram validação. É exatamente
  isso que expand/contract compra, e agora está medido em vez de prometido.
- Não parou no boot — autenticou e **serviu dados de produção** (`GET /api/v1/production/batches` → 200).

**Um efeito colateral que o ensaio expôs:** o artefato anterior devolve a listagem de lotes **sem paginação**
— 988 KB para 3.000 lotes, que é a `REL-002` desfeita (p95 de 319 ms). Rollback de aplicação restaura o
comportamento antigo *inteiro*, inclusive o que foi corrigido por performance. Não impede o rollback; muda o
que se observa depois dele, e evita concluir que "o rollback deixou o sistema lento" como se fosse novidade.

**O que este ensaio não mediu**, e continua em aberto:

- **Volume e hardware de produção.** Contêiner local com dataset sintético. Os tempos escalam com a tabela,
  e o número que vale é o da cópia restaurada — o ensaio de restauração existe agora em
  `restore-drill.md`, mas rodou sob a mesma limitação: máquina de desenvolvimento, dados semeados.
- **Concorrência real.** A sonda é uma escrita por vez. Em produção há dezenas, e uma fila que se forma
  atrás do lock demora mais para drenar do que o lock durou.
- **O rollback sob carga.** O artefato anterior subiu com o banco ocioso.
