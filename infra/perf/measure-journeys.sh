#!/usr/bin/env bash
#
# REL-002 — Mede as jornadas críticas contra as metas NFR.
#
# Metas (docs/15_NONFUNCTIONAL_REQUIREMENTS.md):
#   leitura simples ... p95 < 500 ms
#   comando ........... p95 < 1000 ms (sem integração externa)
#
# POR QUE p95 E NÃO MÉDIA. A média esconde a cauda: cem requisições de 50 ms e cinco de 4 s dão média
# de 230 ms — dentro da meta — enquanto uma pessoa a cada vinte espera quatro segundos. É a cauda que
# a pessoa percebe, e é ela que a meta descreve.
#
# POR QUE MEDIR AQUECIDO. A primeira chamada de cada rota paga JIT, primeira conexão de pool e cache de
# plano frio. Incluí-la mediria a partida da aplicação, não o regime. O aquecimento é descartado e o
# número de descartes é declarado no relatório — esconder isso seria escolher o número que agrada.
#
# Uso:
#   infra/perf/measure-journeys.sh
#   BASE_URL=https://homolog.exemplo REPETICOES=200 infra/perf/measure-journeys.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-admin@brassia.local}"
PASSWORD="${PASSWORD:-admin-local-123}"
REPETICOES="${REPETICOES:-60}"
AQUECIMENTO="${AQUECIMENTO:-5}"
COOKIES="$(mktemp)"
# Opcional: com acesso ao banco, o relatório declara o VOLUME de cada tabela medida. Sem isso, um p95
# de 5 ms sobre tabela vazia é indistinguível de um p95 de 5 ms sobre um milhão de linhas — e só o
# segundo significa alguma coisa.
DB_URL="${DB_URL:-}"
VOLUME_MINIMO="${VOLUME_MINIMO:-1000}"
REPORT="${REPORT:-$(mktemp -d)/perf-report.txt}"

META_LEITURA_MS=500
META_COMANDO_MS=1000

trap 'rm -f "$COOKIES"' EXIT

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }

# --- autenticação ---
# O endpoint responde 204 e entrega o token no cookie XSRF-TOKEN — não no corpo. Ler do corpo devolve
# string vazia e o login falha com 403, que é um sintoma que não aponta para a causa.
csrf() {
  curl -s -o /dev/null -c "$COOKIES" -b "$COOKIES" "$BASE_URL/api/v1/security/csrf"
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIES" | tail -1
}

log "autenticando em $BASE_URL"
TOKEN="$(csrf)"
LOGIN_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -c "$COOKIES" -b "$COOKIES" \
  -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  "$BASE_URL/api/v1/security/login")
# Falhar aqui e alto: sem sessão, todas as jornadas medem o tempo de responder 401 — números ótimos
# e completamente sem sentido, que passariam pela meta sem ninguém perceber.
[ "$LOGIN_STATUS" = "200" ] || { log "login falhou (HTTP $LOGIN_STATUS)"; exit 1; }
TOKEN="$(csrf)"

# Tempo total de UMA requisição, em milissegundos.
tempo_ms() {
  local metodo="$1" caminho="$2" corpo="${3:-}"
  local args=(-s -o /dev/null -w '%{time_total}' -b "$COOKIES" -c "$COOKIES"
              -H "X-XSRF-TOKEN: $TOKEN" -X "$metodo")
  [ -n "$corpo" ] && args+=(-H 'Content-Type: application/json' -d "$corpo")
  local s; s=$(curl "${args[@]}" "$BASE_URL$caminho")
  python3 -c "print(f'{float('$s')*1000:.1f}')"
}

# Executa a jornada N vezes e resume. Cada linha do relatório é uma jornada.
# Linhas na tabela que sustenta a jornada, quando há acesso ao banco.
contar() {
  local tabela="$1"
  [ -z "$DB_URL" ] && { echo "-1"; return; }
  docker run --rm --network host postgres:18 psql "$DB_URL" -Atq \
    -c "SELECT count(*) FROM $tabela" 2>/dev/null || echo "-1"
}

medir() {
  local nome="$1" tipo="$2" metodo="$3" caminho="$4" corpo="${5:-}" tabela="${6:-}"
  local i amostras=()
  for ((i=0; i<AQUECIMENTO; i++)); do tempo_ms "$metodo" "$caminho" "$corpo" >/dev/null; done
  for ((i=0; i<REPETICOES; i++)); do amostras+=("$(tempo_ms "$metodo" "$caminho" "$corpo")"); done

  local meta=$META_LEITURA_MS
  [ "$tipo" = "comando" ] && meta=$META_COMANDO_MS

  local linhas="?"
  [ -n "$tabela" ] && linhas="$(contar "$tabela")"

  printf '%s\n' "${amostras[@]}" | python3 -c "
import sys
v = sorted(float(x) for x in sys.stdin)
n = len(v)
# p95 pelo método do índice mais próximo, o mesmo que Prometheus e Grafana usam por padrão — para que
# o número medido aqui seja comparável ao que o painel mostra depois.
p = lambda q: v[min(n-1, max(0, round(q*n) - 1))]
p95 = p(0.95)
meta = $meta
linhas = int('$linhas') if '$linhas' not in ('?', '') else -1
status = 'OK ' if p95 < meta else 'FALHA'
# Um p95 dentro da meta sobre tabela quase vazia NÃO é evidência de que a meta é atendida. Marcar isso
# é o que impede o relatório de virar sete linhas verdes que ninguém questiona.
if status == 'OK ' and 0 <= linhas < $VOLUME_MINIMO:
    status = 'VAZIO'
vol = 'sem acesso ao banco' if linhas < 0 else f'{linhas} linhas'
print(f'{status} | {\"$nome\":38} | {\"$tipo\":8} | p50 {p(0.5):7.1f} | p95 {p95:7.1f} | max {v[-1]:7.1f} | meta {meta} | {vol}')
"
}

log "medindo ($REPETICOES repetições, $AQUECIMENTO de aquecimento)"
{
  echo "MEDIÇÃO DE JORNADAS CRÍTICAS — REL-002"
  echo "executado em: $(date -u +%Y-%m-%dT%H:%M:%SZ)   alvo: $BASE_URL"
  echo "repetições: $REPETICOES   aquecimento descartado: $AQUECIMENTO"
  echo "volume mínimo para a medição contar como representativa: $VOLUME_MINIMO linhas"
  echo
  medir "listar lotes"                 leitura GET  "/api/v1/production/batches"          "" production_batch
  medir "listar receitas"              leitura GET  "/api/v1/recipes"                     "" recipe
  medir "listar reclamações de campo"  leitura GET  "/api/v1/field-feedback/complaints"   "" field_complaint
  medir "listar operações de blend"    leitura GET  "/api/v1/blends"                      "" blend_operation
  medir "listar experimentos"          leitura GET  "/api/v1/experiments"                 "" experiment_plan
  medir "listar otimizações"           leitura GET  "/api/v1/optimizations"               "" optimization_run
  medir "auditoria (tabela que mais cresce)" leitura GET "/api/v1/audit/events?page=0&size=20" "" audit_event
  echo
  echo "Metas: leitura p95 < ${META_LEITURA_MS} ms | comando p95 < ${META_COMANDO_MS} ms"
  echo "(docs/15_NONFUNCTIONAL_REQUIREMENTS.md)"
  echo
  echo "LEGENDA"
  echo "  OK    — dentro da meta, com volume suficiente para a medição significar algo."
  echo "  VAZIO — dentro da meta, mas sobre tabela com menos de $VOLUME_MINIMO linhas. NÃO é evidência:"
  echo "          semeie a tabela (infra/perf/seed-representative-dataset.sql) e meça de novo."
  echo "  FALHA — p95 acima da meta. É o único estado que exige correção antes do release."
} | tee "$REPORT"

log "relatório em $REPORT"
grep -q '^FALHA' "$REPORT" && { log "há jornada fora da meta"; exit 1; } || true
