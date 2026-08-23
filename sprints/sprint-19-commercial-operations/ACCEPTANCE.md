# Aceite — Sprint 19

Cada item aponta a evidência que o sustenta. **Marcado** significa que existe artefato verificável no
repositório; **aberto** significa que a prova não existe ainda, e a lacuna está nomeada. Um item marcado
sem evidência citada não é aceite — é opinião.

- [x] **Cliente, consentimento e retenção são auditáveis.**
      `CrmIT`: consentimento por finalidade, finalidade contratual que não depende de consentimento,
      revogação que não apaga o histórico ("o livro só cresce"), anonimização que apaga a pessoa e mantém
      a linha, fila de retenção dizendo de onde veio a data. Domínio em `ConsentLedgerTest` (silêncio não
      é permissão; decisão posterior não contamina a consulta do passado) e `RetentionPolicyTest`.

- [x] **Preço, moeda, canal e vigência são explícitos.**
      `SalesIT`: preço novo fecha o anterior na véspera, sobreposição recusada com a data na resposta,
      a linha do tempo não troca de moeda, períodos adjacentes não conflitam, preço zero recusado. E a
      garantia é do banco, não da checagem: `oBancoRecusaSobreposicaoMesmoContornandoODominio`.

- [x] **Concorrência não vende estoque duas vezes.**
      `SalesOrderIT#duasVendasSimultaneasNaoVendemOMESMOEstoqueDuasVezes` — duas transações de verdade,
      alinhadas por barreira, contra um lote que comporta só uma: uma vende, a outra recebe
      `insufficient_lot_stock`, e o reservado gravado é exatamente o de um pedido.
      **Este item passou a ser marcado em 22/08.** O teste que o cobria (`oPedidoReserva...`) declara em
      comentário que a prova é *sequencial* — o segundo pedido encontra o estoque já preso. Sequencial não
      é concorrente: passaria mesmo se a reserva fosse ler-depois-escrever, que é o padrão que quebra
      quando duas telas vendem o mesmo lote no mesmo segundo. O `UPDATE` condicional já estava certo; o
      que faltava era a prova.

- [x] **Pedido mantém rastreio até lote e validade.**
      `SalesOrderIT#oPedidoCongelaOPrecoEGuardaOLoteReservado` e
      `naoSePrometeEntregaDepoisDaValidadeDoLote`. A validade viaja congelada na reserva
      (`sales_lot_reservation.best_before`): é o número que sustentou a promessa, e a checagem precisa
      dele, não do de hoje.

- [x] **Previsão mostra dados, versão, erro e confiança.**
      `DemandForecastIT#comSeisMesesDeHistoricoAPrevisaoTrazOsQuatroDados` — `sampleMonths`, `method`
      (`moving-average v1`), `meanAbsolutePercentageError` e `confidence`, os quatro juntos. E o
      contraponto que impede o número solto: `semHistoricoAPrevisaoDizQueNaoTemPrevisao`.

- [x] **Integração externa falha sem corromper pedido.**
      `oPedidoSobreviveAFalhaDaEntrega`. A entrega é enfileirada no mesmo commit do pedido (outbox), então
      a falha do destino não desfaz a venda nem manda webhook de pedido que não existe.

- [x] **Testes financeiros, tenant, privacidade e E2E estão verdes.**
      `mvnw clean verify`: 1525 testes, incluindo `TenantIsolationTest` e `ModularityTest`. E2E:
      `e2e/tests/sales-journey.spec.ts` — a jornada que o `TEST_PLAN` pedia e que **não existia** quando
      a sprint foi declarada encerrada (`DEB-SAL-004`). Foi ela que encontrou as oito stores lendo o erro
      no nível errado.

## Débitos desta sprint

Todos fechados: `DEB-SAL-002`, `DEB-SAL-003`, `DEB-SAL-004`, `DEB-SAL-005` e `DEB-SAL-006`. Ver `STATUS.md`.
