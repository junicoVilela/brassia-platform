# Playbook de prompts para implementação

## Implementar história

Leia `AGENTS.md`, os documentos citados e a sprint ativa. Analise a história `<ID>`. Antes de editar, apresente: invariantes, módulos afetados, contrato, dados, riscos e testes. Implemente uma fatia vertical mínima. Ao final, execute os testes e relate arquivos, decisões e pendências.

## Revisar mudança

Revise o diff segundo `.ai/REVIEW_CHECKLIST.md`. Procure falhas de domínio, isolamento multi-tenant, precisão, concorrência, autorização, migrations, contratos, segurança e lacunas de teste. Não altere código até classificar os achados por severidade.

## Corrigir bug

Reproduza com teste falhando, identifique causa raiz e corrija no menor ponto responsável. Não enfraqueça validações nem altere contratos sem explicação. Execute regressão do módulo.

## Criar migration

Modele a mudança, volume esperado, constraints, índices, compatibilidade e rollback operacional. Gere migration nova e teste banco vazio e banco na versão anterior.
