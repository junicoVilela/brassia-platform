# Status — Sprint 11

Estado: ACEITA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| MTR-001 | Concluída | IA | #131 — V74 + `MetrologyIT` (18 testes) | Novo módulo `metrology`; porta publicada `InstrumentStatusLookup` |
| MTR-002 | Concluída | IA | #132 — V75 + `MetrologyIT` (26 testes) | Temperatura pelo hub; curva no domínio |
| QLT-001 | Concluída | IA | #133 — V76 + `QualityIT` (16 testes) | Novo módulo `quality`; fecha MTR-001-A |
| QLT-002 | Concluída | IA | #134 — V77 + `QualityIT` (25 testes) | Encerrar exige verificação eficaz |
| SEN-001 | Concluída | IA | #135 — V78 + `SensoryIT` (14 testes) | Novo módulo `sensory`; cegueira na API |
| SEN-002 | Concluída | Claude | `sensory/domain/SensoryDescriptor`, `LicenseTier`, `Hypothesis`, `V109__sensory_descriptor.sql` | Vocabulário com sinônimos, fonte e licença como invariante; causa é hipótese com verificação. Ver DEC-SEN-001. |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### MTR-001

- **A aptidão do instrumento é derivada, nunca armazenada.** `FIT`, `EXPIRED`, `UNCALIBRATED`,
  `REJECTED`, `BLOCKED` e `RETIRED` saem do estado cadastral + última calibração + data da
  consulta. Uma coluna "apto" envelheceria sozinha e passaria a mentir no dia seguinte ao
  vencimento — é o mesmo princípio do volume derivado em PKG-001.
- **Bloqueio e baixa precedem o vencimento na aptidão.** Instrumento bloqueado não vira "vencido"
  ao passar do prazo: continua bloqueado, porque alguém o tirou de circulação de propósito e a
  tela precisa dizer isso.
- **A última calibração decide, inclusive quando reprova.** Uma reprovação derruba a aptidão
  mesmo que a aprovação anterior ainda estivesse no prazo: o instrumento falhou na verificação.
- **Padrão vencido não calibra.** Calibrar contra padrão fora da validade produz um número com
  aparência de rastreável e sem rastreabilidade nenhuma — pior que não calibrar, porque passa a
  impressão de evidência. Recusado com 409 `standard_expired`.
- **A periodicidade não é calculada pelo sistema.** O vencimento vem do certificado; o prazo
  depende da norma, do tipo de instrumento e da criticidade, e derivá-lo de regra fixa de meses
  criaria regra de negócio sem fonte. Mesma postura de `GAS-001-B`.
- **O certificado permanece.** Histórico imutável: registrar calibração nova não reescreve a
  anterior, e vencer não apaga nada. É o critério da história, e tem teste de domínio e de
  integração.
- **MTR-001-A — "ponto crítico" aqui é designação do instrumento, não vínculo com plano de
  controle.** O critério da história diz que instrumento vencido bloqueia ponto crítico, mas o
  ponto de controle nasce na QLT-001. Nesta história o instrumento carrega a designação de uso
  crítico (designar exige estar apto; a designação cai sozinha quando vence) e a porta publicada
  `InstrumentStatusLookup` responde `fitForCritical`. Critério de remoção: ao modelar QLT-001,
  ligar instrumento ↔ ponto de controle e mover a verificação para o momento da medição.
- **MTR-001-B — FECHADO EM 2026-08-11 COMO "NÃO VAI SER FEITO"** (ver DEC-DEBT-001 na Sprint 17):
  interpretar a restrição exigiria inventar semântica sobre texto livre, e uma faixa adivinhada errada é
  pior que nenhuma — ela parece conferida. O caminho continua humano. Registro original: a restrição é
  texto obrigatório e viaja junto da aptidão para quem consulta, mas o sistema não a interpreta:
  interpretar "faixa útil de 0 a 60 °C" exigiria parsear texto livre e inventar semântica.
  Critério de remoção: dar estrutura à restrição (faixa reduzida tipada) e validar a medição
  contra ela.

### MTR-002

- **A decisão de MTR-001 foi revisada pelo próprio critério que a acompanhava.** Estava escrito
  que a história usaria o hub `calculator`, "revisar se a curva de calibração não couber no
  contrato de entradas do hub". Não cabe: `CalculatorEngine.compute` recebe
  `Map<String, BigDecimal>` — só escalares — e uma curva é lista de pares. Em vez de alargar um
  contrato publicado que todos os módulos consomem, separamos: **temperatura no hub**
  (`hydrometer-temp-correction`, fórmula compartilhada e versionada) e **interpolação da curva no
  domínio de metrologia**. A curva não é fórmula: é dado do certificado daquele instrumento, e
  colocá-la no hub obrigaria o hub a conhecer certificado de calibração.
- **A ordem dos passos é temperatura e depois curva.** A curva foi levantada comparando o
  instrumento ao padrão em condição de referência, então ela se aplica ao valor já compensado por
  temperatura.
- **Fora da faixa conferida, a correção é recusada em vez de extrapolada** (409
  `outside_curve_range`, com os limites). Extrapolar produziria um número com aparência de
  corrigido sobre uma região que ninguém verificou.
- **Curva não monótona é recusada:** se uma leitura maior correspondesse a uma referência menor, a
  mesma indicação teria dois valores verdadeiros possíveis e a correção viraria adivinhação.
- **Certificado reprovado não fornece curva** — ela descreveria um instrumento que falhou.
- **O bruto é imutável e corrigir de novo cria outro registro.** Sobrescrever apagaria o rastro de
  como um número foi obtido, que é o que permite auditar uma liberação meses depois.
- **Instrumento não apto não impede corrigir: vira ressalva.** Seguimos o precedente de FSL-001 —
  purga não conferida e vedação reprovada não mudam o número, mudam a confiança nele. A aptidão do
  momento fica gravada e `trustworthy` cai. Recusar esconderia a medição; aceitar em silêncio
  mentiria sobre a evidência.
- **Não confundir com CAL-002.** `production.AppliedCorrection` é correção *de processo* no dia de
  brassa; esta é correção *metrológica* do que o instrumento leu. A colisão de nome entre os dois
  controllers apareceu no contexto do Spring e foi resolvida nomeando o daqui
  `ReadingCorrectionController`.
- **Fora de escopo:** ligar a correção às leituras de fermentação (FER-002). A correção referencia
  a leitura de origem por id opaco, sem acoplar os módulos — integrar seria antecipar escopo.

### QLT-001

- **O plano é versionado e publicado**, como perfil de fermentação (FER-001) e modelo de rótulo
  (PKG-004). A medição grava contra qual versão foi julgada: apertar um limite hoje não
  transforma em desvio uma medição que estava conforme ontem. Rascunho não julga (409
  `plan_not_published`) — ele pode ter limite pela metade, e o veredito mudaria ao salvar a edição.
- **Nova versão copia os pontos com identidade nova.** Reaproveitar os ids faria duas versões
  apontarem para o mesmo ponto, e uma medição antiga passaria a referenciar o limite da versão
  nova — o oposto do que o versionamento serve. Encontrado pela IT e coberto por teste de domínio.
- **Limite unilateral é caso normal, não exceção.** "O₂ ≤ 50 ppb" e "atenuação ≥ 75%" são
  especificações reais; exigir os dois lados obrigaria a inventar o que falta, e limite inventado
  vira desvio inventado. Os limites são inclusivos: o valor no limite está conforme.
- **Ação é obrigatória no ponto.** Ponto sem ação não é controle, é observação — registra-se que
  algo saiu da faixa e ninguém sabe o que fazer. O desvio copia a ação na abertura, para continuar
  dizendo o que fazer mesmo que uma versão futura mude a prescrição.
- **A severidade é do ponto, não da medição.** Quem decide o quanto importa sair da faixa é quem
  escreveu o plano, antes de qualquer medida existir; deixar para o momento do desvio abriria
  espaço para minimizar o problema depois de ele acontecer.
- **Fecha o débito MTR-001-A.** A designação de uso crítico criada em MTR-001 encontra aqui o seu
  uso: medir num ponto crítico exige instrumento apto, verificado pela porta publicada
  `InstrumentStatusLookup` no momento da medição — que é quando importa. Em ponto não crítico a
  medição passa com instrumento vencido, mas grava a aptidão e sinaliza.
- **Desvio grave avisa na central do lote** (`BatchAlertPublisher`, PRD-006), como FER-004 faz com
  etapa atrasada, em vez de manter uma segunda central. Alerta é aviso: não muda estado do lote.
- **QLT-001-A — a frequência é declarada, não fiscalizada.** O plano registra a cadência, mas
  ninguém é avisado de medição atrasada: isso pede varredura agendada, o mesmo débito aberto desde
  FER-004. Critério de remoção: existir agendador na plataforma e ligá-lo à cadência do ponto.
- **Colisões de nome no Spring.** O scan é global por nome simples: `JdbcMeasurementRepository` e o
  bean `recordMeasurementUseCase` já existiam em `production`. Renomeados aqui para
  `JdbcQualityMeasurementRepository` e `recordQualityMeasurementUseCase`.
- **Fora de escopo:** o tratamento do desvio (conter, investigar, agir, verificar eficácia) é
  QLT-002. Aqui ele só nasce — um fluxo de CAPA pela metade seria pior que nenhum, porque daria a
  impressão de que o desvio está sendo tratado.
- **PKG-002-A e PKG-001-A cabem como pontos deste plano** quando os números forem definidos:
  pressão máxima por embalagem e validade do CIP são parâmetro + faixa + ação, que é exatamente a
  forma do ponto de controle. Nenhum dos dois foi inventado aqui.

### QLT-002

- **A não conformidade é agregado próprio, não um estado a mais no desvio.** Desvio é medição fora
  da faixa; NC também nasce de reclamação de cliente, auditoria e fornecedor. Origem `DEVIATION`
  exige apontar um desvio existente, senão o encerramento não teria o que fechar.
- **As fases têm ordem e o domínio a impõe:** não se investiga o que não se conteve, não se age sem
  causa raiz, não se verifica sem ação concluída. Pular etapa é o jeito mais comum de um CAPA virar
  teatro — fica o registro de que algo foi tratado sem que nada tenha sido.
- **Verificação ineficaz devolve à fase de ação; não encerra.** Fechar com verificação negativa
  produziria um registro dizendo que o problema foi resolvido quando ele não foi — pior que não
  verificar, porque a próxima auditoria encontraria a prova documental de uma solução inexistente.
  A negativa fica no histórico como evidência de que a primeira tentativa não resolveu.
- **Encerrar a NC encerra o desvio de origem**, no mesmo commit. É o ciclo aberto em QLT-001 se
  fechando: a medição abriu o desvio, e ele só se encerra quando o tratamento provou eficácia. Um
  fechado sem o outro mentiria em uma das duas telas.
- **Corretiva e preventiva são separadas de propósito.** Descartar o lote afetado é corretivo e não
  impede o problema de voltar; um CAPA só com ação corretiva é um CAPA que vai se repetir.
- **O método da investigação é obrigatório junto da causa.** "Contaminação" sem dizer como isso foi
  determinado é palpite com aparência de conclusão — e é sobre essa conclusão que a ação preventiva
  será desenhada.
- **Prazo vencido é derivado na consulta**, nunca coluna: coluna de "atrasado" envelheceria sozinha
  e exigiria a varredura agendada que a plataforma não tem. Assim esta história não esbarra no
  débito QLT-001-A.
- **Encerrar é alçada própria** (`quality.nc.close`, marcada como crítica): é o ato que declara o
  problema resolvido.
- **QLT-002-A — os prazos são informados, não derivados da severidade.** O tempo aceitável para
  conter depende do porte da operação e do tipo de problema; derivá-lo de regra fixa criaria número
  sem fonte. Critério de remoção: a tela de parametrização por cervejaria (ver PRM-001 abaixo).

### SEN-001

- **A cegueira é garantida na resposta da API, não na tela.** A amostra sai sem lote enquanto a
  sessão não é encerrada, e o resultado é recusado com 409. Deixar isso para o frontend significaria
  que qualquer cliente — inclusive o navegador com o devtools aberto — enxergaria o que o provador
  não deveria.
- **O código cego é aleatório de três dígitos, sorteado pelo sistema.** Sequencial vazaria a ordem
  de preparo, e ordem é informação: quem percebe "a 001 é a primeira" começa a inferir o que está
  provando. O cliente não escolhe o código.
- **O mesmo lote pode entrar duas vezes sob códigos diferentes** — de propósito. Duplicata cega é
  a técnica clássica para medir a consistência do painel: se o mesmo lote recebe notas muito
  distintas, o problema está em quem prova, não no que se prova. O resultado traz essa comparação
  separada, e é a resposta ao risco de **viés sensorial** declarado no plano de testes da sprint.
- **A ficha é imutável e há uma por provador e amostra.** Sem isso bastaria esperar o fechamento,
  ver o resultado e reescrever a própria avaliação. Não existe endpoint de alteração, e o
  repositório não tem update de ficha — a imutabilidade é propriedade da persistência, não só do
  domínio.
- **A auditoria não vaza resultado.** Os eventos de envio de ficha e de inclusão de amostra
  registram o que aconteceu sem nota e sem o par código↔lote: a trilha de auditoria não pode ser a
  fresta por onde o resultado escapa antes do fechamento.
- **O vínculo ao lote nunca é apagado** — é o outro lado do critério. Ele existe no registro desde
  a montagem; apenas não é revelado.
- **Ficha incompleta é recusada:** faltando um atributo, a média do painel compararia coisas
  diferentes.
- **Fora de escopo:** a biblioteca estruturada de descritores e off-flavors é a SEN-002. Aqui os
  descritores são texto livre.

### PRM-001 — parametrização por cervejaria (proposta)

Levantada pelo mantenedor durante a QLT-002: **tudo que hoje é "valor que depende de cada
cervejaria" deveria virar uma tela de parametrização**, para cada casa ajustar conforme a sua
política — em vez de continuar como débito espalhado.

A plataforma já tem o padrão pronto: `brewery.OperationalPreferences` é mutável com trava otimista
e **gera revisão imutável a cada alteração**, para que consumidores futuros não reinterpretem o
passado. Também já existem políticas por cervejaria em `YeastPolicy` (YST-002),
`ShelfLifePolicy` (FSL-001) e a regra de rótulo (PKG-004) — o que confirma o padrão e mostra que
ele está espalhado por módulos.

Débitos que a história resolveria de uma vez:

| Débito | Parâmetro |
|---|---|
| `PKG-001-A` | Prazo de validade da liberação de CIP (horas) |
| `PKG-002-A` | Pressão máxima por tipo de embalagem |
| `GAS-001-B` | Periodicidade de requalificação de cilindro |
| `MTR-001` | Periodicidade de calibração por tipo de instrumento |
| `QLT-002-A` | Prazos de contenção, investigação e verificação por severidade |
| `SEN-001` | Escala da ficha (0–10 ou BJCP) e conjunto de atributos sensoriais |

**Decisão de arquitetura (aplicada): cada módulo mantém a sua política; a tela reúne.**

Centralizar os parâmetros em `brewery.OperationalPreferences` daria uma tela mais simples, mas
faria o módulo `brewery` conhecer pressão de embalagem, requalificação de cilindro, calibração,
prazos de CAPA e escala sensorial — conceitos de cinco outros módulos. O `ModularityTest` acusaria,
e com razão: seria o mesmo tipo de acoplamento que as consultas publicadas existem para evitar.

O caminho recomendado espelha o que a plataforma já faz em `YeastPolicy` (YST-002),
`ShelfLifePolicy` (FSL-001) e na regra de rótulo (PKG-004): a política vive no módulo dono do
conceito, e a tela de parametrização é um agregador de leitura/escrita que fala com cada módulo pela
sua porta. `OperationalPreferences` continua com o que é genuinamente transversal (unidades, moeda,
política de estoque) e ganha o padrão de revisão imutável como referência para os demais.

**Backend entregue antes da sprint 12** (PR de `feat/prm-001-parametrizacao-backend`), com migration
V79. **A tela veio no PR seguinte** (`feat/prm-001-tela-parametrizacao`), em `/settings/parameters`,
alcançada por um cartão novo na seção "Operação" do hub de configurações.

**Por que a tela salva seção por seção.** São cinco endpoints de cinco módulos: não há transação
abrangendo os cinco, e um botão único de "salvar tudo" prometeria atomicidade inexistente — uma
falha na terceira chamada deixaria as duas primeiras gravadas sem que ninguém soubesse. Cada seção
tem o seu botão, a sua permissão de escrita (`*.policy.manage`) e o seu erro. A leitura, essa sim,
é uma só: um `forkJoin` das cinco, porque uma tela pela metade não ajuda a decidir nada.

**Campo em branco é valor, não esquecimento** — e a tela diz isso em texto, embaixo de cada campo,
declarando qual comportamento vale hoje. É a metade da invariante que costuma se perder na
interface: quem apaga a validade do CIP precisa entender que acabou de desligar a expiração por
tempo, não que deixou de preencher algo.

**Verificação visual (5 telas, claro e escuro, desktop e mobile).** Feita contra a aplicação real
— backend, banco e `ng serve`, com screenshots por Playwright — e não mais contra um harness
estático que replicava o HTML à mão. A diferença não é de conforto: o harness replicado só prova
que a *cópia* está certa. Quatro achados, todos corrigidos:

1. **`ri-sliders-line` não existe** no Remix Icon empacotado pelo tema, e o cartão de
   Parametrização aparecia com o distintivo vazio. Trocado por `ri-sound-module-line`. Vale como
   lembrete: nome de ícone errado falha em silêncio, sem erro de console e sem quebrar teste.
2. **`<th scope="row">` não recebia o estilo de tabela.** As regras de `styles.scss` cobriam só
   `td`, então a primeira coluna do CAPA ficava com a borda sólida do Bootstrap no meio de uma
   tabela tracejada. Corrigido nos dois temas — é a tabela compartilhada, não só esta tela.
3. **Controles nativos ignoravam o tema escuro.** O seletor de data abria calendário branco e
   exibia o ícone escuro sobre campo escuro nas telas de instrumentos, planos de controle e
   sessões sensoriais. Resolvido com `color-scheme: dark` no bloco escuro, que também acerta
   spinners de `number` e barras de rolagem.
4. **Cartão sensorial com metade vazia.** Ele dividia a linha com a calibração, que tem sete
   campos e o esticava. Reorganizado: três cartões curtos em uma linha, calibração e CAPA em
   largura total.

**Correção da tabela acima:** dois itens que eu havia listado não são parâmetro de cervejaria e
saíram do escopo:

- `PKG-002-A` (pressão máxima por embalagem) é **dado do catálogo**, não da casa: lata, long neck e
  garrafa de champanhe têm limites diferentes entre si, independentemente da cervejaria. É atributo
  do ingrediente-embalagem, como `volumeMl` já é. Segue como item próprio.
- `PKG-004-A` (alergênicos) é o mesmo caso, e já está coberto pela `FDS-001` da sprint 12.

**Invariante que a história sustenta:** o parâmetro é opcional e **a ausência dele preserva
exatamente o comportamento anterior**. Sem validade de CIP a liberação não expira por tempo; sem
periodicidade o vencimento continua vindo do certificado; sem prazos de CAPA eles continuam
informados. Nenhuma migration muda comportamento de quem não configurar nada.

**Por que não há revisão imutável por política:** os agregados já guardam o que precisam no momento
da decisão — a NC grava os próprios prazos ao abrir, o certificado grava o próprio vencimento, o
plano de controle é versionado e a sessão sensorial congela a escala. Mudar um parâmetro afeta só
decisões futuras, então a trilha de auditoria basta como histórico.

**Débitos fechados:** `PKG-001-A`, `GAS-001-B`, `QLT-002-A` e a periodicidade de calibração da
`MTR-001`. O conjunto de atributos sensoriais continua fixo e segue aberto — parametrizá-lo
reestruturaria a ficha.

### Antes de começar

- **MTR-002 usa o hub `calculator`, não um motor próprio.** A história pede correção de leitura
  "mostrando fórmula e versão, com o original imutável" — que é exatamente o contrato do hub, já
  usado por `hydrometer-temp-correction` (anterior) e pelas cinco calculadoras que a sprint 10
  acrescentou. Criar um motor de correção dentro de `metrology` duplicaria versionamento de
  fórmula e abriria uma segunda fonte de verdade para o mesmo cálculo, contra
  `docs/05_CALCULATION_ENGINE.md`. O que cabe a `metrology` é o cadastro do instrumento, a curva
  de calibração e a guarda do valor original; o cálculo em si é chamada ao hub. **Revisar esta
  decisão se** a curva de calibração por instrumento não couber no contrato de entradas do hub —
  nesse caso a alternativa é o hub receber a curva como parâmetro, não `metrology` calcular.
- **Dependência das sprints 09 e 10:** ambas aceitas em 2026-08-03. Quatro débitos de decisão de
  negócio da sprint 10 seguem abertos e dois encostam neste escopo: `PKG-002-A` (pressão máxima
  por embalagem) e `PKG-001-A` (validade do CIP por tempo) são faixas de controle, e QLT-001
  define faixas de controle. Decidir onde essas duas vivem **antes** de modelar QLT-001 evita
  criar um segundo lugar para o mesmo dado.

### DEC-SEN-001 (SEN-002) — A história não pedia escolher catálogo; pedia modelar a licença

SEN-002 estava adiada por "depender de decisão sobre catálogo licenciado". Relendo os critérios, o quarto
diz: *"Conteúdo licenciado respeita atribuição e nível de permissão."* O que faltava não era **escolher** um
catálogo — era construir a estrutura que o respeita. Nenhum conteúdo licenciado foi embutido; a decisão
jurídica continua aberta, e agora tem onde entrar.

**A licença é coluna e invariante, não observação.** `LicenseTier` decide três coisas: se o limiar pode ser
gravado, se a atribuição é obrigatória, e se o descritor é exportável. Um campo de texto dizendo "ver
licença" dependeria de alguém ler antes de copiar o descritor para um relatório que sai da cervejaria.

**O limiar é recusado na criação, não filtrado na leitura.** Dado que não pode ser publicado e mesmo assim
está gravado é vazamento esperando exportação. O `CHECK` repete a regra no banco, para valer também em
carga direta.

Por que o limiar é o ponto sensível: descrever "papelão" é vocabulário comum; afirmar que o limiar do
trans-2-nonenal é 0,1 µg/L é reproduzir trabalho experimental de alguém — e é por isso que os catálogos de
referência cobram.

**O tipo se chama `Hypothesis`, e o nome é a garantia.** O critério exige que causa e ação sejam hipóteses,
não diagnóstico automático — e a diferença desaparece quando alguém lê "diacetil → parada de fermentação"
numa tela e vai mexer no tanque. Chamar de `Diagnosis` faria o mesmo dado significar outra coisa para quem
lê o código e, depois, para quem lê a tela. É a mesma decisão de `Estimate` (DTW-001) e `supported`
(OPT-001): o nome carrega o limite epistêmico. Um teste de reflexão vigia que nenhum campo do agregado
tenha "diagnos" no nome.

**A hipótese exige como verificar.** "Pode ser infecção" sem dizer como confirmar deixa quem lê com a
preocupação e sem o próximo passo. A probabilidade é qualitativa — um número daria falsa precisão a algo
que ninguém mediu nesta cervejaria.

**Sinônimos existem porque o vocabulário é regional e a série histórica não é.** Uma pessoa anota
"papelão", outra "cartonado", outra "molhado" — a mesma percepção. Sem sinônimos, a mesma cerveja aparece
com três problemas diferentes e nenhum acumula amostra para virar tendência. A normalização (sem acento,
sem caixa) acontece dos **dois** lados: no gravado e no digitado.

**O mesmo descritor muda de papel conforme o estilo.** Banana é atributo numa Weissbier e desvio numa
Pilsen. Um vocabulário que não distingue isso ensina errado justamente no treinamento, que é o objetivo da
história.

**O que continua sendo decisão sua:** qual catálogo de referência licenciar, se algum. A estrutura aceita
os três níveis e o conteúdo inicial é próprio.

## Evidências de encerramento## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:

## Evidências de encerramento

- **Build/commit:** `main` em `105a6ff`; PRs #131 (MTR-001), #132 (MTR-002), #133 (QLT-001),
  #134 (QLT-002) e #135 (SEN-001) mergeados por squash, nesta ordem.
- **Testes executados:** `./mvnw verify` — 628 unitários + 441 de integração
  (Testcontainers/PostgreSQL 18), verdes, incluindo `ModularityTest`; frontend `ng build` +
  `ng test` — 257 testes, verdes, ESLint sem warnings. **E2E: 9 jornadas** contra a stack real
  (`ng serve` + API empacotada + PostgreSQL), três delas criadas nesta sprint.
- **Migration aplicada:** V74 `metrology_instrument`, V75 `metrology_reading_correction`,
  V76 `quality_control_plan`, V77 `quality_non_conformity`, V78 `sensory_session`. Sequência
  contínua, sem alteração de migration já publicada.
- **Contratos atualizados:** `contracts/openapi.yaml` de 129 para 165 paths (+36). Dez códigos de
  Problem Details novos: `instrument_not_fit`, `standard_expired`, `outside_curve_range`,
  `plan_not_published`, `nc_phase_out_of_order`, `verification_required`, `results_not_available`,
  `session_not_open`, `already_evaluated` e a reutilização de `instrument_not_fit` em QLT-001.
- **Riscos remanescentes:** (1) o E2E cobre navegação e integração, não o fluxo de negócio ponta a
  ponta — item de backlog decidido no aceite; (2) `QLT-001-A` segue dependendo do agendador ausente
  desde FER-004; (3) SEN-002 adiada deixa os descritores como texto livre, sem sinônimos nem
  agregação confiável. **A verificação visual saiu da lista**: foi feita junto com a tela da
  PRM-001, em cinco telas, claro e escuro, desktop e mobile, contra a aplicação real — quatro
  achados corrigidos e registrados acima.
- **Aceite:** **Valdemir Vilela Junior, 2026-08-04** — aceita com as ressalvas registradas em
  `ACCEPTANCE.md`. `QLT-001-A`, `MTR-001-B` e a adiada `SEN-002` seguem abertos, e o item de E2E do
  DoD fica como jornada de negócio a escrever: o aceite libera a sprint, não os débitos.

## Débitos abertos ao fim da sprint

Três criados aqui: **QLT-001-A** (frequência declarada, não fiscalizada), **QLT-002-A** (prazos do
CAPA informados, não derivados da severidade) e **MTR-001-B** (restrição do certificado não estreita
a faixa). **MTR-001-A foi aberto e fechado dentro da própria sprint**: a designação de ponto crítico
criada em MTR-001 virou regra executável em QLT-001, verificada no momento da medição.

A **PRM-001 foi entregue antes da sprint 12** (#139 e #140) e fechou `PKG-001-A`, `GAS-001-B`,
`QLT-002-A` e a periodicidade de calibração da MTR-001. Restam abertos: `QLT-001-A` e `MTR-001-B`
daqui, `PKG-002-A` e `PKG-004-A`/`PKG-004-B` da sprint 10 (o primeiro virou item de catálogo, o
segundo cai na FDS-001 da sprint 12) e `CLN-004-A` da sprint 08.
