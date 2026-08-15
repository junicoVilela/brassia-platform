# ADR 0016 — Escrita entre módulos por porta publicada, e de que lado ela é declarada

## Status

Aceita

## Contexto

Até a sprint 16 a comunicação entre módulos era quase toda de **leitura**: consultas publicadas
(`BatchLookup`, `RecipeLookup`, `CleaningReleaseLookup`) e eventos de domínio. A única escrita publicada
era `production.BatchAlertPublisher`, e o aceite da sprint 09 já registrou que ela "é a primeira porta de
*escrita* publicada entre módulos" e que "o precedente pode merecer registro".

O precedente virou regra sozinho. Fechando os débitos das sprints 08 a 16, **cinco portas de escrita**
apareceram em duas semanas:

| Porta | Declarada em | Quem chama |
|---|---|---|
| `production.BlendResultCommands` | produção | blend |
| `equipment.EquipmentCleanlinessCommands` | equipamento | sanitização |
| `equipment.EquipmentUsageCommands` | equipamento | produção, blend |
| `quality.NonConformityOpening` | qualidade | IA |
| `traceability.CorrectiveActionSink` | rastreabilidade | qualidade |

A última é a que obriga este ADR: ela está na direção **oposta** às outras quatro, e a diferença não é
estilo.

## Decisão

**Escrita entre módulos passa por porta publicada no pacote raiz do módulo, nunca por acesso a
repositório ou tabela alheia.** O módulo dono do dado continua dono das suas invariantes mesmo quando quem
pede é outro.

**A direção padrão é: quem tem o dado declara a porta e a implementa; quem precisa escrever depende
dela.** É a forma de quatro das cinco acima.

**A exceção é o ciclo.** Quando a direção padrão fecharia um ciclo entre módulos, ela se inverte: quem
precisa do efeito declara a porta no próprio pacote raiz, e o módulo dono do dado a implementa num
`adapter/inbound/gateway`. É a forma que `traceability.LineageSource` e `costing.CostContributor` já
usavam para leitura, aplicada agora à escrita.

**Quem decide qual das duas formas vale não é o julgamento de quem escreve o código — é o
`ModularityTest`.** Ele roda em cada build e recusa ciclo entre fatias.

## Motivo

- **A fronteira precisa sobreviver ao chamador.** Um módulo que aceita escrita direta na sua tabela perde
  a garantia no dia em que alguém escrever ali sem passar pelas suas regras — e não há teste que pegue
  isso, porque não há regra a violar.
- **A direção não é escolha estética.** Duas vezes nesta leva a direção "natural" fechou um ciclo:
  - `CLN-004-A`: o débito previa um *listener* em `equipment` consumindo `CleaningCycleReleased`.
    Implementado assim, ele criou `equipment → sanitation` — e `sanitation → equipment` já existia desde a
    CLN-003, porque o ciclo de limpeza valida o equipamento ao iniciar. Ciclo de dois. Invertido para
    porta chamada pela liberação, a dependência ficou numa direção só.
  - `FDS-004-A`: o critério pedia "o CAPA publicar porta de abertura de ação". Publicada em `quality` e
    chamada por `traceability`, fechou `production → traceability → quality → production`, porque
    `quality` depende de `production` desde a QLT-001. Declarada em `traceability` e implementada por
    `quality`, o ciclo sumiu.
- **Nos dois casos o débito prescrevia a solução errada**, e o teste foi quem corrigiu. Uma regra
  escrita aqui não teria evitado o erro — mas encurta o próximo diagnóstico.
- **Inverter tem um ganho além do arquitetural**: quem declara a porta descreve o efeito que quer em
  vocabulário próprio (`CorrectiveActionSink.plan`, e não `planCapaAction`), e deixa de saber que CAPA
  existe.

## Consequências

- **Síncrono e dentro da transação de quem chama, por padrão.** Foi decisivo em dois casos: a criação do
  lote de blend precisa do identificador de volta e precisa que a falha desfaça a execução inteira; a
  limpeza do equipamento dentro da transação da liberação elimina a janela em que um ciclo aparece
  liberado com o tanque ainda sujo. Efeito que pode falhar depois do commit continua sendo evento.
- **A porta expõe o efeito, não o modelo.** `NonConformityOpening` não recebe prazo, código nem status:
  prazo sai da política da casa, código é numerado pelo sistema e NC nasce aberta. O que **não** está na
  assinatura é a parte que impede um chamador de decidir o que a cervejaria decidiu uma vez.
- **A porta delega ao mesmo caso de uso da tela.** Um caminho paralelo seria um segundo lugar onde as
  mesmas validações precisariam ser mantidas iguais, e elas divergiriam na primeira mudança.
- **Checagem prévia de existência entre módulos vira dependência**, e às vezes é ela que fecha o ciclo.
  Foi o que aconteceu com o lote da não conformidade: a validação saiu do handler e virou chave
  estrangeira. A troca melhora o que estava lá — checagem prévia não é garantia, porque duas requisições
  simultâneas passam as duas por ela.
- **Cresce o número de tipos no pacote raiz de cada módulo.** É o preço de a fronteira ser explícita: o
  pacote raiz passa a ser a lista legível do que o módulo aceita que façam com ele.

## Quando NÃO usar porta

- **Efeito que pode acontecer depois, e cuja falha não deve derrubar o ato**: continua evento de domínio.
  `CleaningCycleReleased` segue publicado mesmo depois de a limpeza do equipamento virar porta — o estado
  não pode depender de alguém ter registrado um listener, mas quem quiser reagir continua podendo.
- **Leitura**: consulta publicada, que é o padrão anterior e não muda.
- **Dado que o outro módulo não deveria ter**: a porta não é atalho para contornar a fronteira. Se a
  escrita exige conhecer o modelo alheio, o recorte está errado antes da porta.
