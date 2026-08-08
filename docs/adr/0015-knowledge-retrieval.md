# ADR 0015 — Recuperação de conhecimento por busca textual do PostgreSQL

## Status

Aceita

## Contexto

A sprint 14 introduz RAG: o copiloto precisa responder citando POPs, manuais, fichas de segurança e
laudos. A recuperação precisa de um mecanismo de busca.

`docs/01_ARCHITECTURE.md` classifica busca vetorial como opcional e sujeita a ADR — junto de Redis,
broker e Kubernetes. `.ai/PROJECT_CONTEXT.md` pede que a arquitetura pague pelo problema que resolve, e o
projeto é operado por uma pessoa.

Duas propriedades das perguntas reais deste domínio pesam na decisão:

1. Elas são sobre **termos técnicos concretos** que aparecem literalmente nos documentos — "peracético",
   "alcalinidade", "torque de aperto", "ponto de fulgor". Não é um domínio de paráfrase.
2. O corpus é **pequeno e por cervejaria**: dezenas a poucas centenas de documentos, não milhões.

## Decisão

A recuperação usa **busca textual nativa do PostgreSQL** (`tsvector` com dicionário `portuguese`,
`plainto_tsquery`, ranqueamento por `ts_rank`, índice GIN). Nenhuma extensão nova, nenhum serviço novo.

O vetor de busca é **coluna gerada** a partir do texto do trecho, não preenchida por gatilho ou por
código: índice e conteúdo não podem divergir, e uma inserção nova que esquecesse de atualizar o índice
faria a busca não achar um documento que está lá.

Filtro de cervejaria, de permissão e de vigência entram **na mesma consulta** da busca, não em etapa
posterior.

## Motivo

- Custo operacional zero: é o banco que já existe, no backup que já existe.
- Casa com a forma das perguntas: termo técnico literal é o caso forte de busca lexical.
- `plainto_tsquery` trata a pergunta como texto e não como sintaxe, o que neutraliza `&`, `!` e
  parênteses vindos do usuário sem código de escape próprio.
- Embedding resolveria sinônimo e paráfrase — problemas reais, mas que este corpus e estas perguntas
  ainda não apresentam. Pagar por eles agora seria comprar variação que não existe.

## Consequências

- Sinônimo não coberto pelo dicionário português não é encontrado ("bomba" não acha "moto-bomba" se o
  documento só usa a segunda forma).
- Pergunta em paráfrase distante do vocabulário do documento recupera pior.
- A qualidade da recuperação depende do dicionário `portuguese`; documento em inglês é indexado com o
  dicionário errado — aceitável enquanto o corpus é local, e o `sourceUri` permite reindexar depois.
- O limite de resultados é confiável, porque o filtro de permissão participa do ranqueamento.

## Critério para adotar busca vetorial

Um novo ADR, com evidência medida — não impressão — de pelo menos um destes:

1. Taxa de "não há fonte para isto" alta em perguntas cuja resposta **existe** no corpus (falha de
   recuperação, não ausência de documento), medida sobre um conjunto de perguntas reais.
2. Corpus por cervejaria em ordem de grandeza que degrade a latência da busca lexical de forma medida.
3. Necessidade de recuperação multilíngue sobre o mesmo corpus.

O ADR precisará tratar: onde os vetores vivem (extensão `pgvector` no PostgreSQL existente antes de
qualquer serviço separado), custo e latência de gerar embedding na indexação e na consulta, o que
acontece com documento já indexado quando o modelo de embedding muda, e como o filtro de permissão
continua dentro da consulta.
