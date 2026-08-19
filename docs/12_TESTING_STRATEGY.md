# Estratégia de testes

- Unitário: valores, agregados, políticas, fórmulas e máquinas de estado.
- Integração: adapters, JPA, Flyway, Outbox e PostgreSQL via Testcontainers.
- Arquitetura: Spring Modulith `verify()` e ArchUnit apenas para regras adicionais.
- Contrato: OpenAPI, Problem Details RFC 9457 e schemas de evento/IA.
- Frontend: Vitest para unidade/componentes/formulários e Playwright no fluxo crítico.
- Segurança: autorização negativa, tenant cruzado, upload e rate limit.
- IA: datasets dourados, prompt injection, fonte ausente e JSON inválido.

O pipeline bloqueia merge quando unitários, integração do módulo, arquitetura, lint, migrations ou contrato falham.

## Tempo em teste: data fixa só onde o tempo não anda

Um teste que grava uma **data fixa** e exercita código que compara contra `Instant.now()` não é
determinístico — ele é **datado**. Passa hoje, falha num dia futuro, e falha sozinho, sem ninguém ter
tocado em nada. Já aconteceu duas vezes:

- `YeastReuseIT` ancorava a coleta de levedura em `2026-07-31` e o parâmetro se chamava `ageDays`. Ele
  deixou de significar idade assim que o calendário andou, e o teste quebrou o build em 2026-08-14.
- Onze ITs planejavam o envase para `2026-08-20T09:00Z` e liberavam a limpeza da linha "agora". A regra
  é que a liberação seja **anterior** ao início planejado; em 2026-08-20 a ordem se inverteria e todos
  passariam a ser recusados com `line_not_clean`.

A regra:

- **Cenário relativo a agora → âncora relativa a agora.** "Coleta de 10 dias", "envase daqui a uma hora",
  "manutenção durante a janela" se escrevem com `Instant.now().plus/minus(...)`.
- **Data fixa vale quando a comparação é entre datas fixas.** Duas janelas de agenda que se sobrepõem,
  um filtro `from`/`to` no passado — nada disso envelhece, porque o relógio não participa.
- **Relógio injetado quando o teste precisa viajar no tempo.** `FrequencySweepIT` constrói o serviço com
  `Clock.fixed(now + 5h)` em vez de esperar cinco horas ou adulterar a data no banco — adulterar o banco
  testaria um estado que o sistema nunca produz.

## Mock inventado concorda com o defeito

Um dublê escrito a partir da **leitura do código** — e não da resposta que a stack devolve — não testa
nada: ele reproduz a suposição de quem escreveu o código, e por construção concorda com ela. Quando a
suposição está errada, o teste fica verde sobre um defeito.

Já aconteceu duas vezes, com o mesmo formato. O `problemDetailsInterceptor` desembrulha o corpo do erro:
`code`, `detail` e as extensões chegam ao `error:` do subscribe no **primeiro nível**. Ler `e.error?.code`
devolve `undefined` sem erro de compilação, a tela cai calada na mensagem genérica, e o mock — escrito
como `{ error: { code } }` — concorda. A `TRC-001` corrigiu sete stores assim; três sprints depois havia
outros oito, e quem pegou foi de novo o E2E contra a stack real.

A regra:

- **O dublê copia a resposta real, e não o que se imagina dela.** Quando não se sabe qual é, a forma
  barata de descobrir é uma chamada de verdade — um IT, ou o próprio E2E.
- **O TestBed monta o que a aplicação monta.** Um spec que registra `provideHttpClient()` sem os
  interceptors da aplicação exercita uma cadeia que não existe em lugar nenhum, e o que ele garante não
  vale para nenhum usuário.
- **O que só a stack real prova, prove na stack real.** Contrato de erro, formatação de locale e o que a
  tela mostra depois de uma recusa não sobrevivem a dublê: nos três, o teste de unidade e o defeito
  cabem confortavelmente juntos.

## O plano de testes é conferido antes de encerrar

Duas sprints foram declaradas entregues com um item do próprio `TEST_PLAN.md` por fazer — o E2E da
jornada comercial (`DEB-SAL-004`) e o do ciclo de retornáveis (`DEB-LOG-002`). Nos dois casos havia
cobertura de domínio, de integração e de store, e a ausência não apareceu em lugar nenhum: nem no
STATUS, nem no aceite.

Encerrar uma sprint inclui reler o plano de testes dela linha a linha. O que não foi feito vira débito
com identificador, e não silêncio — porque um item que some do registro não volta por conta própria.
