# Ensaio de restauração — REL-001

**Backup sem teste de restauração não é controle válido** (`docs/21_DATA_RETENTION_BACKUP.md`). Este
runbook é o teste. Ele existe para que a resposta a *"o procedimento de restauração funciona?"* deixe de
ser "ninguém nunca tentou".

**O que ele mede:** o **RTO** — quanto tempo leva, a partir de um dump, até a aplicação servir dado de
novo. **O que ele não mede: o RPO**, que depende de política de backup — frequência, retenção, cópia
fora do ambiente. Enquanto essa política não existir, não há RPO, e nenhum ensaio o inventa.

**A execução mais recente está no fim deste arquivo**, com números e condições declaradas. Reexecute e
acrescente uma seção nova a cada release; não sobrescreva a anterior — a série é que mostra a curva.

---

## Duas lições já pagas — não as reaprenda

Estas custaram uma execução do ensaio anterior, o que foi removido na `DEC-REL-008`:

- **`n_live_tup` não confere integridade.** É estimativa do autovacuum e volta **zerada** num banco
  recém-restaurado: a conferência acusaria divergência em todas as tabelas, sempre. Use `COUNT(*)`
  exato, via `query_to_xml` para varrer todas as tabelas de uma vez.
- **As ferramentas do PostgreSQL rodam em container da mesma imagem do servidor.** Um `pg_dump` de
  versão diferente da do servidor falha de formas que parecem corrupção e não são.

---

## 0. Antes de começar

O ensaio precisa de: o dump a validar, uma porta livre para a cópia, e **espaço em disco para o banco
restaurado inteiro** — a cópia não é comprimida, ao contrário do dump.

> **A cópia é isolada de propósito.** Porta e volume próprios, e nunca o banco de produção como destino.
> Restaurar direto sobre produção um backup que não se sabe íntegro troca um incidente por dois.

## 1. Congele a escrita na origem

Um dump tirado com a aplicação escrevendo mede um estado que não existiu em instante nenhum. Pare a
aplicação, ou tire o dump de uma réplica.

```bash
# Confirme que ninguém está escrevendo antes de seguir.
docker exec brassia-postgres psql -U brassia_app -d brassia -At -c \
  "SELECT count(*) FROM pg_stat_activity WHERE datname='brassia' AND state='active' AND pid<>pg_backend_pid();"
```

## 2. Linha de base de integridade, na origem

```bash
cat > /tmp/counts.sql <<'SQL'
SELECT table_name,
       (xpath('/row/cnt/text()',
              query_to_xml(format('SELECT count(*) AS cnt FROM %I.%I', table_schema, table_name),
                           false, true, '')))[1]::text::bigint AS linhas
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
SQL

docker exec -i brassia-postgres psql -U brassia_app -d brassia -At -F'|' -f - \
  < /tmp/counts.sql > /tmp/counts-origem.txt
```

## 3. Dump, cronometrado

```bash
/usr/bin/time -f "TEMPO_DUMP=%e s" \
  docker exec brassia-postgres pg_dump -U brassia_app -d brassia -Fc > /tmp/brassia.dump
ls -lh /tmp/brassia.dump
```

Anote **tempo e tamanho**. O tamanho do dump comprimido versus o do banco é o que permite estimar o
custo de transferência para a cópia fora do ambiente.

## 4. Restaure numa cópia isolada — **este tempo é o RTO do banco**

```bash
docker rm -f brassia-restore-test 2>/dev/null
docker run -d --name brassia-restore-test \
  -e POSTGRES_DB=brassia -e POSTGRES_USER=brassia_app -e POSTGRES_PASSWORD='...' \
  -p 5544:5432 postgres:18                       # MESMA imagem do servidor de origem

until docker exec brassia-restore-test pg_isready -U brassia_app -d brassia; do sleep 2; done

/usr/bin/time -f "TEMPO_RESTORE=%e s" \
  docker exec -i brassia-restore-test pg_restore -U brassia_app -d brassia \
    --no-owner --no-privileges < /tmp/brassia.dump
```

`--no-owner --no-privileges` porque a cópia não tem os papéis da origem; os donos reais são recriados
pelo provisionamento do ambiente de destino, não pelo dump.

## 5. Confira a integridade — `COUNT(*)`, não estimativa

```bash
docker exec -i brassia-restore-test psql -U brassia_app -d brassia -At -F'|' -f - \
  < /tmp/counts.sql > /tmp/counts-copia.txt

diff /tmp/counts-origem.txt /tmp/counts-copia.txt && echo "íntegro: todas as tabelas conferem"
```

**Uma divergência aqui invalida o backup**, não o ensaio. Um dump que restaura sem erro e volta com
menos linhas restaurou um banco diferente.

Confira também a versão do schema nos dois lados:

```bash
for c in brassia-postgres brassia-restore-test; do
  docker exec $c psql -U brassia_app -d brassia -At -c \
    "SELECT count(*) FROM flyway_schema_history WHERE success;"
done
```

## 6. Suba a aplicação contra a cópia — a prova que os passos anteriores não dão

Linhas restauradas não são banco utilizável. Este passo é o que separa "o dump abriu" de "o sistema
voltaria". O Flyway valida o schema no arranque: se a cópia divergir das migrations, a aplicação
**recusa subir**, e é isso que se quer descobrir aqui, não num incidente.

```bash
java -jar backend/target/brassia-api-*.jar --spring.profiles.active=local \
  --spring.datasource.url=jdbc:postgresql://localhost:5544/brassia

curl -sf http://localhost:8080/actuator/health
```

Depois **leia um dado de verdade** pela aplicação — uma listagem paginada serve. Health verde diz que a
conexão abriu; só a leitura diz que o dado chegou.

## 7. Objetos em disco — a outra metade

O banco é metade do estado. A outra é o armazenamento de objetos (`brassia.storage.local-path`, por
padrão `./data/objects`): anexos, dossiês em PDF, rótulos.

```bash
du -sh backend/data/objects
tar czf /tmp/objects.tar.gz -C backend/data objects     # cronometre também
```

**Banco e objetos precisam ser restaurados ao mesmo ponto no tempo.** Um dossiê que aponta para um
objeto que o backup de objetos não tinha ainda é um buraco que só aparece quando alguém abre o PDF.

## 8. Registre — e some ao histórico

Acrescente uma seção "Execução" no fim deste arquivo com: data, tamanho do banco, tempos de dump e
restauração, resultado da conferência, e **as condições em que rodou**. As condições não são
formalidade: um RTO medido em máquina de desenvolvimento não é promessa operacional, e o registro tem
de dizer isso por escrito.

## 9. Encerre a cópia

```bash
docker rm -f brassia-restore-test
```

---

## Execução — 2026-08-19

**Resultado: o procedimento funciona de ponta a ponta.** Dump, restauração, integridade, arranque da
aplicação e leitura de dado — todos verdes, sem intervenção manual em nenhum passo.

### Condições — leia antes dos números

| | |
|---|---|
| Ambiente | **Máquina de desenvolvimento**, não cópia de produção |
| Origem dos dados | **Semeados** por `infra/perf/seed-representative-dataset.sql`, não dados reais |
| PostgreSQL | 18.4, mesma imagem nos dois lados |
| Aplicação | `brassia-api-0.1.0-SNAPSHOT`, 141 migrations |

**Produção não existe** (REL-001/REL-005 seguem abertas), então não há cópia de produção para ensaiar.
Estes números são **ordem de grandeza e prova de procedimento**, não compromisso operacional.

### Volume

| | |
|---|---|
| Banco | **1.586 MB** |
| Tabelas | 204 |
| `audit_event` | 2.000.003 linhas |
| `production_measurement` | 5.500.000 linhas |
| `production_batch` | 5.000 linhas |

### Tempos

| Etapa | Tempo |
|---|---|
| `pg_dump -Fc` | **54,6 s** → 611 MB (39% do banco) |
| `pg_restore` | **44,6 s** |
| Arranque da aplicação contra a cópia | 11,3 s |
| **RTO do banco, a partir do dump** | **≈ 56 s** (restauração + arranque) |

### Integridade

- **204 de 204 tabelas conferem linha a linha** — `diff` sem divergência.
- Versão do schema idêntica: 141 migrations, topo `V141`, nos dois lados.
- Flyway validou a cópia no arranque, sem erro.
- Leitura funcional pela API: **5.000 lotes** na listagem paginada.

### O que esta execução não estabelece

- **RPO.** A política existe desde 2026-08-26 (`docs/21_DATA_RETENTION_BACKUP.md`) e fixa **5 minutos**,
  por arquivamento de WAL com PITR. Mas este ensaio **não o mede**: medir exige o arquivamento
  configurado e rodando, para observar o atraso real. Enquanto não houver ambiente, o número é
  compromisso, e não medida — e nenhum ensaio de dump o produz.
- **RTO em volume de produção.** 1,5 GB semeados neste hardware; produção tem outro tamanho e outro
  disco. O tempo escala com ambos.
- **Restauração de objetos.** Nenhum objeto havia sido gravado nesta execução
  (`backend/data/objects` ausente), então o passo 7 não foi exercitado com conteúdo. O procedimento
  está escrito; falta rodá-lo com objetos de verdade.
- **Restauração parcial ou a ponto no tempo (PITR).** Este ensaio restaura um dump inteiro. Recuperar
  até um instante escolhido exige WAL archiving, que é outra configuração e outro ensaio — e é o ensaio
  que a política de 2026-08-26 passou a exigir, porque é dele que sai o RPO medido.
