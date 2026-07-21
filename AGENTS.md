# Instruções obrigatórias para agentes de desenvolvimento

## Antes de alterar código

1. Leia `README.md`, `docs/00_PRODUCT_IDENTITY.md`, `.ai/PROJECT_CONTEXT.md`, `.ai/DEVELOPMENT_RULES.md`, `docs/22_ARCHITECTURE_DECISION_GUIDE.md` e a pasta da sprint ativa.
2. Declare o objetivo, arquivos afetados, riscos e plano curto.
3. Confirme que a mudança pertence à sprint ativa e ao módulo proprietário.

## Regras arquiteturais

- O sistema é um monólito modular; não criar microserviços.
- Spring Modulith verifica limites por pacote; não criar um módulo Maven para cada domínio no início.
- Domínios complexos usam `domain`, `application`, `adapter/inbound`, `adapter/outbound` e `config`.
- CRUDs de apoio podem usar `api`, `application` e `infrastructure` sem portas artificiais.
- Domínio não depende de Spring, JPA, HTTP, banco, provedor de IA ou frontend.
- Um módulo não acessa repositório nem tabela pertencente a outro módulo.
- Comunicação entre módulos usa porta de aplicação, consulta publicada ou evento.
- Antes de criar uma abstração, justificar qual risco, variação, fronteira ou teste ela isola.
- Controllers apenas validam o contrato e chamam casos de uso.
- Regras cervejeiras e cálculos ficam em serviços/objetos de domínio testáveis.
- Toda tabela de negócio multi-tenant contém `brewery_id` e índices adequados.
- Valores físicos usam decimal e unidade explícita; nunca `float`/`double` para persistência de precisão.
- Datas persistidas em UTC; fuso é aplicado somente na borda de apresentação.

## Regras de segurança e domínio

- Toda escrita verifica cervejaria, permissão, versão otimista e transição de estado.
- Medições, movimentos de estoque, eventos de auditoria e snapshots não são apagados fisicamente.
- Receita publicada é imutável; alteração gera nova versão.
- IA nunca altera receita, estoque, produção, limpeza ou qualidade sem comando humano explícito.
- Procedimentos químicos usam POP/FISPQ do produto; não inventar concentração ou misturas.
- Nunca registrar tokens, senhas, prompts sensíveis ou documentos completos em logs.

## Entrega mínima

- Testes unitários do domínio e integração com PostgreSQL real via Testcontainers.
- Contrato OpenAPI e Problem Details RFC 9457 atualizados.
- Migration Flyway idempotente no histórico e testada em banco limpo.
- Autorização negativa e isolamento entre cervejarias testados.
- Auditoria, métricas e logs estruturados para comandos críticos.
- Relatório final com arquivos alterados, testes executados, riscos e pendências.
