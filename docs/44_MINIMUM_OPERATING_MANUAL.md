# 44 — Manual mínimo de operação

Entregável da história `REL-005`. Cobre o caminho que uma cervejaria percorre do insumo recebido à cerveja
expedida, na ordem em que o sistema aceita — e as portas que ele fecha quando a ordem não é essa.

> **O que este manual não é.** Não é referência de tela nem catálogo de campos: cada tela tem rótulo,
> validação e mensagem próprios, e duplicá-los aqui criaria uma segunda fonte que envelhece na primeira
> mudança de layout. O que está aqui é a **sequência** e o **porquê de cada porta** — que é o que não se
> descobre olhando a tela.

O ciclo descrito abaixo é o mesmo exercitado de ponta a ponta por `e2e/tests/business-journey.spec.ts`.
Quando manual e teste divergirem, o teste está certo: ele roda contra a API real a cada mudança.

---

## 1. Antes do primeiro lote

Esta ordem não é sugestão de organização — cada item é pré-requisito do seguinte, e pular gera recusa lá na
frente, longe da causa.

| # | Onde | O que | Por que antes |
|---|---|---|---|
| 1 | `/breweries` — **Cervejarias** | A cervejaria e seu fuso | Todo dado de negócio pendura em `brewery_id`, e o fuso decide a que dia pertence um apontamento das 23 h |
| 2 | `/security/users` — **Usuários** | Pessoas e grupos de permissão | Comando sem permissão é recusado; conceder no meio da operação obriga a refazer o passo |
| 3 | `/settings/parameters` — **Parâmetros** | Parâmetros da casa | Validade da liberação de limpeza, tolerâncias e alçadas saem daqui. O default existe, mas é o da casa que decide se um CIP de ontem ainda vale hoje |
| 4 | `/equipment` — **Equipamentos** | Panela, fermentadores, linha de envase | Receita, transferência e envase apontam para equipamento; sem ele não há onde a cerveja estar |
| 5 | `/catalog` — **Ingredientes** | Malte, lúpulo, levedura, embalagem | Com os atributos técnicos: sem potencial, cor, alfa-ácido e atenuação, a receita não calcula métrica nenhuma |
| 6 | `/suppliers` — **Fornecedores** | Quem fornece | O recebimento exige fornecedor: é o primeiro elo da rastreabilidade, e sem ele o recall não sai da fábrica |
| 7 | `/sanitation/procedures` — **POPs de limpeza** | O POP **publicado** | Ciclo de limpeza só executa procedimento publicado — rascunho não libera equipamento |

---

## 2. O ciclo, do insumo à expedição

### 2.1 Receber insumo — `/inventory` (**Estoque**)

Entra com fornecedor, lote do fornecedor, validade, custo unitário e resultado da inspeção. **É daqui que a
rastreabilidade parte:** o lote do fornecedor é o que um recall percorre de volta. Recebimento sem esse
código produz cerveja cuja origem termina na sua porta.

### 2.2 Publicar a receita — `/recipes` (**Receitas**)

Calcule as métricas e **publique**. Receita publicada é imutável: alteração gera versão nova, e é por isso
que o lote consegue dizer meses depois com qual receita foi feito. Ordem de produção só aceita receita
publicada.

### 2.3 Ordem de produção — `/brew-orders` (**Ordens de produção**)

Quatro passos, nesta ordem, e cada um recusa se o anterior não aconteceu:

1. **Criar** — receita publicada e volume.
2. **Liberar** — com **responsável nomeado**. "A equipe" não é responsável.
3. **Reservar estoque** — separa o insumo. Reservar antes de iniciar é o que impede dois lotes de contarem
   com o mesmo saco de malte.
4. **Iniciar** — **é aqui que o lote nasce**. Antes disso existe intenção; depois existe cerveja.

### 2.4 Acompanhar o lote — `/production/batches` (**Lotes de produção**)

Apontamentos de temperatura, densidade, pH e volume. A **transferência** para o fermentador registra volume
transferido, OG e perdas — e as perdas são declaradas, não deduzidas: volume que some sem alguém dizer por
onde vira cerveja sem origem.

Fermentação tem tela própria (`/fermentation/readings`, **Leituras de fermentação**); leitura de sensor, se
houver sensor cadastrado, entra pela mesma curva sem digitação.

### 2.5 Liberar a linha — `/sanitation/cycles` (**Ciclos de limpeza**)

**A linha de envase não recebe plano sem ciclo de limpeza liberado.** O ciclo é: abrir sobre o POP
publicado → registrar as medições de cada passo (concentração, temperatura, tempo) → concluir → registrar a
verificação (enxágue, visual, ATP, microbiológico) → liberar.

A liberação é **evidência, não um "ok" digitado**: cada passo carrega o que foi medido, e a validade vem do
parâmetro da casa. CIP feito semana passada não libera envase de hoje.

### 2.6 Plano de envase — `/packaging/plans` (**Planos de envase**)

Plano sobre o lote, com embalagem, linha limpa e janela. Depois: **checklist** (inspeção do vasilhame, teste
de recravação, suprimento de gás) → **reserva** → **execução** com volume de entrada, unidades produzidas e
unidades rejeitadas.

A execução **cria o lote de produto acabado** — é o objeto que sai da fábrica, e é ele que o recall procura.

### 2.7 Produto acabado e expedição — `/packaging/finished-lots` (**Produto acabado**)

A tela mostra quantas unidades ainda estão **sem destino**. Registre a expedição com destino, contato e
quantidade. O que não foi expedido está na fábrica e não é objeto de recall — a distinção é o que faz o
exercício de recall medir alguma coisa.

### 2.8 Provar a cadeia — `/traceability/genealogy` (**Genealogia**)

Do insumo à expedição, nos dois sentidos. É a tela que responde "de onde veio" e "para onde foi" sem que
ninguém precise montar planilha.

### 2.9 Exercitar o recall — `/traceability/recall-drills` (**Simulados de recall**)

Abra o simulado sobre o lote, registre quantas unidades foram localizadas, o resumo e as ações corretivas.
O relatório devolve **percentual localizado** e o que fazer para melhorar.

**Simulado não recolhe nada** e não abre recall de verdade. É exercício — e é o único jeito de descobrir que
a rastreabilidade tem buraco antes do dia em que ela precisa funcionar.

### 2.10 Fechar a conta — **Custo do lote**, **Planejado × real**, **Relatório do lote**

`/costing/batches`, `/costing/variance` e `/reporting/batches`. O custo só fecha depois do envase, porque é
o envase que diz quantos litros viraram produto.

---

## 3. Quando o sistema recusa

A maioria das recusas **não é erro**: é uma regra que existe para impedir um estrago silencioso. As mais
frequentes no primeiro ciclo:

| A recusa | O que ela está dizendo | O caminho |
|---|---|---|
| Ordem não aceita a receita | A receita não está publicada | Publique — e note que publicar congela a versão |
| Não dá para iniciar a ordem | Falta liberar ou reservar estoque | Volte um passo; a ordem é a garantia de que o insumo existe |
| Plano de envase recusa a linha | A linha não tem limpeza liberada, ou a liberação venceu | Rode o ciclo de limpeza; a validade é parâmetro da casa |
| Execução de envase recusada | Falta item do checklist ou a reserva | Checklist inteiro antes da reserva |
| 403 em uma tela que existe | Permissão, não bug | `/security/users`; a permissão é do **tipo** de operação, não genérica |
| Conflito ao salvar | Alguém alterou o mesmo registro antes | Recarregue e refaça: a versão otimista está protegendo a edição da outra pessoa |

Todo erro traz `traceId`. Ele é o que localiza a operação no log — leve-o junto ao relatar problema.

---

## 4. Roteiro de homologação

Executar o ciclo inteiro em homologação, com evidência de cada etapa. O roteiro é o mesmo da seção 2; o que
muda é que **cada linha exige evidência anexada** — sem ela, "funcionou" é memória, não registro.

- [ ] **Preparo** — cervejaria, usuários e permissões, parâmetros da casa, equipamentos, ingredientes com
      atributos técnicos, fornecedores, POP de limpeza publicado
- [ ] **Recebimento** — insumo com lote do fornecedor, validade e inspeção
- [ ] **Receita publicada** — com métricas calculadas
- [ ] **Ordem** — criada, liberada com responsável, estoque reservado, iniciada; **lote gerado**
- [ ] **Produção** — apontamentos e transferência com OG e perdas
- [ ] **Limpeza** — ciclo executado com medições e liberado
- [ ] **Envase** — plano, checklist completo, reserva, execução; **lote de produto acabado gerado**
- [ ] **Expedição** — com destino e quantidade; unidades sem destino conferem
- [ ] **Genealogia** — cadeia visível do insumo à expedição
- [ ] **Simulado de recall** — percentual localizado e ações corretivas registrados
- [ ] **Custo e relatório do lote** — fecham com o que foi produzido
- [ ] **Autorização** — uma operação tentada sem permissão retorna 403 com Problem Details
- [ ] **Isolamento** — um usuário de outra cervejaria não enxerga este lote
- [ ] **Bloqueadores** — nenhum aberto; o que sobrou tem identificador e critério de remoção

Evidência mínima por linha: identificador do objeto criado (lote, plano, simulado) e captura da tela que o
mostra. Para 403 e isolamento, o corpo da resposta — é ele que prova qual regra recusou.

---

## 5. O que este manual não cobre, deliberadamente

- **Operação da infraestrutura** — deploy, migration e retorno estão em `infra/runbooks/deploy-rollback.md`.
- **Restauração de backup** — não há ensaio no repositório (ver `DEC-REL-008` na Sprint 17). Enquanto não
  houver, **não há RPO nem RTO de dados medidos** — e isso é informação operacional, não detalhe.
- **Módulos que não estão no caminho do primeiro lote** — água, sensorial, metrologia, gases, IA, sensores,
  webhooks e os módulos de inteligência têm tela própria e não são pré-requisito do ciclo. Entram quando a
  operação básica estiver de pé.
