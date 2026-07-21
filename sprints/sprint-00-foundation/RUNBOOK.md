# Runbook executável — Sprint 00 completa

## Resultado obrigatório

Ao final, deve existir um repositório remoto confirmado contendo projetos backend e frontend executáveis, ambiente local reproduzível, banco migrado, contratos e observabilidade básicos, pipeline verde e evidências de encerramento. Gerar apenas pastas, exemplos ou um plano não conclui esta sprint.

## Ordem de execução

### 0. Preflight e proteção do trabalho existente

1. Ler `AGENTS.md`, `.ai/PROJECT_CONTEXT.md`, `.ai/DEVELOPMENT_RULES.md`, os documentos de arquitetura/versões e toda esta pasta.
2. Inspecionar diretório, Git, remotos, arquivos não rastreados e alterações existentes.
3. Preservar qualquer trabalho do usuário; não limpar, sobrescrever ou reverter mudanças alheias.
4. Resolver as entradas de `REPOSITORY_INPUTS.md`. Se faltarem dados que alterem o destino remoto, perguntar uma única vez e aguardar.
5. Registrar decisões relevantes em ADR; não inventar nome, proprietário, visibilidade ou licença.

### 1. FND-000 — Criar ou vincular o repositório

1. Validar a sessão autenticada no provedor escolhido sem revelar credenciais.
2. Consultar se o repositório já existe. Criá-lo vazio somente quando inexistente e no destino confirmado.
3. Inicializar Git com `main` quando o diretório ainda não for um repositório.
4. Configurar `origin` por SSH ou HTTPS autenticado, sem credencial embutida na URL.
5. Criar `.gitignore`, `.gitattributes`, `.editorconfig`, `README.md`, `LICENSE` quando aprovada e `.env.example` sem segredos.
6. Criar o commit `chore: bootstrap repository` e publicar `main` com rastreamento.
7. Se o remoto já contiver histórico, fazer `fetch`, comparar e integrar de forma não destrutiva; bloquear e explicar qualquer conflito de propriedade ou histórico.

### 2. FND-006 — Fixar ferramentas e versões

1. Verificar pré-requisitos e registrar comandos de instalação quando algum estiver ausente.
2. Fixar Java 25 LTS, Spring Boot 4.1, Spring Modulith 2.1, Maven Wrapper 3.9.16, PostgreSQL 18, Node 24 LTS e Angular 22.
3. Gerar e versionar Maven Wrapper, lockfile do npm e arquivo de versão do Node compatível com a ferramenta adotada.
4. Não usar releases preview/RC como baseline. Não atualizar uma dependência isoladamente se quebrar o conjunto compatível.

### 3. FND-001 — Gerar os projetos reais

1. Criar o backend Spring Boot `brassia-api` em `backend/`, com `groupId` `br.com.brew`, pacote raiz `br.com.brew.brassia`, Actuator, Validation, Security, Data JPA, Flyway, PostgreSQL, Testcontainers, Spring Modulith e dependências de teste.
2. Criar o frontend Angular standalone `brassia-web` em `frontend/`, com routing, Signals, modo zoneless, Vitest, lint/format e estrutura feature-first.
3. Aplicar a estrutura de `docs/18_REPOSITORY_STRUCTURE.md`; usar `scaffold/` como referência, não como substituto dos projetos gerados.
4. Criar módulos/pacotes vazios apenas quando necessários para provar fronteiras. Não implementar funções das sprints seguintes.
5. Adicionar testes de arquitetura para limites Modulith e regras de importação do frontend.
6. Garantir que os comandos documentados de build e teste funcionem pelos wrappers, sem depender de instalações globais além dos pré-requisitos declarados.

### 4. FND-002 — Ambiente local reproduzível

1. Criar `compose.yaml` com PostgreSQL 18 fixado, health check, volume nomeado e rede local.
2. Criar configurações `local`, `test` e `prod`; somente `local` pode usar adapter de filesystem.
3. Criar migration Flyway mínima e validar execução desde banco vazio.
4. Disponibilizar health/readiness do backend e uma tela/rota inicial do frontend que indique disponibilidade da API sem expor detalhes internos.
5. Documentar subida, parada, reset controlado e diagnóstico. Reset destrutivo exige alvo local explícito e confirmação.

### 5. FND-004 — Contrato HTTP e erros

1. Implementar Problem Details RFC 9457 com `type`, `title`, `status`, `detail` seguro, `instance`, `code`, `traceId` e erros de campo quando aplicável.
2. Cobrir validação, autenticação, autorização, recurso inexistente, conflito e erro inesperado.
3. Publicar OpenAPI mínimo e testes de contrato. Stack trace, SQL e dado sensível nunca saem na resposta.

### 6. FND-005 — Auditoria e observabilidade

1. Propagar ou gerar `traceId` e incluí-lo na resposta de erro e nos logs estruturados.
2. Configurar métricas e endpoints operacionais com exposição mínima.
3. Criar a porta de auditoria e um adapter inicial testável, sem antecipar o domínio de segurança.
4. Mascarar cabeçalhos e campos sensíveis; adicionar testes que detectem vazamento conhecido.

### 7. FND-003 — Integração contínua

1. Criar pipeline nativo do provedor remoto para backend, frontend, arquitetura, migrations, contratos e verificação de segredos/dependências.
2. Usar versões fixas ou referências imutáveis quando suportadas e permissões mínimas do token da CI.
3. Habilitar cache somente com chaves que incluam lockfiles; cache nunca substitui teste.
4. Fazer push, acompanhar a execução e corrigir o pipeline até ficar verde.
5. Configurar proteção de `main` depois que os checks existirem. Se a edição não for permitida pelo plano/provedor, documentar o passo manual exato sem fingir conclusão.

### 8. FND-007 — Encerramento verificável

1. Executar build, lint, testes unitários, integração, arquitetura, migration e contrato localmente.
2. Clonar o repositório em diretório temporário limpo e repetir o procedimento documentado usando somente arquivos versionados e variáveis de exemplo.
3. Confirmar que nenhum segredo, binário de build ou arquivo local indevido foi rastreado.
4. Atualizar `STATUS.md` e `ACCEPTANCE.md` com commit, comandos, resultados, URL/identificador da CI e bloqueios reais.
5. Criar commits pequenos e compreensíveis; criar o commit final `chore: complete sprint 00 foundation` e fazer push sem reescrever histórico.
6. Confirmar CI remota verde e entregar relatório com repositório/branch, estrutura criada, comandos de execução, testes, decisões, riscos e próximo item recomendado (`BRW-001`/`SEC-001`).

## Política de continuidade

Após as entradas remotas serem confirmadas, executar as etapas em sequência sem pedir confirmação para cada arquivo, comando, commit ou push normal deste escopo. Pausar apenas diante de credencial ausente, destino remoto ambíguo, conflito com trabalho existente, operação destrutiva, cobrança, mudança de visibilidade ou decisão arquitetural que altere o produto.

Não declarar a sprint concluída por percentual ou intenção. Um item só pode ser marcado como concluído com evidência verificável; caso contrário, registrar `BLOQUEADO` e o comando/decisão necessária para desbloqueá-lo.
