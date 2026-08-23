# Aceite — Sprint 18

Cada item aponta a evidência que o sustenta. **Marcado** significa que existe artefato verificável no
repositório; **aberto** significa que a prova não existe ainda, e a lacuna está nomeada. Um item marcado
sem evidência citada não é aceite — é opinião.

- [x] **Receita privada não aparece em busca, link expirado ou acesso cruzado.**
      As três portas, cada uma com seu teste. Busca: `LibraryIT#privadaNaoExisteParaQuemEstaDeFora`,
      `soOPublicoApareceNaVitrine` e `aMatrizDeVisibilidadeDecideQuemAlcanca`. Link: `ShareLinkIT` —
      `semTokenOLinkNaoAbreNadaPeloEndereco`, `tokenInventadoNaoAbreEResponde404`, `oLinkExpiradoNaoAbre`,
      `revogarCortaNaHora`, e as duas que cortam tudo de uma vez
      (`fecharAPublicacaoDerrubaTodosOsLinksDeUmaVez`, `despublicarTambemDerruba`). Acesso cruzado:
      `outraCervejariaNaoCriaNemRevogaLinkAlheio`.

- [x] **Publicação mostra autor, licença, fonte e versão.**
      `LibraryIT`: `author` congelado no ato da publicação (renomear depois não reescreve o que já saiu),
      `licenseLabel`, e a versão como chave — `aMesmaVersaoNaoSePublicaDuasVezes` responde
      `version_already_published`.

- [x] **Fork preserva linhagem sem manter referência mutável.**
      `ForkIT#oForkCriaReceitaPropriaComAtribuicaoCongelada` — a atribuição é **congelada**, e não um
      ponteiro: a receita de origem pode mudar ou sair de circulação sem alterar o que o fork diz de onde
      veio. E a licença governa: `licencaQueNaoAutorizaCopiaRecusaOFork` e
      `todosOsDireitosReservadosNaoAutorizaFork`. O fork falha **inteiro** quando falta ingrediente no
      catálogo (`faltandoIngredienteNoCatalogoOForkERecusadoInteiro`): meia receita é pior que nenhuma.

- [x] **Comentários e sugestões possuem moderação e auditoria.**
      `ContributionIT`: denunciar registra e não esconde nada; o autor vê as denúncias contra si **sem
      saber quem denunciou**; a mesma pessoa não denuncia duas vezes pelo mesmo motivo; esconder tira da
      lista **sem apagar**; o desfecho não se reescreve. A decisão sobre a sugestão é registro de
      concordância, e não alteração (`aceitarRegistraConcordanciaENaoAlteraNada`, `recusarNaoApaga`).

- [x] **Exportação pública remove custo, fornecedor, estoque e dados pessoais.**
      `LibraryIT#oRetratoPublicoNaoCarregaCervejariaNemIdentificadorDeIngrediente` afirma sobre o **corpo
      inteiro da resposta**, e não campo a campo: sem `breweryId`, sem `ingredientId`, sem `recipeId`, sem
      `cost`, sem `supplier`. E o contraponto que impede o retrato vazio: `ingredientName` continua lá —
      uma exportação que remove tudo não é privacidade, é inutilidade.
      `ContributionIT#aRespostaNaoCarregaCervejariaNemIdentificadorDeUsuario` faz o mesmo para comentários.

- [ ] **Testes de autorização, privacidade, abuso e E2E estão verdes.**
      **Parcialmente aberto.** Autorização, privacidade e abuso: verdes, com os testes citados acima —
      `semPermissaoNaoSeAvalia`, `oAutorNaoAvaliaAPropriaReceita`, `naoSeAvaliaOQueNaoSePodeLer`, e os de
      isolamento entre cervejarias em cada IT. Suíte completa: 1525 testes verdes.
      **O E2E não existe.** Não há nenhum `.spec.ts` de comunidade em `e2e/tests/` — nem biblioteca, nem
      link compartilhado, nem fork, nem contribuição. É a mesma ausência que o `DEB-SAL-004` registrou nas
      sprints 19 e 20: o plano de testes pedia jornada ponta a ponta e ela não foi escrita, e a falta não
      apareceu em relatório nenhum até alguém ir procurar. Registrado como `DEB-COM-001` no `STATUS.md`.

## O que este aceite mudou

Cinco dos seis itens estavam **provados desde a entrega** — a evidência existia e ninguém a tinha ligado ao
critério. O sexto estava meio provado, e a metade que faltava é justamente a que a sprint 19 aprendeu a
não deixar passar.
