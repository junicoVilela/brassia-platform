# ADR 0013 — Dados cervejeiros como datasets versionados

## Status

Aceita

## Contexto

Estilos, ingredientes e perfis de água mudam, possuem fontes e licenças diferentes e são utilizados por receitas históricas que não podem mudar retroativamente.

## Decisão

A BrassIA mantém catálogo interno. Fontes externas entram em staging imutável, passam por validação, normalização, revisão e publicação. Cada publicação produz dataset versionado com checksum, vigência, licença e proveniência. Receita e lote guardam snapshot da referência aplicada.

## Consequências

- cálculo e produção não dependem de disponibilidade externa;
- atualizações são auditáveis e reversíveis por nova publicação;
- curadoria e licença tornam-se requisitos de produto;
- é necessário armazenamento adicional para payload bruto e snapshots;
- duplicidades não podem ser mescladas apenas por nome.
