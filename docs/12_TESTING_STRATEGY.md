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
