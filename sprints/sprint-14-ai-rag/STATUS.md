# Status — Sprint 14

Estado: CONCLUÍDA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| AIA-001 | Concluída | Claude | `backend/.../ai`, `V93__ai_model_gateway.sql`, `frontend/.../features/ai` | Gateway com provedor abstrato, timeout, orçamento, contrato validado, fallback e ledger. Provedor desligado é o default. |
| RAG-001 | Concluída | Claude | `backend/.../knowledge`, `V94__knowledge_document.sql`, `frontend/.../features/knowledge`, ADR 0015 | Módulo `knowledge` novo. Indexação com versão/vigência, busca textual em português sem acento, filtro de permissão dentro da consulta. |
| RAG-002 | Concluída | Claude | `ai/domain/Grounding`, `ai/.../GroundedAnswerHandler`, `V95__ai_grounded_answer.sql`, `frontend/.../copilot-page` | Resposta com citação **conferida** contra as fontes, inferência em campo separado, limitação declarada sem fonte. |
| AIA-002 | Concluída | Claude | `ai/domain/Fact`, `ai/domain/FactGrounding`, `ai/.../BatchFactsAssembler`, `V96__ai_batch_assessment.sql`, `frontend/.../assessment-page` | O domínio calcula, o modelo interpreta. Todo número conferido contra os fatos que a afirmação cita. |
| AIA-003 | Concluída | Claude | `ai/domain/ProposedAction`, `ai/domain/CommandProposal`, `ai/.../CommandProposalHandler`, `V97__ai_command_proposal.sql`, `frontend/.../proposals-page` | Allowlist fechada, proposta persistida com prazo, **nova autorização no aceite exigindo a permissão do comando**, decisão auditada. Não executa — ver DEB-AIA-002. |

## Decisões e bloqueios

### DEC-AI-001 — Fallback só para falha de provedor, nunca para resposta fora do contrato

Falha do provedor é do outro lado da fronteira e outro modelo pode responder; resposta que não
satisfaz o contrato é sinal de que o nosso prompt ou o nosso schema está errado, e repetir num segundo
modelo gasta dinheiro para colher a mesma classe de erro. A recusa é registrada com o custo real, que é
o que aponta o prompt defeituoso. Fixado em `ModelGatewayServiceTest`.

### DEC-AI-002 — O gasto do mês é somado do ledger, não guardado em contador

A tabela de orçamento guarda só o teto. Um total incrementado ao lado dele seria um segundo número
sobre o mesmo fato: um incremento perdido numa falha faria o orçamento proteger contra um consumo que
ele não vê. Custa uma consulta indexada por leitura de status.

### DEC-AI-003 — Contagem de tokens fica no ledger, fora da auditoria

O mascarador de auditoria (`SensitiveDataMasker`) trata qualquer chave contendo "token" como segredo, e
está certo — quase sempre é. Como o ledger já guarda a contagem exata e a auditoria só precisa do custo,
as chaves `inputTokens`/`outputTokens` saíram do metadata em vez de disputar o nome.

### DEC-RAG-001 — POP de limpeza não é indexado (ADR 0015 e `DocumentType`)

O procedimento de sanitização já é estrutura versionada e imutável no módulo `sanitation`, e o sistema
responde sobre ele deterministicamente. Passá-lo por recuperação textual transformaria um fato em um
palpite — o contrário de "IA interpreta e explica; domínio calcula e decide validade". A base de
conhecimento serve ao que não tem estrutura: manual, ficha, laudo e nota.

### DEC-RAG-002 — Busca textual do PostgreSQL, não vetorial (ADR 0015)

`docs/01_ARCHITECTURE.md` põe busca vetorial como opcional e sujeita a ADR. As perguntas deste domínio são
sobre termos técnicos concretos que aparecem literalmente nos documentos, e o corpus é pequeno e por
cervejaria. O ADR registra o critério medido para adotar embeddings depois.

Duas escolhas dentro dela, ambas descobertas por teste que falhou:

- **`unaccent` + configuração `portuguese_unaccent`.** Em português se digita "peracetico"; o dicionário
  puro não acha "peracético" a partir disso e a busca devolve nada — quem perguntou conclui que o
  documento não existe.
- **Termos em OU, não em E.** `plainto_tsquery` exigiria todos os termos, e o efeito é perverso: quanto
  mais detalhada a pergunta, menos ela recupera. É seguro montar a consulta com `to_tsquery` porque
  `Chunker.termsOf` só devolve letras e dígitos — não há operador para injetar.

### DEC-RAG-003 — Citação é conferida contra a evidência, não aceita por confiança

Validar o formato da resposta garante que ela tem os campos certos, não que o que está neles é verdade. Um
modelo pode devolver JSON impecável citando um manual inexistente, ou atribuindo a um manual real uma frase
que ele nunca disse — as duas coisas são invisíveis para um validador de schema. `Grounding` compara cada
citação contra os mesmos trechos que foram ao prompt: documento ausente, trecho inexistente e frase ausente
são descartados com o motivo. A normalização dobra forma (espaço, caixa, acento) e **não** aproxima sentido:
paráfrase não passa, porque trocar palavra é inventar.

Regra derivada: **quem afirma tem de sustentar.** `answered: true` sem nenhuma citação conferida faz a
resposta ser descartada — o texto do modelo não é apresentado com ressalva, porque afirmação sem fonte não
deve circular. `answered: false` sem citação é legítimo: declinar corretamente não é falha.

### DEC-RAG-004 — Sem fonte, o modelo não é chamado

Recuperação vazia devolve a limitação declarada direto. É garantia estrutural, não confiança no
comportamento do modelo — quem não é perguntado não pode inventar — e não custa nada.

### DEC-RAG-005 — Resposta não é persistida

Guardar a resposta criaria uma segunda verdade sobre as fontes: uma ficha substituída em junho deixaria para
trás uma resposta de maio afirmando a concentração antiga com a mesma aparência de atual. O que sustenta uma
resposta é o documento citado, que já está indexado e versionado. Custo e latência estão no ledger de
invocações; quem perguntou e quantas citações conferiram estão na auditoria — nenhum dos dois guarda
pergunta ou resposta.

### DEC-RAG-006 — Defesa contra prompt injection é estrutural, não uma frase no prompt

A instrução pede ao modelo que trate conteúdo como dado, e isso ajuda — mas é pedido, não garantia. O que
impede o dano é o que **não existe** nesta chamada: `ModelGateway.Prompt` não tem conceito de ferramenta,
então não há ferramenta a conceder; o schema da resposta não tem campo de comando, então um comando devolvido
é campo desconhecido e a resposta é recusada inteira; e a citação é conferida, então um documento que mande
"cite este outro documento" não consegue produzir citação verificável. Texto injetado no máximo suja um
campo de texto. Coberto por três testes de unidade e um IT com o documento injetado indexado de verdade.

### DEC-AIA-001 — O modelo não produz número; o domínio calcula e ele interpreta

Cada número da avaliação vem da consulta publicada do módulo que responde por ele: volume e estado da
produção, OG/FG/ABV/IBU do motor de cálculo da receita, medições e desvios da qualidade, custo do custeio.
Nada é recalculado no módulo de IA — recalcular criaria uma segunda opinião sobre o mesmo fato. Perda
percentual e proporção dentro da faixa são derivados calculados no domínio **exatamente** para que o modelo
não precise dividir nada. `Fact.source` viaja até a tela, o que faz o critério "cálculos referenciam serviço
de domínio" ser verificável por quem lê.

### DEC-AIA-002 — O número é conferido contra os fatos que a afirmação cita, não contra todos

Esta versão é a segunda. A primeira conferia o número contra qualquer valor entre os fatos, e um teste pegou
**"o lote perdeu 45 L" passando porque 45 era o IBU previsto da receita** — número existente, assunto errado,
afirmação errada por 35 litros. Amarrar cada número aos fatos que a própria afirmação declara usar fecha essa
porta.

Consequências, todas deliberadas:
- **Afirmação sem fato citado não pode conter número** — sem referência não há contra o que conferir.
- O **resumo** virou uma afirmação com `factRefs` no schema, porque é a frase que a pessoa lê primeiro e
  deixá-la fora da amarração seria deixar de fora justamente a mais lida.
- **Suposições não podem ter número** — uma suposição com número é o número inventado no seu disfarce mais
  convincente.
- Aritmética derivada é recusada mesmo quando está correta: `400 − 390 = 10` é conta de quem não presta contas
  dela.

### DEC-AIA-003 — Propor e confirmar são alçadas diferentes, e a diferença é a história

Pedir uma proposta exige `ai.command.propose`. **Confirmá-la exige a permissão do comando proposto** —
`costing.cost.close`, `quality.nc.manage`, `sanitation.cycle.execute` — conferida no instante do aceite contra
as permissões de *quem está confirmando*. Sem essa separação, "propor" seria um caminho lateral para fazer pela
IA o que a pessoa não pode fazer pela porta da frente.

Três consequências deliberadas:

- **A verificação mora no caso de uso, não no controller.** `ProposalController` não chama
  `requirePermission` no aceite: a alçada exigida depende da ação proposta, que só se conhece depois de
  carregar a proposta, e a regra é sobre quem consente e não sobre por onde a requisição entrou.
- **Quem propôs pode não poder confirmar, e quem confirma pode nunca ter proposto.** As duas colunas existem
  separadas (`proposed_by`, `decided_by`) e as duas são auditadas.
- **Recusar não exige a alçada do comando.** Dizer "não" a uma sugestão não altera nada; exigir alçada para
  descartar deixaria propostas pendentes acumulando até vencer — e uma tela cheia de pendências treina quem a
  lê a ignorar a tela inteira, inclusive as que valem. Vale para proposta vencida também: se não valesse, a
  lista nunca se limparia.

### DEC-AIA-004 — Proposta é a única coisa da sprint que vira tabela

V95 e V96 são só de permissão: resposta e avaliação são derivadas das fontes e dos fatos, e guardá-las criaria
cópias que envelhecem (DEC-RAG-005). Uma decisão humana é o oposto — é fato do passado, e fato do passado se
guarda. Sem a tabela não haveria onde registrar quem consentiu, e "a IA fez" seria a única explicação possível
para uma alteração de custo ou de qualidade.

Duas invariantes ficam no banco e não só no código: `CHECK` de allowlist para que uma ação removida do código
não continue aceitável por inserção antiga, e `CHECK` de que pendente não tem decisão e decidida sempre tem
autor e instante.

### DEC-AIA-005 — Proposta vence em 12 horas; vencimento é derivado, não estado

Uma proposta foi feita sobre os fatos de um instante: o custo estava incompleto, a medição estava fora da
faixa, o tanque estava sujo. Aceitá-la três dias depois é agir sobre um retrato antigo — convincente
justamente porque parece atual. Doze horas cobrem um turno e a leitura do turno seguinte, e não cobrem o fim
de semana, que é o intervalo em que os fatos de um lote mudam sem ninguém olhar.

Não há status `EXPIRED`: uma proposta não muda de estado porque o tempo passou, ela só deixa de ser aceitável.
O aceite de uma vencida responde **410 Gone** e não 409 — 409 diria "tente de novo", e tentar de novo a mesma
proposta é exatamente o que não se deve fazer.

### DEC-AIA-006 — Allowlist e schema saem da mesma enum

`CommandProposalHandler.schema()` monta o `enum` do JSON Schema a partir de `ProposedAction.names()`. Se as
duas listas fossem escritas separadas, um dia o schema aceitaria ação que o domínio não conhece — ou o
contrário, que é pior, porque calaria a ação nova sem erro nenhum.

O corpo do aceite leva **só a observação**: nem ação, nem parâmetros, nem quem decide. Todos vêm da proposta
gravada e do contexto autenticado, e é isso que impede confirmar uma coisa na tela e executar outra no
servidor.

Parâmetro faltando **e parâmetro inesperado** recusam a proposta, que é descartada em vez de guardada — uma
proposta malformada guardada é uma proposta que alguém acaba confirmando. O caso concreto está num teste: o
modelo mandando `concentracao: 2%` junto do ciclo de limpeza, que é justamente o parâmetro químico que o POP
dita e ninguém inventa.

### DEB-AIA-002 — O aceite registra a decisão autorizada; não executa o comando

Nenhum módulo publicava porta de comando no pacote raiz — só consultas. Criar `costing.CostCommands`,
`quality.NonConformityCommands` e `sanitation.CycleCommands` é mudança nas histórias daqueles módulos, não
nesta.

**Atualização (Sprint 15, DEB-INT-001):** `fermentation.FermentationCommands` existe e é a primeira porta de
comando publicada do projeto. O obstáculo deixou de ser "não se sabe como fazer" e virou trabalho nos três
módulos — com um precedente que também mostra a forma: porta estreita, um comando só, sem ator humano quando
não há um. Os três casos daqui são diferentes num ponto que importa: eles **têm** ator humano (quem confirma
a proposta), então a porta precisa carregar o ator e auditar, ao contrário daquela. Consequência prática: quem confirma tem a decisão gravada e auditada, e é levado à rota onde o comando
vive (`ProposedAction.executionRoute`) para praticá-lo — o consentimento está registrado, a execução é um
segundo passo manual.

**Critério de remoção:** quando os três módulos publicarem porta de comando, o aceite passa a invocá-la na
mesma transação da gravação da decisão, e `executionRoute` deixa de ser necessário.

### DEB-AIA-001 — RESOLVIDO: a fermentação entrou nos fatos

**Critério de remoção cumprido.** `fermentation.FermentationLookup` publica o retrato do lote e o
`BatchFactsAssembler` passou de cinco para seis fontes. A avaliação enxerga o que estava cego: curva (última
densidade, última temperatura, quantas leituras), agenda (planejadas, executadas, **atrasadas**) e geração da
levedura inoculada.

`etapas_atrasadas` é o fato de maior valor da leva. É o sinal mais direto de lote em apuros e o único que o
modelo não teria como inferir dos outros — antes disto, um lote com três etapas vencidas e densidade parada
chegava ao modelo idêntico a um lote saudável.

**Retrato, não série — e a razão mudou de peso durante o trabalho.** A tentação era publicar a curva inteira.
Desde `DEB-INT-001`, a telemetria alimenta a fermentação: um dispositivo de 30 segundos gera 2.880 pontos por
dia, e uma fermentação de duas semanas passa de 40 mil. `findSeries` devolveria todos para o consumidor
descartar 39.999 a cada avaliação. Entrou `ReadingRepository.latestOf`, com `DISTINCT ON (kind)`, que traz a
ponta de cada grandeza em uma consulta. É o tipo de custo que a ligação anterior criou e que teria passado
despercebido até um lote longo.

**Ausência continua sendo ausência, em três níveis.** O retrato é `Optional`: lote que nem chegou ao
fermentador devolve vazio, e o fato vira `fermentacao` ausente. Dentro dele, grandeza nunca medida é `null`,
não zero — densidade zero não existe em cerveja, densidade ausente existe o tempo todo. E levedura não
vinculada é `null`, não geração zero: a primeira quer dizer levedura nova. Colapsar qualquer um dos três
faria a avaliação ler "0 etapas atrasadas" de um lote sem agenda como um lote rigorosamente em dia — a
conclusão oposta da verdadeira. Há teste para cada um.

**Leitura sinalizada como inválida conta e aparece.** Escondê-la faria a avaliação ver uma curva que parou,
quando o que houve foi um sensor entregando absurdo — dois problemas diferentes, com respostas diferentes.

### DEB-AI-001 — Mês do orçamento vira em fuso configurado, não no da cervejaria

O mês devia virar no fuso da cervejaria, mas `BreweryRef` não expõe fuso e ampliar a API publicada de
outro módulo não pertence a esta história. Enquanto isso vale `brassia.ai.budget-zone` por instalação
(default `America/Sao_Paulo`); o erro se limita às horas de virada do mês.
**Critério de remoção:** quando `BreweryRef` passar a expor o fuso, ler dali e apagar a propriedade.

### Pendência declarada — falta exercitar uma geração bem-sucedida contra o provedor real

Os testes automatizados rodam com o provedor desligado, que é o default do produto e a configuração que
vale para qualquer instalação sem contrato de IA; pôr chave de terceiro na CI não é opção.

**Verificado manualmente com `brassia.ai.enabled=true` e chave inválida** (stack local, `8081`), o que
exercita o adaptador real contra a fronteira HTTP da Anthropic:

- a aplicação sobe com IA habilitada e o status reporta `enabled: true` com a cadeia
  `[claude-opus-5, claude-sonnet-5]`, timeout e preços vindos da configuração;
- `POST /ai/gateway/probe` tentou **os dois modelos em ordem** e devolveu `503 ai_provider_unavailable`;
- o ledger gravou **as duas tentativas separadas** (`PROVIDER_FAILED`, 799 ms e 427 ms), com custo zero
  porque a recusa veio antes de gerar, e motivo `UnauthorizedException` — nome do tipo do erro, sem
  nenhum trecho de prompt.

**O que ainda não foi exercitado:** uma geração bem-sucedida e a aceitação da resposta pelo contrato
contra um modelo de verdade. A recusa por contrato está coberta por unidade
(`JacksonStructuredResponseReaderTest`, 8 casos) e o `probe` existe justamente para fazer essa
verificação em minutos quando houver chave.

### Pendência declarada (RAG-001) — indexação recebe texto, não arquivo

Guardar o PDF ou o DOCX original exigiria armazenamento S3/MinIO, que a história não pede, e extrair texto
de PDF exigiria um parser novo. A indexação recebe o texto; `sourceUri` aponta para onde o original vier a
morar. Consequência prática: hoje alguém cola o texto do manual. Quando entrar upload de arquivo, o
`checksum` já permite detectar reindexação do mesmo conteúdo.

Documento em inglês é indexado com o dicionário português — aceitável enquanto o corpus é local, e o
`sourceUri` permite reindexar depois. Registrado no ADR 0015.

## Evidências de encerramento

- Build/commit: `mvnw verify` verde (934 unitários + 620 de integração), `eslint` e `ng build` limpos,
  E2E 45/45 contra API real
- Testes executados (AIA-001):
  - Unitários: 819 verdes (`mvnw test`), dos quais 36 novos do módulo `ai`
  - Integração: `AiGatewayIT` 10/10 com PostgreSQL 18 real (Testcontainers), migrations desde banco vazio
  - Limites de módulo: `ModularityTest` verde — domínio e casos de uso não alcançam o SDK do provedor
  - Frontend: 354 verdes (`ng test`), 11 novos de `AiStore`; `eslint` e `ng build` limpos
  - E2E: `e2e/tests/ai-gateway.spec.ts` 3/3 contra API real
  - Verificação visual: desktop 1440px e mobile 390px
  - Manual com IA habilitada: fallback nos dois modelos e ledger, ver pendência declarada
- Testes executados (RAG-001):
  - Unitários do domínio: 35 novos (`ChunkerTest` 17, `EffectivityTest` 8, `KnowledgeDocumentTest` 10)
  - Integração: `KnowledgeIT` 13/13 com PostgreSQL 18 real — busca em português, `unaccent`,
    filtro de permissão, vigência por data e isolamento entre cervejarias
  - Frontend: 364 verdes, 10 novos de `KnowledgeStore`; `eslint` e `ng build` limpos
  - E2E: `e2e/tests/knowledge.spec.ts`
- Testes executados (RAG-002):
  - Unitários: 26 novos (`GroundingTest` 13, `GroundedAnswerHandlerTest` 13), incluindo três de injeção
  - Integração: `CopilotIT` 8/8 com **provedor programável** e PostgreSQL real — documento com ordem
    plantada indexado e recuperado de verdade; comando devolvido pelo modelo recusado com 502
  - Frontend: 372 verdes, 8 novos de `CopilotStore`; `eslint` e `ng build` limpos
  - E2E: `e2e/tests/copilot.spec.ts`
- Testes executados (AIA-002):
  - Unitários: 29 novos (`FactGroundingTest` 16, `BatchAssessmentHandlerTest` 13)
  - Integração: `BatchAssessmentIT` 4/4 — monta o caso de uso com as cinco consultas publicadas **reais** e
    cobre o contrato HTTP. **Não** cobre fatos de um lote com dados reais: montar um lote completo pela API são
    ~200 linhas já escritas em `BatchReportIT`, e duplicá-las aqui testaria a produção, não a avaliação. A
    montagem dos fatos é coberta por unidade com dublês das mesmas consultas, cujo comportamento real é testado
    no IT do módulo que as publica. Declarado no Javadoc do IT.
  - Frontend: 380 verdes, 8 novos de `AssessmentStore`; `eslint` e `ng build` limpos
  - E2E: `e2e/tests/assessment.spec.ts`
- Testes executados (AIA-003):
  - Unitários: 25 novos (`CommandProposalTest` 11, `CommandProposalHandlerTest` 14) — a assimetria entre
    aceitar e recusar, o vencimento, a definitividade da decisão e a recusa de parâmetro faltando/inesperado
  - Integração: `CommandProposalIT` 10/10 com PostgreSQL 18 real — **quem só pode propor recebe 403 no
    aceite e a proposta continua pendente**, o aceite com alçada grava a decisão e a linha de auditoria é
    conferida no banco, segunda confirmação é 409, vencida é 410, proposta de outra cervejaria é 404.
    As propostas são inseridas direto no banco: chegar a uma pela porta da frente exige um lote completo
    (~200 linhas já escritas em `BatchReportIT`) mais uma chamada ao modelo, e duplicá-las testaria a
    produção e não a decisão. O caminho de *propor* é coberto por unidade com dublês. Declarado no Javadoc.
  - Frontend: 389 verdes, 9 novos de `ProposalStore`; `eslint` e `ng build` limpos
  - E2E: `e2e/tests/proposals.spec.ts` 4/4; suíte completa 45/45
  - Verificação visual: desktop 1440px (claro e escuro) e mobile 390px, com propostas de amostra inseridas
    no banco local e removidas depois — sem provedor a tela nunca renderizaria uma proposta. Dois ajustes
    saíram da inspeção: a lista de lotes empurrava as propostas pendentes para muito abaixo da dobra (o
    pedido foi para o fim da página, porque a tela existe para decidir), e chave e valor do parâmetro
    ficavam distantes num grid de 3/9 (viraram par inline, porque quem confirma precisa lê-los juntos)
- Migrations aplicadas: `V93__ai_model_gateway.sql`, `V94__knowledge_document.sql`,
  `V95__ai_grounded_answer.sql`, `V96__ai_batch_assessment.sql` (as duas só de permissão — nem a resposta nem
  a avaliação têm tabela, ver DEC-RAG-005), `V97__ai_command_proposal.sql` (a única com tabela, ver
  DEC-AIA-004)
- ADR: `docs/adr/0015-knowledge-retrieval.md`
- Contratos atualizados: `contracts/openapi.yaml` (`/ai/gateway*`, `/ai/copilot/ask`,
  `/ai/copilot/batches/{batchId}/assessment`, `/ai/proposals*`, `/knowledge/documents`, `/knowledge/search`)
- Riscos remanescentes: ver as pendências declaradas acima — em especial DEB-AIA-002 (o aceite não executa o
  comando) e a geração bem-sucedida contra provedor real, que segue não exercitada
- Aceite: as cinco histórias concluídas com testes de domínio, integração com PostgreSQL real, frontend,
  E2E, OpenAPI e verificação visual
