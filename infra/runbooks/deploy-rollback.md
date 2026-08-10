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
| Backup restaurável **de hoje** | `infra/backup/restore-drill.sh` verde nas últimas 24 h |
| Credencial do coletor de métricas | `/actuator/prometheus` passou a exigir autenticação (REL-003). Sem isso o painel fica cego **exatamente durante o deploy** |
| Artefato imutável promovido | O mesmo artefato que passou em homologação. Rebuild entre ambientes invalida o teste |
| Janela e responsável pela decisão de reverter | Nome, não papel. "A equipe decide" significa que ninguém decide às 3 h |

---

## 1. Ensaio em cópia representativa

Nunca aplique migration nova direto em produção sem medir o tempo de lock numa cópia com volume real.

```bash
# Cópia isolada a partir do backup mais recente
infra/backup/restore-drill.sh          # deixa um Postgres restaurado; anote o RTO

# Aplique as migrations da versão nova contra a cópia e cronometre
JAVA_HOME=~/.sdkman/candidates/java/25.0.3-tem ./mvnw -pl backend spring-boot:run \
  --spring.datasource.url=jdbc:postgresql://localhost:5544/brassia
```

**O que observar, e por quê:**

- **Tempo de cada migration.** Uma que leva 40 s numa base de teste pode levar 40 min em produção.
- **`ACCESS EXCLUSIVE` em tabela grande.** `ALTER TABLE ... ADD COLUMN` com `DEFAULT` não-volátil é
  barato no PostgreSQL moderno; `ADD CONSTRAINT` sem `NOT VALID` faz varredura completa e **bloqueia
  escrita**. Em `production_measurement` ou `audit_event`, isso é indisponibilidade.
- **Índice sem `CONCURRENTLY`.** `CREATE INDEX` bloqueia escrita na tabela durante toda a criação.

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

```bash
infra/backup/restore-drill.sh    # valide o backup ANTES de apontar produção para ele
```

Custo real: tudo que entrou entre o backup e o incidente **se perde** — é a definição do RPO. Decisão de
pessoa nomeada, nunca automática.

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
| | | | | | |

> Tabela vazia é um estado honesto: significa que nenhum ensaio foi feito ainda. Preenchê-la é o que
> transforma este runbook de documento em controle — `REL-004` só fecha com pelo menos uma linha aqui.
