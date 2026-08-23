# Aceite — Sprint 20

Cada item aponta a evidência que o sustenta. **Marcado** significa que existe artefato verificável no
repositório; **aberto** significa que a prova não existe ainda, e a lacuna está nomeada. Um item marcado
sem evidência citada não é aceite — é opinião.

- [x] **Contêiner possui identidade única e histórico completo.**
      A identidade é do objeto, e a etiqueta é só como se acha ele: `ContainerIT#aEtiquetaViveEmUmConteinerSo`
      e `aEtiquetaAposentadaNaoResolveEOValorPodeSerReaproveitado` — aposentar não apaga, e o mesmo valor
      pode voltar a viver em outro keg porque o índice único é parcial. `ContainerIdentifierTest` guarda,
      por reflexão, que o identificador não tem campo de permissão, cervejaria ou token: ler um QR não
      autoriza nada.

- [x] **Conteúdo aponta para lote, validade e eventos de envase.**
      `ContainerFillIT#esvaziarNaoApagaOQueEsteveDentro` (fechar o período não apaga o vínculo),
      `oKegRespondePeloLoteDoDiaCerto` (o mesmo keg carrega um lote em março e outro em abril) e
      `oEnchimentoEntraNaGenealogia` (o contêiner é **nó**, e não atributo do lote).

- [x] **Movimentação concorrente não posiciona o item em dois locais.**
      `ContainerIT#aEscritaRecusaGravarPorCimaDeOutraOperacao` — a versão otimista passou a ser conferida
      no `WHERE`, e a segunda escrita recebe `409 container_modified` em vez de gravar por cima
      (`DEB-CON-003 #1`). O teste vive no repositório, e não na API, porque pela porta HTTP o caso de uso
      relê o vasilhame antes de gravar: envelhecer a linha por fora não simularia concorrência nenhuma.
      Complementos: `LoadIT#oMesmoKegNaoVaiEmDuasCargas` e
      `LoadIT#duasParadasNaMesmaPosicaoNaoConvivem`, ambos garantidos por índice único parcial.

- [x] **Entrega/coleta offline é idempotente e resolve conflitos.**
      `SyncIT`: o reenvio devolve o mesmo resultado e não cria outro; a idempotência é por (aparelho,
      operação), então dois celulares podem sortear o mesmo `UUID` sem colidir; um item recusado não
      derruba os outros; a hora do fato é do aparelho, e o relógio adiantado é **marcado, não recusado**.
      E o conflito de verdade `oConflitoNaoSeResolveSozinhoEEsperaGente` — resolver sozinho seria escolher
      em silêncio qual das duas versões do mundo vale.

- [x] **Avaria, perda, retorno, limpeza e baixa são auditados.**
      Auditoria em todos os comandos críticos do `ContainerController`/`LoanController`
      (`container.retire`, `container.condition`, `container.loan.*`, `container.sanitize`), com alçada
      própria testada: `ContainerIT#aBaixaEAInspecaoTemAlcadaPropria`,
      `ContainerLoanIT#declararPerdaTemAlcadaPropria` e `aVoltaTemAlcadaCritica`. Nada é apagado
      fisicamente — a perda que reaparece vira fato **novo** (`oPerdidoQueVoltaNaoApagaAPerda`).

- [x] **Dados de localização e comprovantes seguem política de privacidade.**
      Garantido no **tipo e na coluna**, não na disciplina: `DeliveryIT#aCoordenadaEGravadaArredondada`
      confere no banco que `distribution_proof.latitude` é `NUMERIC(6,3)` e não guarda mais casas nem que
      alguém mande. A mídia de entrega não é construtível sem consentimento e finalidade
      (`aAssinaturaSoEntraComQuemConsentiuEParaQue`, `aAssinaturaSemFinalidadeNaoEntra`), e a entrega
      acontece **sem** assinatura (`aEntregaAconteceSemAssinatura`): exigir o dado pessoal para operar o
      transformaria em obrigatório na prática.

- [ ] **Simulado localiza todos os contêineres de um lote afetado.**
      **Aberto, e a lacuna é nomeada.** `RecallIT#abrirRecallListaOsDestinos` prova que o recall alcança
      `FINISHED_LOT` e `SHIPMENT`, e `ContainerFillIT#oEnchimentoEntraNaGenealogia` prova que o
      enchimento entra na genealogia. **O que não existe é o teste que liga as duas pontas**: um recall
      aberto sobre um lote e a lista dos vasilhames que o carregaram saindo no escopo. É exatamente a
      pergunta que o keg responde e a caixa responde diferente — o mesmo vasilhame carregou outro lote no
      mês seguinte, e o recall precisa alcançar o do período certo.
      **Como fechar:** um IT que enche dois kegs com o lote afetado, abre o recall e afirma que os dois
      aparecem no escopo com `node.type = CONTAINER` — e que um terceiro keg, enchido com outro lote, não
      aparece. O contraponto é o que distingue "achou" de "listou tudo".

## Débitos desta sprint

`DEB-LOG-002` e `DEB-CON-003` (dois furos altos e os oito achados) — todos fechados. Ver `STATUS.md`.
