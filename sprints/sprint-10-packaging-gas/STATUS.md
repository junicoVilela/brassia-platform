# Status — Sprint 10

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| PKG-001 | Concluída | — | V67 + `PackagingPlanIT` (16 testes) | Novo módulo `packaging` |
| GAS-001 | Concluída | — | V68 + `GasNetworkIT` (16 testes) | Novo módulo `gas` |
| PKG-002 | Concluída | — | V69 + `CarbonationIT` (13 testes) | Fórmulas no hub `calculator` |
| PKG-003 | Concluída | — | V70 + `PackagingRunIT` (12 testes) | Perda derivada; consumo vira movimento |
| FSL-001 | Concluída | — | V71 + `FreshnessIT` (13 testes) | Política de vida útil é da cervejaria |
| GAS-002 | Concluída | — | V72 + `ServiceLineIT` (12 testes) | Recomendação; nada é ajustado |
| PKG-004 | A fazer | — | — | — |

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
