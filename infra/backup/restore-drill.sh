#!/usr/bin/env bash
#
# REL-001 — Ensaio de restauração com medição de RPO/RTO.
#
# "Backup sem teste de restauração não é controle válido" (docs/21_DATA_RETENTION_BACKUP.md).
# Este script existe para que a frase seja verificável em vez de aspiracional.
#
# O que ele faz, nesta ordem:
#   1. tira um dump do banco de origem e ANOTA o instante — é o que define o RPO;
#   2. sobe um PostgreSQL isolado, em porta e volume próprios;
#   3. restaura o dump e CRONOMETRA — é o que define o RTO;
#   4. confere a integridade do que voltou, comparando com a origem;
#   5. escreve um relatório e derruba o ambiente isolado.
#
# O passo 4 é o que separa este ensaio de um "deu certo" otimista: um dump que restaura sem erro mas
# volta com menos linhas restaurou um banco diferente. A conferência compara contagem por tabela e o
# histórico do Flyway — se a versão de schema divergir, a aplicação sobe contra estrutura errada.
#
# Uso:
#   infra/backup/restore-drill.sh                      # usa o compose local
#   SOURCE_URL=postgres://user:pass@host:5432/brassia infra/backup/restore-drill.sh
#
# Requisitos: apenas docker.
#
# As ferramentas do PostgreSQL (pg_dump, pg_restore, psql) rodam DENTRO de um container da mesma imagem
# do servidor, e não no host. Duas razões: não exige instalar cliente na máquina de quem opera, e garante
# que a versão do cliente casa com a do servidor — pg_dump de versão menor que o servidor recusa rodar,
# e é exatamente o tipo de detalhe que só aparece no dia da restauração de emergência.
set -euo pipefail

# ATENÇÃO À SENHA NA URL: caracteres especiais precisam vir percent-encoded. A senha local é
# `brassia85!@#`, e o `@` cru faria o parser ler `#@localhost` como nome de host — o erro é
# "could not translate host name", que não parece problema de senha e custa tempo para diagnosticar.
#   ! = %21   @ = %40   # = %23
SOURCE_URL="${SOURCE_URL:-postgres://brassia_app:brassia85%21%40%23@localhost:${DB_PORT:-5433}/brassia}"
DRILL_PORT="${DRILL_PORT:-5544}"
DRILL_CONTAINER="brassia-restore-drill"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="${WORK_DIR:-$(mktemp -d)}"
DUMP_FILE="$WORK_DIR/brassia.dump"
REPORT="${REPORT:-$WORK_DIR/restore-drill-report.txt}"
PG_IMAGE="${PG_IMAGE:-postgres:18}"

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }

# Ferramentas do PostgreSQL em container, com o diretório de trabalho montado e a rede do host.
pg() {
  docker run --rm --network host -v "$WORK_DIR:$WORK_DIR" -w "$WORK_DIR" \
    -v "$SCRIPT_DIR:/drill:ro" "$PG_IMAGE" "$@"
}

cleanup() {
  # O ambiente de ensaio é descartado SEMPRE, inclusive em falha: um container de restauração deixado
  # de pé com dados de produção é um vazamento criado pelo próprio controle de segurança.
  docker rm -f "$DRILL_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# --- 1. Dump, e o instante que define o RPO -----------------------------------------------------
log "gerando dump da origem"
BACKUP_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
BACKUP_EPOCH=$(date -u +%s)
pg pg_dump --format=custom --no-owner --no-privileges --file="$DUMP_FILE" "$SOURCE_URL"
BACKUP_FINISHED_EPOCH=$(date -u +%s)
DUMP_BYTES=$(stat -c%s "$DUMP_FILE")
log "dump concluído: $((DUMP_BYTES / 1024 / 1024)) MiB em $((BACKUP_FINISHED_EPOCH - BACKUP_EPOCH))s"

# Contagens da ORIGEM, tiradas depois do dump de propósito: se a origem seguiu recebendo escrita, a
# diferença aparece aqui e é justamente o que o RPO mede. Esconder isso faria o ensaio parecer perfeito.
log "coletando contagens da origem"
SOURCE_COUNTS="$WORK_DIR/source-counts.txt"
pg psql "$SOURCE_URL" -Atq -f /drill/table-counts.sql > "$SOURCE_COUNTS"
SOURCE_FLYWAY=$(pg psql "$SOURCE_URL" -Atq -c \
  "SELECT COALESCE(MAX(installed_rank), 0) || ':' || COALESCE(MAX(version), '-') FROM flyway_schema_history WHERE success")

# --- 2. Ambiente isolado ------------------------------------------------------------------------
log "subindo PostgreSQL isolado na porta $DRILL_PORT"
docker rm -f "$DRILL_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$DRILL_CONTAINER" \
  -e POSTGRES_DB=brassia -e POSTGRES_USER=brassia_app -e POSTGRES_PASSWORD=drill \
  -p "$DRILL_PORT:5432" "$PG_IMAGE" >/dev/null

DRILL_URL="postgres://brassia_app:drill@localhost:$DRILL_PORT/brassia"
for _ in $(seq 1 60); do
  if docker exec "$DRILL_CONTAINER" pg_isready -U brassia_app -d brassia >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$DRILL_CONTAINER" pg_isready -U brassia_app -d brassia >/dev/null

# --- 3. Restauração cronometrada: o RTO ---------------------------------------------------------
log "restaurando"
RESTORE_START=$(date -u +%s)
# --exit-on-error: uma restauração "parcialmente bem-sucedida" é uma restauração falhada que se
# apresenta como sucesso. Sem esta flag, pg_restore acumula erros e sai com 0.
pg pg_restore --no-owner --no-privileges --exit-on-error --dbname="$DRILL_URL" "$DUMP_FILE"
RESTORE_END=$(date -u +%s)
RTO_SECONDS=$((RESTORE_END - RESTORE_START))
log "restauração concluída em ${RTO_SECONDS}s"

# --- 4. Conferência de integridade --------------------------------------------------------------
log "conferindo integridade"
DRILL_COUNTS="$WORK_DIR/drill-counts.txt"
pg psql "$DRILL_URL" -Atq -f /drill/table-counts.sql > "$DRILL_COUNTS"
DRILL_FLYWAY=$(pg psql "$DRILL_URL" -Atq -c \
  "SELECT COALESCE(MAX(installed_rank), 0) || ':' || COALESCE(MAX(version), '-') FROM flyway_schema_history WHERE success")

DIFF_FILE="$WORK_DIR/counts.diff"
DIVERGENCIAS=0
if ! diff -u "$SOURCE_COUNTS" "$DRILL_COUNTS" > "$DIFF_FILE"; then
  DIVERGENCIAS=$(grep -c '^[+-][^+-]' "$DIFF_FILE" || true)
fi

SCHEMA_OK="sim"
[ "$SOURCE_FLYWAY" = "$DRILL_FLYWAY" ] || SCHEMA_OK="NÃO ($SOURCE_FLYWAY na origem, $DRILL_FLYWAY no ensaio)"

# --- 5. Relatório -------------------------------------------------------------------------------
{
  echo "ENSAIO DE RESTAURAÇÃO — REL-001"
  echo "executado em: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "RPO — o quanto se perde"
  echo "  instante do backup ....... $BACKUP_STARTED_AT"
  echo "  duração do dump .......... $((BACKUP_FINISHED_EPOCH - BACKUP_EPOCH))s"
  echo "  tamanho .................. $((DUMP_BYTES / 1024 / 1024)) MiB"
  echo "  RPO efetivo .............. o intervalo entre backups. Este ensaio mede o custo de UM backup;"
  echo "                             o RPO é definido pela frequência agendada, não por este número."
  echo
  echo "RTO — o quanto se demora para voltar"
  echo "  restauração .............. ${RTO_SECONDS}s"
  echo "  RTO efetivo .............. some a este número o tempo de provisionar o host, restaurar"
  echo "                             objetos (S3) e subir a aplicação. Restaurar o banco é a maior"
  echo "                             parcela, não a única."
  echo
  echo "INTEGRIDADE"
  echo "  versão de schema idêntica  $SCHEMA_OK"
  echo "  tabelas com contagem divergente: $DIVERGENCIAS"
  if [ "$DIVERGENCIAS" -gt 0 ]; then
    echo
    echo "  Divergência não é necessariamente falha: se a origem recebeu escrita depois do dump, a"
    echo "  diferença é esperada e é a medida do RPO. Falha é divergência com a origem PARADA."
    echo
    sed -n '1,40p' "$DIFF_FILE" | sed 's/^/  /'
  fi
} | tee "$REPORT"

if [ "$SCHEMA_OK" != "sim" ]; then
  log "FALHA: versão de schema divergente — a aplicação subiria contra estrutura errada"
  exit 1
fi
log "relatório em $REPORT"
