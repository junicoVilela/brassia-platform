# Plano de testes — Sprint 00

## Repositório

- Verificar `origin`, tracking de `main`, ausência de credenciais na URL e preservação do histórico remoto.
- Verificar arquivos rastreados, ignore, segredos conhecidos, chaves privadas, `.env`, dumps e artefatos de build.

## Backend e banco

- Executar Maven Wrapper desde ambiente limpo, testes unitários, Spring Modulith, integração/Testcontainers e empacotamento.
- Aplicar todas as migrations em PostgreSQL vazio; reiniciar a aplicação e confirmar que não há migration divergente.
- Testar health/readiness e respostas RFC 9457 para validação, não autenticado, acesso negado, conflito, inexistente e erro interno.
- Confirmar presença de `traceId` e ausência de stack trace, SQL, token, senha ou cabeçalho sensível em resposta/log.

## Frontend

- Instalar estritamente pelo lockfile; executar lint, unitários e build de produção.
- Testar rota inicial e estados carregando, disponível e API indisponível.
- Executar verificação de fronteiras feature-first e impedir importação proibida.

## Ambiente e CI

- Subir PostgreSQL pelo Compose, aguardar health check, iniciar backend/frontend e executar smoke test.
- Executar o pipeline remoto sem depender de arquivo local, cache prévio ou segredo não documentado.
- Repetir o procedimento em clone temporário limpo e registrar comandos, versões, duração e resultado.

## Falhas obrigatórias

- Dependência proibida entre módulos deve reprovar.
- Migration inconsistente deve reprovar.
- Formatação/lint, teste ou contrato quebrado deve bloquear a CI.
- Segredo de teste reconhecível deve ser detectado sem jamais publicar segredo real.
