# ADR 0014 — BeerJSON como intercâmbio canônico

## Status

Aceita

## Contexto

A BrassIA precisa trocar receitas com diferentes ferramentas. BeerXML é amplamente reconhecido, mas não representa bem processos e estruturas modernas. BeerJSON 1.0 cobre receitas, ingredientes, estilos, equipamento, água e processos com schema versionado.

## Decisão

BeerJSON 1.0 será o contrato canônico externo. BeerXML 1.0 será mantido como adapter legado com relatório explícito de compatibilidade. DTOs de fornecedores nunca entram no domínio.

## Consequências

- importação e exportação passam por modelo canônico;
- extensões BrassIA usam namespace próprio;
- versão e licença do schema são fixadas;
- round-trip deve ser testado;
- perdas do BeerXML são exibidas ao usuário;
- conectores futuros reutilizam o mesmo pipeline.
