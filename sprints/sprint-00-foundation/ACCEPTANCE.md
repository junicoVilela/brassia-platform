# Aceite — Sprint 00

## Repositório e governança

- [ ] Provedor, proprietário, nome, visibilidade e branch principal foram confirmados.
- [ ] Repositório remoto correto existe; `origin` não contém credencial; `main` rastreia o remoto.
- [ ] Histórico existente foi preservado; nenhum force-push, exclusão ou sobrescrita foi usado.
- [ ] `.gitignore`, `.gitattributes`, `.editorconfig`, `.env.example` e README estão versionados; varredura não encontrou segredo.

## Projetos e arquitetura

- [ ] Backend Spring Boot/Modulith e frontend Angular são projetos reais, iniciam e compilam pelos wrappers/lockfile.
- [ ] Estrutura modular e feature-first corresponde aos documentos; testes reprovam dependências proibidas.
- [ ] Versões efetivas correspondem ao baseline estável da sprint e estão reproduzíveis na CI.
- [ ] Nenhuma funcionalidade da Sprint 01 ou infraestrutura sem necessidade foi antecipada.

## Ambiente e fundação transversal

- [ ] PostgreSQL sobe com health check; migrations funcionam desde banco vazio.
- [ ] Perfis local/test/prod e adapter local de arquivos não expõem segredo nem comportamento de desenvolvimento em produção.
- [ ] Health/readiness e integração inicial frontend/API funcionam conforme documentação.
- [ ] Problem Details RFC 9457 possui código e `traceId`, sem stack trace, SQL ou dado sensível.
- [ ] Logs estruturados, métricas e porta de auditoria têm testes e política de mascaramento.

## Qualidade, CI e encerramento

- [ ] Build, lint, unitários, integração/Testcontainers, arquitetura, migration e contrato estão verdes.
- [ ] Pipeline remoto executa os mesmos gates e a última execução está verde.
- [ ] Proteção de `main` foi aplicada ou há bloqueio externo e instrução manual precisa registrada.
- [ ] Checkout/clone limpo reproduziu configuração, build, testes e subida local usando apenas conteúdo versionado.
- [ ] `.ai/DEFINITION_OF_DONE.md` foi executado; OpenAPI, ADRs e documentação estão consistentes.
- [ ] `STATUS.md` contém commits, comandos, resultados e links/identificadores de evidência reais.
- [ ] Commit final foi publicado sem reescrever histórico; débitos têm identificador e critério de remoção.
