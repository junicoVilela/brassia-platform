# Status — Sprint 11

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| MTR-001 | Concluída | IA | V74 + `MetrologyIT` (18 testes) | Novo módulo `metrology`; porta publicada `InstrumentStatusLookup` |
| MTR-002 | Concluída | IA | V75 + `MetrologyIT` (26 testes) | Temperatura pelo hub; curva no domínio |
| QLT-001 | A fazer | — | — | — |
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
