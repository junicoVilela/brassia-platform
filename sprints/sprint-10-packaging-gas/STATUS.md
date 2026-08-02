# Status — Sprint 10

Estado: CONCLUÍDA (aceite pendente do mantenedor)

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PKG-001 | Concluída | IA | #118 — V67 + `PackagingPlanIT` (16 testes) | Novo módulo `packaging` |
| GAS-001 | Concluída | IA | #119 — V68 + `GasNetworkIT` (16 testes) | Novo módulo `gas` |
| PKG-002 | Concluída | IA | #120 — V69 + `CarbonationIT` (13 testes) | Fórmulas no hub `calculator` |
| PKG-003 | Concluída | IA | #121 — V70 + `PackagingRunIT` (12 testes) | Perda derivada; consumo vira movimento |
| FSL-001 | Concluída | IA | #122 — V71 + `FreshnessIT` (13 testes) | Política de vida útil é da cervejaria |
| GAS-002 | Concluída | IA | #123 — V72 + `ServiceLineIT` (12 testes) | Recomendação; nada é ajustado |
| PKG-004 | Concluída | IA | #124 — V73 + `LabelIT` (14 testes) | Template versionado ≠ regra regulatória |

## Decisões e bloqueios

### PKG-001

- **Limpeza da linha vem do ciclo de sanitização, não do checklist.** O envase consulta
  `sanitation.CleaningReleaseLookup` (última liberação do equipamento) em vez de aceitar um "ok"
  digitado, para a evidência de limpeza ser rastreável ao ciclo. Isso evita criar estado de
  limpo/bloqueado em `equipment` — o débito CLN-004-A da sprint 08 continua aberto e sem dono.
- **PKG-001-A — validade da limpeza por tempo não foi decidida.** A regra implementada
  (`LineCleanliness`) exige liberação anterior ao início planejado e posterior ao último envase na
  linha. Falta o prazo de validade do CIP (quantas horas uma liberação cobre sem novo uso): o número
  depende do POP e da cervejaria e inventá-lo criaria regra de negócio sem fonte. Critério de
  remoção: definir o prazo com o cervejeiro e passá-lo a um parâmetro da cervejaria.
- **Disponibilidade da linha = cadastro ativo + agenda de manutenção + agenda de envase.**
  Publicada como `equipment.EquipmentAvailabilityLookup`; conflito entre planos é resolvido dentro
  do próprio `packaging`.
- **Só lote em `FERMENTING` aceita plano de envase.** Lote em brassagem (`IN_PROGRESS`) não é
  envasável; `COMPLETED` passa a existir com PKG-003 e será reavaliado lá.
- **O teto do plano é a cerveja que está no tanque, não o volume da ordem.** A transferência tem
  perdas: uma ordem de 400 L que transferiu 390 L só pode envasar 390 L. Planejar contra o volume
  planejado inventaria cerveja que não existe. `production.BatchLookup` expõe
  `packageableVolumeLiters` (o volume transferido quando já houve transferência, senão o planejado)
  e é ele que limita o plano. Coberto por `capsThePlanByWhatWasTransferredNotByWhatWasOrdered`, que
  separa os dois números — os demais casos passavam com qualquer um dos dois tetos.
- Consultas publicadas ampliadas em PKG-001: `production.BatchLookup` (passou a expor código,
  volume planejado, volume envasável e estado do lote), `catalog.IngredientSpecLookup` (ganhou
  `volumeMl` e `useUnit`).

### GAS-001

- **Gás é rastreado por massa, não por pressão.** Em cilindro de CO₂ com fase líquida o manômetro
  fica praticamente constante enquanto houver líquido, então estimar o restante pela pressão daria
  um número errado com cara de certo. A pressão é medida e guardada como evidência da linha, nunca
  como estimativa de conteúdo.
- **Sobrepressão bloqueia a linha automaticamente.** Leitura acima do teto da rede é preservada
  (medição é evidência) e leva a conexão a `BLOCKED`; só um novo teste de vazamento aprovado
  devolve a linha ao serviço. É regra determinística de segurança, não ação de IA.
- **O teto de pressão da rede é congelado na conexão** (menor limite entre regulador e manifold):
  alterar depois o cadastro do componente não reescreve o que a linha montada suportava.
- **Regulador e manifold vivem no mesmo cadastro** (`gas_network_component`, discriminado por
  `kind`): compartilham identidade, código e limite de pressão; o que muda é o papel na conexão.
- **GAS-001-A — consumo de gás não entra em estoque nem em custo.** O consumo é registrado no
  módulo `gas` (massa por linha), sem movimento de estoque nem rateio por lote. Cilindro é ativo
  em comodato na maioria das cervejarias, e o modelo de custo do gás é assunto da sprint 13.
  Critério de remoção: definir com o cervejeiro se o gás é insumo de estoque ou despesa de
  utilidade, e ligar o consumo ao módulo escolhido.
- **GAS-001-B — periodicidade da requalificação não é calculada pelo sistema.** A data de
  vencimento é informada por cilindro; o sistema não deriva o próximo vencimento a partir de uma
  regra fixa de anos, porque o prazo depende de norma e do tipo de cilindro. Critério de remoção:
  confirmar a norma aplicável e transformá-la em parâmetro da cervejaria.
- O ponto de uso é um equipamento (`equipment.EquipmentProfileLookup` valida existência); um ponto
  recebe um cilindro por vez e um cilindro serve um ponto por vez, garantido por índice parcial
  único além da checagem no comando.

### PKG-002

- **As fórmulas foram para o hub `calculator`, não para `packaging`.** `docs/05_CALCULATION_ENGINE.md`
  manda não espalhar fórmula, e o hub já é determinístico e versionado. Três calculadoras novas:
  `co2-residual`, `priming-sugar` e `forced-carbonation-pressure`. O módulo `packaging` compõe a
  decisão e guarda a confirmação; quem calcula é o hub.
- **Confirmação humana é obrigatória**, no mesmo padrão de YST-002: `confirmed: false` é recusado
  com 400. A prévia (`GET .../carbonation/preview`) calcula e explica sem gravar nada — entradas,
  CO₂ residual, o que falta dissolver, fórmula, versão, hipóteses e alertas.
- **Priming sobre CO₂ que já atinge o alvo é bloqueado** (409 `over_carbonation`, com alvo e
  residual): adicionar açúcar ali não carbonata mais, só gera pressão que a embalagem não comporta.
  Na carbonatação forçada o mesmo caso é apenas "não aplique pressão" — nada é adicionado à cerveja,
  então não há risco equivalente.
- **Rendimento dos açúcares:** sacarose (0,514) e dextrose mono-hidratada (0,444) vêm da
  estequiometria da fermentação e são exatos. O extrato seco de malte (0,400) é estimado — depende
  da fermentabilidade do extrato, que varia por fabricante e lote — e sai com aviso de confiança
  junto do resultado, em vez de um número com precisão que ele não tem.
- Recalcular substitui a decisão inteira (1:1 com o plano): trocar de método não deixa resíduo do
  anterior, então entrada e resultado nunca divergem.
- **PKG-002-A — não existe limite de pressão por embalagem.** O sistema bloqueia o caso claro
  (priming sem espaço para o alvo), mas não sabe quanta pressão cada embalagem suporta: lata, long
  neck e garrafa de champanhe têm limites diferentes, e esse dado não está no catálogo. Critério de
  remoção: cadastrar pressão máxima por embalagem no catálogo e validar o alvo contra ela.

### PKG-003

- **A perda é derivada, não digitada.** O operador declara o que mediu — volume que saiu do tanque,
  unidades boas e rejeitadas — e a perda é o resto. Aceitar perda digitada ao lado dos outros três
  números permitiria um balanço que não fecha, que é justamente o que esta história impede. O
  balanço fecha por construção, e o banco também o guarda (`ck_packaging_run_balance`).
- **Rejeito consome embalagem igual:** uma lata cheia e descartada é uma lata gasta. O consumo é
  boas + rejeitadas, e o rejeito também pesa no balanço de volume.
- **Consumo de embalagem vira movimento de verdade:** a reserva do plano é convertida em RELEASE +
  CONSUMPTION no ledger, o excedente sai do saldo livre em FEFO e a sobra da reserva é devolvida —
  plano executado não fica segurando estoque.
- **O teto da execução é o mesmo do planejamento:** `packageableVolumeLiters`, o volume de fato
  transferido ao fermentador (regra estabelecida em PKG-001).
- Um lote pode ser dividido em vários envases (latas e barris, por exemplo), mas a soma das
  execuções não passa do que existiu no tanque — 409 `batch_volume_exceeded` com os números.
- **Executar é terminal e não é cancelável:** os dois estados terminais não se equivalem —
  cancelado devolve a embalagem, executado a consumiu. Desfazer produção não é cancelar plano.

### FSL-001

- **A tabela que traduz ppb em dias é da cervejaria, não do sistema.** TPO é o que mais empurra o
  envelhecimento, mas converter oxigênio em validade depende do estilo, da temperatura de estocagem
  e do padrão de frescor da casa — embutir uma tabela aqui seria dar precisão a um palpite. A
  política (`packaging_shelf_life_policy`) tem faixas de TPO e os dias que cada uma sustenta, no
  mesmo padrão de `YeastPolicy` (YST-002), mas **sem valor padrão**: sem política não há
  recomendação, a medição continua sendo gravada e a validade vira decisão humana registrada.
- **A recomendação sai explicada, fator a fator:** qual faixa pegou, quanto do oxigênio veio do
  espaço livre, se a purga foi conferida e se a vedação passou. É o que a torna auditável.
- **Purga não conferida e vedação reprovada não mudam o número — mudam a confiança nele.** Entram
  como ressalvas (`caveats`), porque evidência incompleta não justifica inventar outra validade.
- **O override nunca apaga o recomendado:** os dois ficam lado a lado, com motivo obrigatório, quem
  e quando, mais a marca `extendsBeyondRecommendation` quando a data vai além do que a evidência
  sustentava. É isso que permite, meses depois, saber de onde veio a data impressa.
- **Invariante de leitura: TPO ≥ DO.** O oxigênio total inclui o dissolvido; um TPO abaixo do DO é
  erro de leitura ou de unidade, não uma embalagem melhor que a cerveja. Guardado no domínio e no
  banco.
- Remedir substitui o registro e **derruba um override anterior** — a evidência mudou, então a
  decisão tomada sobre a evidência antiga não vale mais.
- Configurar a política é alçada própria (`packaging.policy.manage`): gerir plano não basta.
- A política curva só desce: uma faixa mais suja não pode prometer mais dias que uma mais limpa, e
  o pior caso não pode render mais que a última faixa.

### GAS-002

- **A pressão de serviço não é escolha livre.** Ela sai do equilíbrio de carbonatação na temperatura
  de serviço — a mesma calculadora da PKG-002, reutilizada, não copiada. Servir a outra pressão faz
  o barril ganhar ou perder CO₂ ao longo do tempo, e a cerveja sai do padrão sem ninguém ter mexido
  nela. O que sobra dessa pressão, depois do desnível e da pressão residual da torneira, é o que a
  linha dissipa por atrito.
- **A vazão entra escalando a resistência**, não como enfeite: em escoamento laminar a perda de
  carga é proporcional à vazão, então pedir o dobro da vazão de referência do fabricante dobra a
  resistência efetiva do mesmo tubo. Por isso a vazão de referência é guardada ao lado da
  resistência no catálogo de tubos — sem ela não dá para escalar corretamente.
- **A resistência do tubo vem da ficha do fabricante**, não do sistema: material e diâmetro interno
  são a identidade do tubo, e recadastrar a mesma combinação só atualiza os números.
- **Nenhuma válvula ou regulador é ajustado automaticamente.** Todo retorno do balanceamento carrega
  o aviso `manual_adjustment_only`, marcado como aviso de segurança. Montagem impossível vem com
  `feasible: false`; pressão acima do teto da rede de gás do ponto (reaproveitando o
  `networkMaxPressureBar` de GAS-001) vem com `above_network_limit`.
- **Aplicar gera revisão e preserva a anterior:** a montagem física de ontem é a única evidência de
  por que a cerveja de ontem saiu como saiu. O comprimento montado pode divergir do recomendado, e
  o desvio é **registrado, não corrigido**.
- A pressão hidrostática da coluna de cerveja (ρ·g com 1010 kg/m³) é física, não escolha, e ficou
  numa calculadora própria (`beer-column-pressure`). O desnível pode ser negativo: a torneira pode
  ficar abaixo do barril, e aí a coluna devolve pressão em vez de consumir.
- Dataset dourado do balanceamento confere com a regra clássica de campo (3,5 pés para 12 psi em
  tubo 3/16" com 1 pé de subida), o que valida o modelo métrico contra a prática conhecida.

### PKG-004

- **Template e regra regulatória são coisas separadas, e essa é a decisão central da história.**
  O template é layout (quais campos, em que ordem) e muda quando o designer quer outra arte. A
  obrigatoriedade é lei. Se as duas vivessem juntas, uma troca de layout derrubaria silenciosamente
  um campo exigido e sairia um lote inteiro de rótulos irregulares. Separadas, a prévia acusa
  `requiredNotDrawn` — exigido pela regra e não desenhado pelo layout — como caso distinto de
  `missingRequired`, porque a correção é diferente em cada um.
- **Nada no rótulo é digitado:** cada campo é resolvido de uma fonte rastreável e a prévia mostra
  qual — nome e código do lote vêm do lote de produção, volume do plano de envase, ABV da receita
  publicada (com a versão), validade do controle de frescor (dizendo se foi recomendada pela
  evidência ou sobreposta, e por quê), e o QR aponta para lote e plano.
- **Reimpressão não é escolha de quem chama:** já existindo impressão para o plano, a próxima é
  reimpressão e o motivo passa a ser obrigatório. Assim ninguém escapa da justificativa marcando a
  segunda tiragem como se fosse a primeira. Cada tiragem congela a versão do template usada.
- **Quais campos a lei exige é da cervejaria**, não do sistema: depende do país e da categoria da
  bebida. Configurar a regra é alçada própria (`packaging.policy.manage`).
- Salvar o template acrescenta versão e preserva a anterior; a ordem dos campos é o layout e é
  preservada na ida e volta do banco (há teste para isso).
- **PKG-004-A — alergênicos não têm fonte no sistema.** O catálogo de ingredientes não guarda
  declaração de alergênico, e derivá-la do tipo do ingrediente seria inventar classificação
  regulatória. O campo existe no rótulo e sai como ausente: se a regra da casa o exigir, a prévia
  barra a impressão — que é o comportamento correto até o dado existir. Critério de remoção:
  cadastrar alergênicos declarados no catálogo e ligar a fonte.
- **PKG-004-B — o ABV é calculado, não medido.** Vem das métricas da receita publicada (fonte
  rastreável, com versão), não de OG/FG reais do lote. A origem diz isso em voz alta no rótulo
  ("calculado, não medido"). Critério de remoção: expor OG medido (transferência) e FG estável
  (FER-003) como fonte e recalcular o ABV do lote.

## Evidências de encerramento

- **Build/commit:** `main` em `c7fece4`; PRs #118 (PKG-001), #119 (GAS-001), #120 (PKG-002),
  #121 (PKG-003), #122 (FSL-001), #123 (GAS-002) e #124 (PKG-004) mergeados por squash, nesta
  ordem — a pilha foi montada empilhada e cada branch foi rebaseada sobre a `main` antes do seu
  merge.
- **Testes executados:** `./mvnw verify` na `main` depois dos sete merges — 513 unitários + 376 de
  integração (Testcontainers/PostgreSQL 18), verdes, incluindo `ModularityTest`; frontend
  `ng build` + `ng test` — 229 testes, verdes, ESLint sem warnings.
- **Migration aplicada:** V67→V73, sequência contínua, aplicada em banco limpo a cada IT; todas
  com constraints e índices.
- **Contratos atualizados:** `contracts/openapi.yaml` de 94 para 129 paths (+35) e sete códigos de
  Problem Details novos. Cinco cálculos publicados no hub `calculator`: três em PKG-002
  (`co2-residual`, `priming-sugar`, `forced-carbonation-pressure`) e dois em GAS-002
  (`line-balance`, `beer-column-pressure`).
- **Riscos remanescentes:** (1) `PKG-004-A` e `PKG-004-B` afetam rótulo impresso, não só código —
  alergênico exigido barra a impressão, e o ABV sai marcado como calculado; (2) sem limite de
  pressão por embalagem, um alvo alto em embalagem frágil passa (`PKG-002-A`); (3) a liberação de
  limpeza não expira por tempo (`PKG-001-A`); (4) sem harness de e2e no projeto, único item não
  atendido do DoD; (5) três achados cosméticos do tema, encontrados na verificação visual e não
  introduzidos por esta sprint — contraste 2,1:1 do `btn-outline-secondary` no escuro (15
  templates), `datetime-local` truncado em `col-sm-2` no desktop e `<code>` em vermelho ao lado de
  resultado de cálculo.
- **Aceite:** **pendente do mantenedor** — ver `ACCEPTANCE.md`.

## Débitos abertos ao fim da sprint

Seis débitos com identificador seguem abertos, todos com critério de remoção registrado e nenhum
bloqueando o uso das histórias entregues: **PKG-001-A** (validade do CIP por tempo), **PKG-002-A**
(pressão máxima por embalagem), **GAS-001-A** (custo e estoque do gás, previsto para a sprint 13),
**GAS-001-B** (periodicidade da requalificação), **PKG-004-A** (alergênicos sem fonte no catálogo)
e **PKG-004-B** (ABV calculado, não medido). Nenhum débito de sprint anterior foi removido nesta;
o CLN-004-A da sprint 08, citado em PKG-001, continua aberto e sem dono.
