# Status — Sprint 16

Estado: EM ANDAMENTO

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| DTW-001 | Concluída | Claude | `backend/.../digitaltwin`, `V103__digital_twin_profile.sql`, `frontend/.../features/digital-twin` | Estimativa com faixa e confiança explícitas; amostra informada e gravada, o que torna o número reproduzível. Ver DEC-DTW-001/002/003. |
| SPC-001 | Concluída | Claude | `digitaltwin/domain/ControlLimits`, `ControlSignal`, `production/BatchMeasurementLookup` | Limite de controle é calculado e não pode ser injetado; deslocamento e tendência detectados. Ver DEC-SPC-001/002. |
| EXP-001 | A fazer | — | — | — |
| BLD-001 | A fazer | — | — | — |
| FLD-001 | A fazer | — | — | — |
| OPT-001 | A fazer | — | — | — |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### DEC-SPC-001 (SPC-001) — Limite de controle não é especificação, e a fronteira é estrutural

A diferença não é de fórmula, é de **origem**. Especificação vem de uma *decisão* (o estilo pede FG ≤ 1.014;
está em `quality.SpecLimits` e se escolhe). Controle vem de *observação* (é o que este processo produz
quando nada de anormal acontece; calcula-se do histórico e não se escolhe).

As duas combinações que a confusão esconde são as que importam:

- **Sob controle e fora de especificação** — o processo é estável e está estavelmente errado. Nenhum ponto
  dispara alarme e a cerveja está fora do prometido. Ajustar ponto a ponto não resolve: o processo inteiro
  precisa mudar. Há teste montando exatamente esse caso.
- **Fora de controle e dentro de especificação** — tudo passa na inspeção e o processo está mudando. É o
  aviso que chega antes do problema, e é justamente ele que se perde quando alguém usa o limite da
  especificação como se fosse de controle.

A fronteira ficou **estrutural, não documental**: `ControlLimits` não tem caminho público que aceite um
limite de fora — só `from(observações)`. Um teste por reflexão afirma que o único método estático público é
esse. E `ControlChartQueries.Chart` não tem campo para especificação, também afirmado por teste.

Duas constantes com justificativa:

- **20 observações no mínimo.** Com poucos pontos o desvio é instável e os limites oscilam a cada medição —
  disparando alarme ora por variação real, ora porque o próprio limite se mexeu. Um limite que se move não
  serve para dizer que algo mudou. Histórico curto é **recusado**, porque limites sobre cinco pontos passam
  qualquer coisa e um controle que nunca dispara parece um processo saudável.
- **Três sigmas.** ~99,7% dos pontos de um processo estável caem dentro, então um ponto fora tem ~0,3% de
  chance de ser acaso. Limites de 2σ alarmariam a cada vinte medições, e alarme falso frequente treina quem
  opera a ignorar o alarme — pior que não ter alarme.

### DEC-SPC-002 (SPC-001) — A série é do processo, e a ordem é cronológica

Uma carta de controle é sobre o **processo**, não sobre um lote: o momento em que ele muda cai entre dois
lotes tanto quanto dentro de um. Por isso a série atravessa a amostra e é ordenada por instante de medição.
A ordem é parte do contrato — uma lista ordenada por outra coisa produziria sinais que o processo nunca deu.

Três sinais, com sete pontos como comprimento de sequência (a chance de sete pontos caírem do mesmo lado por
acaso é ~1 em 128; menos alarmaria coincidência, mais atrasaria o aviso):

- **Ponto além de 3σ** — o mais forte.
- **Deslocamento** — sete do mesmo lado da linha central, **nenhum precisando estar perto de um limite**. É
  o caso que a inspeção ponto a ponto não pega: o processo mudou de patamar e continua estável nele.
- **Tendência** — sete seguidos subindo ou descendo. O aviso mais antecipado: descreve algo mudando agora,
  antes de qualquer ponto sair da faixa.

Detalhes que os testes fixaram: ponto exatamente na linha central não pertence a lado nenhum e interrompe a
sequência (contá-lo inventaria um deslocamento que ele não sustenta); empate interrompe a tendência (processo
parado não vai a lugar nenhum); e a sequência olhada é a **mais recente** — uma que terminou há trinta pontos
é história, não aviso.

**Unidades misturadas são recusadas, não convertidas.** °C e °F na mesma carta produziriam limites que não
descrevem nada, e a conversão pertence a quem registrou a medição.

**A carta não é persistida.** Ela é leitura da série que já existe — as medições são o registro. Guardá-la
criaria uma cópia que envelhece: uma medição corrigida amanhã deixaria a carta de hoje afirmando um limite
que os dados não sustentam mais.

**Nota de fronteira:** foi publicada `production.BatchMeasurementLookup`. `quality.BatchQualityLookup` só
devolve medições **fora** da faixa, porque responde a pergunta da qualidade — e um controle alimentado
apenas com os piores pontos calcula limites que não descrevem processo nenhum.

### DEC-DTW-001 — A média nunca viaja sozinha

"Rendimento de 92%" parece um fato e é um resumo: pode vir de **trinta lotes agrupados em torno de 92**, ou
de **dois — um de 84 e um de 100**. Mesma média, evidências opostas, decisões de planejamento
completamente diferentes. Por isso a estimativa carrega sempre o tamanho da amostra, a faixa e um rótulo — e
há teste dedicado exatamente a esse par.

Quatro escolhas dentro disso:

- **Uma observação não estima nada.** Não é confiança baixa, é ausência: a "faixa" seria o próprio ponto,
  uma precisão absoluta aparente construída sobre a menor evidência possível.
- **A faixa é intervalo de confiança da média, não a variação observada.** A variação diz o quanto os lotes
  diferem entre si; o intervalo diz o quanto ainda não se sabe sobre a média. É o segundo que encolhe com
  histórico, e é ele que denuncia que dois lotes não bastam.
- **Desvio amostral (n-1).** Os lotes observados são uma amostra do que a cervejaria produz, não o universo
  dela — usar `n` subestimaria a dispersão, que é o oposto do que uma estimativa honesta faz.
- **O rótulo existe porque a faixa não basta.** Com `1,96` (normal) em amostra pequena a incerteza é
  *subestimada* — o correto seria t de Student. Em vez de carregar uma tabela de distribuição, a estimativa
  vem marcada `LOW`, e é o rótulo que impede alguém de ler um intervalo estreito de três lotes como preciso.

### DEC-DTW-002 — A amostra é informada, e é isso que torna o perfil reproduzível

Não existe evento de conclusão de lote publicado pelo `production`, e o `BatchOutcomeLookup` responde por um
lote de cada vez. Em vez de forçar uma consulta de listagem em módulo alheio, o perfil aprende sobre **os
lotes que alguém escolheu** — e grava quais foram.

Parecia limitação e virou propriedade: quem conhece a operação pode excluir o lote em que a bomba falhou, e
a exclusão fica **visível** em vez de escondida dentro de uma consulta. Qualquer pessoa refaz a conta e
chega ao mesmo número, ou aponta que a amostra tinha um lote que não deveria estar lá.

Consequências:

- **Lote sem transferência é excluído, nunca contado como zero.** Um lote que ainda está fervendo não rendeu
  0% — ele ainda não rendeu. Contá-lo como zero arrastaria a média para baixo e, pior, **encolheria a
  faixa**, dando aparência de certeza a um número envenenado. O mesmo para lote de outra receita (aprender
  sobre A com lotes de B não descreve nenhuma) e de outra cervejaria (a consulta publicada simplesmente não
  resolve — o isolamento vale sem este módulo conhecer a tabela de produção).
- **Amostra em que nada serve é recusada** (422), não vira perfil vazio: um perfil sem observação daria a
  impressão de que a receita foi analisada.
- **A auditoria distingue lotes informados de lotes usados.** É a pergunta que alguém faz meses depois: "por
  que este perfil só olhou dois dos três lotes que pedi?".
- **Nenhum número é recalculado.** Volume planejado, transferido e perda vêm das consultas publicadas da
  produção; recalcular criaria uma segunda opinião sobre o mesmo fato. Este módulo *resume*.

### DEC-DTW-003 — Correlação não vira causa, e a fronteira está no tipo

`ProfileMetric` só admite grandeza **observada**. Não existe métrica que nomeie um porquê, e há teste
afirmando isso — a fronteira mora no tipo em vez de numa recomendação de revisão.

O rendimento também **não se chama "eficiência"**: quem lê "eficiência 74%" pensa em extração de açúcar do
malte, que é outra grandeza. Emprestar o nome técnico errado engana por vocabulário.

Duas decisões de forma que acompanham:

- **Versionado, nunca sobrescrito.** Um perfil calculado em maio guiou decisões em maio; recalcular em
  agosto e apagar o anterior faria essas decisões parecerem tomadas sobre números que nunca existiram. O
  repositório não expõe `UPDATE` nem `DELETE`.
- **Métrica sem amostra suficiente não desaparece do perfil.** Ausência declarada é informação; ausência
  silenciosa faria alguém concluir que a perda é zero. O `CHECK` do banco garante que média nula e
  `INSUFFICIENT` andam juntas nos dois sentidos.

### DEC-EXP-001 (EXP-001) — Uma variável isolada é condição de existência, não recomendação

O plano com mais de um fator diferente **não é criado**: `ConfoundedExperimentException` na fábrica do
agregado, 422 na API. Com dois fatores, qualquer resultado tem duas explicações e nenhuma pode ser
descartada — o experimento produz algo que parece conhecimento e não é. Aceitar com um aviso seria pior: o
aviso se perde e o número fica.

Nenhum fator diferente é recusado pelo mesmo caminho. Dois lotes idênticos não testam hipótese nenhuma, e o
registro ficaria parecendo um experimento à espera de resultado.

Três decisões de forma que sustentam a regra:

- **Os fatores iguais são declarados junto com o que difere.** "O resto ficou igual" é a afirmação sobre a
  qual toda a conclusão se apoia; sem os iguais gravados, ninguém confere meses depois que o tanque era
  mesmo o mesmo.
- **A hipótese é imutável.** Não há `UPDATE` para hipótese, fatores ou grandezas — o SQL sequer menciona
  essas colunas. Um experimento cuja hipótese pode ser reescrita depois do resultado sempre confirma a
  hipótese, e fica indistinguível de um que realmente previu o efeito.
- **O mesmo par de lotes não entra em dois experimentos ativos** (índice parcial). Dois experimentos sobre
  o mesmo par testam variáveis diferentes nos mesmos lotes, e aí nenhuma das duas está isolada. Quem decide
  é o PostgreSQL: entre duas requisições simultâneas, só o banco sabe qual chegou primeiro.

A checagem de que os dois lotes são da **mesma receita** fica na aplicação, sobre `production.BatchLookup`:
o domínio não conhece lote, só identificadores. Sem ela, um "controle" de outra receita faria a comparação
medir a diferença entre duas receitas e atribuí-la ao fator isolado — o resultado errado mais convincente
que este módulo poderia produzir, porque parece um experimento correto.

### DEC-EXP-002 (EXP-001) — A conclusão não tem campo para limitações

O critério pedia que a conclusão registrasse limitações. Um campo de texto livre atenderia à letra e
falharia na prática: limitação que depende de alguém lembrar de escrevê-la some justamente quando o
resultado agrada.

As limitações são **derivadas do desenho** — `SINGLE_PAIR` sempre, mais `SENSORY_NOT_BLIND`, `NO_SENSORY`,
`NO_PLANNED_MEASUREMENT` ou `SINGLE_METRIC` conforme o plano. Não há parâmetro para enviá-las, e `Conclusion`
recusa lista vazia. Concluir sem registrá-las não é proibido: é inexprimível.

Não são gravadas, e sim recalculadas na leitura — gravá-las abriria a possibilidade de uma conclusão cuja
lista foi editada. O plano é imutável, então a lista derivada é a mesma que a conclusão carregou na origem.

O campo se chama `supported`, nunca "provado": um par de lotes não prova nada, e o nome do campo é o que
impede o relatório de afirmar que provou. `experiment.plan.conclude` é permissão crítica separada de
planejar — planejar é uma intenção, concluir define o que a cervejaria passa a acreditar sobre a própria
receita.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
