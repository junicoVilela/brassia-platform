# Checklist arquitetural para a IA

Antes de criar pacote, interface, adapter, store, evento ou nova dependência, responda:

1. A mudança pertence a qual módulo proprietário?
2. É CRUD de apoio ou domínio com regra/estado/risco?
3. Existe uma fronteira externa ou variação real que justifica porta?
4. A abstração melhora teste ou apenas muda o nome da delegação?
5. É possível resolver com Java/Angular/Spring já presentes?
6. O módulo precisa de resposta síncrona ou evento pós-commit?
7. O estado é local da tela, remoto ou compartilhado entre rotas?
8. A mudança introduz operação distribuída, lock, retry ou idempotência?
9. A versão está na matriz e é estável/compatível?
10. Há uma alternativa menor que atende a sprint sem comprometer invariantes?

Use `docs/22_ARCHITECTURE_DECISION_GUIDE.md` e `docs/26_PATTERN_SELECTION.md`. Se a escolha mudar o baseline, escreva ADR antes do código.
