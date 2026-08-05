# Aceite — Sprint 12

- [x] Todas as histórias selecionadas atendem critérios específicos.
- [x] Nenhuma história posterior foi implementada parcialmente.
- [x] Testes de domínio, integração, autorização e tenant estão verdes.
- [x] OpenAPI, migrations, eventos e documentação estão consistentes.
- [x] Frontend trata loading, vazio, erro, conflito e acesso negado.
- [x] Observabilidade permite localizar a operação por traceId.
- [x] `.ai/DEFINITION_OF_DONE.md` foi executado.
- [x] Débitos e decisões restantes foram registrados, não escondidos em TODO.

## Observações para o aceite

**O item de E2E de negócio está fechado.** Ele ficou aberto no aceite da sprint 11, onde o harness
cobria navegação, sessão e integração frontend↔API, e não um fluxo completo. A jornada
`business-journey.spec.ts` percorre insumo recebido → receita publicada → ordem → lote →
transferência → linha limpa → envase executado → lote de produto acabado → expedição → genealogia na
tela → simulado medindo cobertura, contra a stack real.

**Eventos:** nenhuma história desta sprint publica evento de domínio, e é decisão registrada, não
esquecimento. Quarentena, recall e simulado não têm consumidor: o bloqueio é *derivado* na hora da
pergunta, não reativo. Publicar evento sem quem o consuma criaria um contrato para manter sem
ninguém do outro lado. Auditoria, essa sim, existe em todos os comandos.

**Quatro débitos anteriores foram fechados:** `TRC-001-B`, `TRC-001-D`, `PKG-004-A` (da sprint 10) e
`FDS-002-A`. Sete seguem abertos, listados no `STATUS.md` com critério de remoção — o aceite libera a
sprint, não os débitos.

**Ressalva a considerar antes de aceitar:** a matriz de alergênicos (`FDS-001-B`) só é aplicada no
envase. Brassagem e fermentação usam equipamento compartilhado e não consultam a matriz, porque
nenhuma das duas registra hoje qual lote ocupou o tanque antes. Uma cervejaria que produza com e sem
alergênico no mesmo fermentador tem a troca checada só na última etapa da cadeia.

- **Aceite:** **Valdemir Vilela Junior, 2026-08-05** — aceita com as ressalvas acima.
