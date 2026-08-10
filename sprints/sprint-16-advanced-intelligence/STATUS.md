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
### DEC-BLD-001 (BLD-001) — O balanço fecha na simulação, não na execução

Simular é o único momento barato: depois de mover cerveja entre tanques, descobrir que faltam 40 litros não
desfaz a mistura. `BlendOperation.simulate` recusa o desequilíbrio com `UnbalancedBlendException`; a
execução apenas confirma o que já estava fechado.

Entrada tem de igualar saída mais **perda declarada**. A perda é o caminho legítimo: quem perdeu 12 L na
transferência declara 12 L. O que não se aceita é a conta não fechar sem ninguém dizer por quê — cerveja que
entra e não sai foi para algum lugar, e aceitar em silêncio criaria volume do nada, que vira cerveja envasada
sem origem rastreável.

Tolerância de **0,10 L**, que é o limite da instrumentação e não folga para erro: medidor de tanque não
resolve mililitro, e exigir igualdade exata recusaria operações corretas por arredondamento — treinando quem
opera a inflar a perda declarada até a conta passar, o que destruiria o valor do próprio campo.

O volume é sempre positivo e o sentido vem do lado (`INPUT`/`OUTPUT`). Guardar o sentido no sinal do número
transformaria todo erro de sinal num balanço que fecha por acidente.

### DEC-BLD-002 (BLD-001) — Recall recalculado é consequência da aresta, não um passo

O critério pedia rótulo, alergênico e recall recalculados. A implementação **não tem rotina de recálculo** —
tem um `LineageSource`.

O módulo contribui arestas de genealogia como qualquer outro (`BlendLineageAdapter`), e a rastreabilidade as
percorre sem saber que blend existe. Assim que uma união é executada, o lote de destino passa a ter os de
origem como ancestrais; um recall que alcança qualquer um alcança os outros. Rótulo e alergênico, que derivam
da composição, atravessam a mesma genealogia. Nada dispara o recálculo: ele decorre da aresta existir.

A alternativa — um serviço que "recalcula tudo" ao executar — teria de conhecer rótulo, alergênico e recall
por dentro, e envelheceria a cada consumidor novo da genealogia. O `BlendIT` verifica a travessia real:
executada a união, o serviço de rastreabilidade devolve as origens; antes de executar, não.

**Só operações executadas contribuem.** Simulada e aprovada não moveram cerveja. Aresta prematura faria o
recall exagerar — e recall que exagera é descartado por quem o recebe, tão inútil quanto um que falta.

Aprovar e executar são permissões críticas separadas: uma autoriza misturar, a outra abre a válvula. Depois
de misturadas, duas cervejas não se separam — a operação é irreversível de um jeito que quase nenhuma outra
na plataforma é.

### DEC-BLD-003 (BLD-001) — PREMISSA DECLARADA: origem e destino são lotes que já existem

**Esta é uma pergunta de negócio em aberto, resolvida por premissa para não travar a entrega.**

A alternativa natural seria a operação *criar* um lote novo como resultado. Ela esbarra na estrutura:
`production_batch.order_id` é `NOT NULL` com `UNIQUE (brewery_id, order_id)`. Um resultado de blend não nasce
de uma ordem de produção, e inventar uma ordem sintética criaria uma ordem que ninguém programou — que
aparece no planejamento, que o custeio tentaria ratear e que o indicador de aderência contaria como desvio.

Implementei com **lotes pré-existentes dos dois lados**: a operação move volume entre lotes que já estão no
sistema. Tudo o mais da história funciona sobre isso — balanço, aprovação, execução, genealogia, recall.

**O que precisa de decisão:** um blend deve produzir um lote novo? Se sim, de onde vem a ordem dele — uma
ordem sintética marcada como tal, ou `order_id` passa a aceitar nulo com um `CHECK` que exige origem
alternativa? A resposta muda o modelo de produção, e não é minha para dar.

### DEC-FLD-001 (FLD-001) — A severidade exige, não sugere

O critério dizia que a severidade "pode abrir CAPA/quarentena". Implementado como sugestão, isso depende de
alguém concordar no dia em que está com pressa — e o dia em que se está com pressa é exatamente o dia em que
uma reclamação de corpo estranho é encerrada como "cliente contatado, caso resolvido".

As ações exigidas são **derivadas** de severidade + categoria (`RequiredAction.of`), e a reclamação **não
encerra** enquanto cada uma não tiver destino: `PendingActionsException`, 422 listando quais faltam.

A **categoria pesa junto com a severidade**, e essa é a decisão que sustenta o resto. Quem registra pode
classificar um corpo estranho como QUALITY por não querer alarmar, mas dificilmente vai errar a categoria.
Uma exigência que dependesse só da severidade cairia junto com a classificação subestimada.

A dispensa existe e é legítima — às vezes o corpo estranho era do copo do consumidor. Mas custa uma
justificativa com conteúdo (o domínio recusa "n/a"), fica assinada com autor e data, e vai inteira para a
auditoria. Sem isso, a dispensa seria indistinguível de esquecimento, e o histórico mostraria reclamações
graves "sem quarentena" sem dizer se alguém decidiu ou se ninguém olhou.

As exigências não são gravadas: derivam na leitura. Gravá-las abriria a possibilidade de uma reclamação de
corpo estranho com a lista editada para vazia.

Dois campos de contexto que existem para não culpar a fábrica pelo que aconteceu fora dela:
**armazenagem** (uma cerveja a 35 °C por duas semanas desenvolve off-flavor sozinha) e **amostra** (sem
amostra retida, quase nenhuma reclamação se confirma). Nos dois, nulo é "ninguém perguntou" e não "estava
tudo bem" — `conditionsKnown` torna a distinção explícita no contrato.

### DEC-FLD-002 (FLD-001) — O dado pessoal está fora da reclamação, e isso é o controle

Tabela própria, permissão crítica própria, endpoint próprio, leitura auditada. Quatro razões, nenhuma de
organização de código:

- **Tempo de vida diferente.** A investigação de um corpo estranho precisa durar anos; o telefone de quem
  ligou, não. Separados, um apaga sem levar o outro.
- **Alcance diferente.** Quem analisa off-flavor precisa do lote, da armazenagem e da amostra — não do
  endereço do consumidor. Uma permissão só faria todo analista ler dado pessoal de graça, todo dia.
- **Acesso auditável.** Em endpoint próprio, cada leitura é um evento — inclusive quando não há contato,
  porque registrar só o acerto deixaria de fora quem varre reclamações procurando dados. Como coluna, a
  leitura aconteceria dentro de um `SELECT` indistinguível de qualquer outro.
- **Erro por omissão vira erro visível.** O DTO da reclamação **não tem campo** para nome, telefone ou
  endereço. Não há o que esquecer de remover quando alguém montar uma tela nova — e um teste de reflexão
  vigia a estrutura do agregado.

O apagamento **esvazia o conteúdo e preserva a linha**. Apagar tudo tornaria indistinguível "reclamação
anônima desde o início" de "dados apagados a pedido", e a segunda precisa ser demonstrável — inclusive para
quem pediu. Um `CHECK` garante que registro apagado não guarda resto, e o endpoint devolve `erased: true`
em vez de 404.

O contato é **opcional**: reclamação anônima é reclamação. Exigir identificação para registrar um corpo
estranho coletaria dado desnecessário e perderia o relato de quem não quis se identificar.

### DEC-OPT-001 (OPT-001) — Restrições descartam; o objetivo ordena

As duas coisas não se misturam, e essa é a decisão central. Uma alternativa que viola qualquer restrição é
eliminada **antes** de qualquer score.

Somá-la ao score com um peso alto pareceria equivalente e não é: um peso, por maior que seja, é sempre
comprável por um ganho maior — e aí o resultado sai apresentado como ótimo tendo quebrado o que não podia.
Restrição não é preferência; uma solução que a viola não é uma solução pior, ela não é solução.

**Um objetivo por vez.** Custo, disponibilidade e alvo técnico se contradizem: o malte mais barato muda a
cor, o que está em estoque muda o amargor. "Otimizar tudo" entregaria uma média ponderada por pesos que
ninguém escolheu, apresentada como ótimo.

O score é normalizado como ganho relativo à receita original — um score absoluto dependeria da escala da
grandeza, e comparar 3,20 R$/L com 32 IBU não significa nada.

**Trade-offs são campo obrigatório**, e listam só o que *piorou*. Uma alternativa que aparece apenas com o
ganho — "8% mais barata" — esconde que mudou a cor em 4 EBC, e quem escolhe decide sem saber o que troca.
Listar as melhorias junto diluiria a leitura e faria o custo parecer menor.

### DEC-OPT-002 (OPT-001) — Reprodutível por construção, e a IA não tem por onde entrar

Método, versão publicada da receita, marca do catálogo e semente viajam com o resultado. Sem eles, seis
meses depois ninguém distingue "o solver mudou" de "o preço do malte mudou" — e um resultado que não se
reproduz não se audita.

A **marca do catálogo é derivada do conteúdo lido**, não da data: uma marca por data diria que a entrada
mudou todo dia mesmo sem nada ter mudado, perdendo exatamente a informação que ela existe para dar.

O solver é determinístico: enumera na ordem estável do id e desempata por rótulo. Sem o desempate, duas
candidatas de mesmo score poderiam trocar de posição entre execuções — a corrida deixaria de ser
reproduzível justo onde a diferença "não importa", que é o pior lugar para descobrir isso. `SolverMethod`
declara `usesSeed()`, e o domínio recusa a incoerência nos dois sentidos: semente em método determinístico
sugeriria variação inexistente; sua falta num método aleatório tornaria o resultado irreprodutível.

**A explicação da IA é comando separado e só recebe texto.** `candidates` é imutável e definido na criação;
`explain` escreve num campo à parte. Não existe caminho em que gerar explicação recalcule alguma coisa — um
teste de reflexão vigia a assinatura, porque a garantia é estrutural. Gerá-la dentro da otimização deixaria
a fronteira dependendo de disciplina de quem escreve o código.

**Inviabilidade é resposta, não erro** — 201 com o corpo dizendo quais restrições se contradizem.
Transformá-la em 4xx faria a tela tratá-la como falha e perder a informação que a torna acionável;
"inviável" sozinho manda a pessoa afrouxar tudo ao acaso.

**Nada é aplicado sem revisão.** A corrida registra que alguém aplicou — ela não aplica. A versão nova de
receita nasce no módulo de receita, por uma pessoa, e aqui fica só o ponteiro. Se o otimizador pudesse
escrever na receita, "revisado" viraria um campo que alguém marca em vez de um ato que alguém pratica.

### DEC-OPT-003 (OPT-001) — LIMITAÇÃO DECLARADA: uma substituição por vez

O solver enumera a troca de **um** ingrediente por vez. Não é descuido: trocar dois simultaneamente
multiplica o espaço de busca e, pior, produz alternativas cujo efeito ninguém consegue atribuir a uma das
trocas — o mesmo problema de confundimento que EXP-001 existe para impedir.

O método está nomeado no resultado (`EXHAUSTIVE_SINGLE_SUBSTITUTION`), então a limitação viaja com o
número em vez de ficar só aqui. Ampliar para combinações é acrescentar um `SolverMethod` novo, e as
corridas antigas continuam dizendo qual estava valendo.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
