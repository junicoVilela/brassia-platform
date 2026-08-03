# Status — Sprint 11

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| MTR-001 | Concluída | IA | V74 + `MetrologyIT` (18 testes) | Novo módulo `metrology`; porta publicada `InstrumentStatusLookup` |
| MTR-002 | Concluída | IA | V75 + `MetrologyIT` (26 testes) | Temperatura pelo hub; curva no domínio |
| QLT-001 | Concluída | IA | V76 + `QualityIT` (16 testes) | Novo módulo `quality`; fecha MTR-001-A |
| QLT-002 | A fazer | — | — | — |
| SEN-001 | A fazer | — | — | — |
| SEN-002 | A fazer | — | — | — |

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
- **MTR-001-B — "aprovado com restrição" não estreita a faixa automaticamente.** A restrição é
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

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
