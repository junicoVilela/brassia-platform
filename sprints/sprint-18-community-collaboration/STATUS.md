# Status — Sprint 18

Estado: **ATIVA desde 2026-08-15** — COM-001 a COM-004 entregues; COM-005 pendente.

**A ressalva que esta sprint carrega, e que a Sprint 19 não carregava:** ela é a que **aponta para
fora**. Biblioteca pública, link compartilhado, fork e moderação colocam dado da cervejaria fora dela,
sobre um release que ninguém validou (REL-001 e o ciclo da REL-005 seguem abertos). Foi o motivo de a 19
ter vindo antes; a decisão de fazer a 18 agora é do mantenedor, e a premissa continua a mesma:
desenvolver não exige produção, **publicar exige** — e aqui "publicar" tem dois sentidos ao mesmo tempo.

## Decisões e bloqueios

### DEC-COM-001 (COM-001) — Allowlist, e nunca blacklist

**A decisão inteira da história.** O retrato público é uma estrutura própria, construída campo a campo. A
alternativa óbvia seria serializar a `Recipe` removendo o que não pode sair — e isso é blacklist, que
falha do lado errado: o dia em que alguém acrescentar um campo à receita (um custo estimado, um
fornecedor preferencial, uma nota interna), ele **vaza por padrão**, e ninguém percebe até estar
publicado.

O que não existe no `PublicRecipeSnapshot`, por construção:

| Omitido | Por quê |
|---|---|
| `breweryId` | é o inquilino — o plano de testes exige que busca e feed não o exponham |
| `ingredientId` | aponta para o catálogo, onde moram **preço de compra e fornecedor** |
| `equipmentId` | identifica o equipamento da casa; sai o volume da brassa, que é o que permite escalar |
| `previousRecipeId` | linhagem interna; a pública é a do fork (COM-003), e é outra coisa |

**O teste usa reflexão de propósito.** Um teste que olhasse só valores passaria no dia em que alguém
acrescentasse um campo novo com id dentro; este falha. E há a verificação no corpo HTTP, que é onde a
fronteira realmente importa.

**Publica-se uma versão, e não uma receita.** O retrato é **congelado**: uma vista faria a edição privada
de amanhã alterar em silêncio o que o público já leu, e o autor descobriria ter publicado algo que nunca
revisou. O `UPDATE` do repositório não toca no retrato.

**404 para inacessível, e nunca 403.** Numa biblioteca isso vale mais que nos outros módulos: distinguir
"não existe" de "é privada" permite **enumerar o acervo alheio sem ler nada** — basta contar quais
identificadores respondem diferente.

**Licença é lista fechada**, ao contrário de canal de venda e atividade de mão de obra, que são cadastro.
A diferença é efeito jurídico: quem escrevesse "livre" estaria dizendo nada, e quem copiasse acreditando
naquilo ficaria exposto. O padrão é **todos os direitos reservados** — assumir permissão por omissão daria
ao público um direito que o autor nunca concedeu.

**Despublicar não apaga, e relicenciar não retroage.** O que já foi lido não se desfaz, e um fork feito
enquanto estava pública continua legítimo. Fingir o contrário seria a plataforma prometendo um controle
que ela não tem sobre o que já saiu.

**O nome do ingrediente passou a ser publicado pelo catálogo.** `IngredientSpecLookup.Spec` ganhou `name`:
uma receita publicada sem os nomes é inútil, e o identificador não pode sair. Mudança pequena, na direção
padrão do ADR-0016 — quem tem o dado declara.

**Entregue:** `V125`, `PublicRecipeSnapshot`, `PublishedRecipe`, `Visibility`, `RecipeLicense`, portas,
caso de uso, controller com Problem Details, 6 caminhos e 7 schemas no OpenAPI, e a tela. **15 testes de
domínio e 10 de integração**, incluindo a matriz de visibilidade inteira exercida de fora.

### DEC-COM-002 (COM-002) — O link abre o que já era alcançável, e nada além

**O critério da história é literal — "acesso nunca ignora autorização ou visibilidade" — e ele virou
comportamento**, não comentário. `ShareLink.grantsAccessTo` exige **duas** condições: o link valer *e* a
publicação estar alcançável de fora.

Disso saem duas propriedades que valem mais que a soma delas:

- **Fechar a publicação derruba todos os links de uma vez**, sem revogar um por um. É o botão de pânico
  do autor — a decisão de quem vê é da publicação, e o link só carrega a chave.
- **Despublicar também derruba.**

**Só o hash é guardado**, como no token de conta e no segredo do webhook. O valor legível aparece uma vez,
na criação, e nunca mais — um link vazado do banco seria acesso concedido sem que ninguém tivesse
compartilhado nada. O teste de integração consulta o banco **pelo valor legível e espera zero linhas**: é
prova, e não promessa. No cliente, o token vive só em memória e some ao fechar a tela ou ao abrir outra
publicação.

**SHA-256 sem sal, e é decisão.** Sal atrapalha dicionário contra segredo escolhido por gente; este token
são 256 bits de aleatório. O que se ganha sem sal é poder **buscar pelo hash** — com sal, validar um link
exigiria ler todos e comparar um a um.

**Revogar e expirar são coisas diferentes, e as duas existem.** Expirar é o prazo combinado; revogar é o
arrependimento. Revogar duas vezes não é erro e **não muda a data** — quem clica de novo quer o mesmo
resultado, e a data é o registro de quando o acesso foi cortado. Não há "desrevogar": o motivo de revogar
costuma ser que o link chegou a quem não devia.

**404 para inexistente, expirado, revogado ou publicação fechada — sem distinguir.** Dizer "expirado" a
quem tem um token inventado confirma que aquele token um dia existiu; dizer "revogado" conta que houve um
compartilhamento e que alguém se arrependeu. O autor, que é quem precisa do motivo, vê o estado de cada
link na própria lista.

**Nenhuma permissão de link edita a receita.** Comentar é escrever *sobre* a receita, e não *na* receita —
a diferença é o que mantém a autoria intacta.

**Entregue:** `V126`, `ShareLink`, `SharePermission`, `ShareTokens`, porta, caso de uso, controller, 4
caminhos e 2 schemas no OpenAPI, e a tela com o token mostrado uma vez. **12 testes de domínio e 10 de
integração.**

### DEC-COM-003 (COM-002) — A COM-001 tratava `LINK` como `UNLISTED`, e isso foi corrigido

**Achado ao escrever a COM-002, e é correção de fronteira — não acréscimo.** Na COM-001, a visibilidade
`LINK` era legível por **qualquer usuário autenticado que soubesse o identificador**. Isso é a semântica
de `UNLISTED` ("abre por endereço direto, sem segredo"), e não a de `LINK` ("quem tem o link").

A partir da COM-002, `LINK` **exige o token** e entra por `/community/shared`; `UNLISTED` continua
abrindo pelo endereço da publicação. A mudança está no `findForReader`, com o motivo escrito no próprio
SQL, na migration e no contrato — para o próximo a ler não achar que sempre foi assim.

O teste da matriz de visibilidade foi corrigido junto: `LINK` passou da lista dos que abrem para a lista
dos que não abrem por endereço.

### DEC-COM-004 (COM-003) — A linhagem é atribuição congelada, e não ponteiro

**O critério é literal — "sem acesso futuro ao conteúdo privado do autor" — e ele decidiu a modelagem.**
Nome do autor, título, licença e versão são gravados **como estavam** no momento do fork. Se o autor
renomear a publicação, fechar a visibilidade ou despublicar, a atribuição continua correta e o forkador
**não ganha nada novo**. Provado de ponta a ponta: fechar a publicação depois do fork não quebra a
receita nem apaga o crédito.

O identificador da publicação fica guardado para a tela oferecer o link de volta — e **não para dar
acesso**: abrir aquela publicação continua passando pela matriz de visibilidade.

**O fork é recusado inteiro quando falta ingrediente.** O retrato público traz os ingredientes pelo
**nome** — o id é do catálogo do autor e nunca sai. A alternativa seria criar a receita só com o que
casou; uma receita a que faltam três de oito ingredientes **não é incompleta, é errada**, e alguém a
brassaria achando que é a do outro. A recusa vem com a lista, que é o que a torna acionável.

**A comparação de nome é normalizada** (sem maiúsculas nem espaços nas pontas): exigir igualdade exata
faria o forkador criar ingredientes duplicados para casar com um espaço.

**Sem nome informado, a cópia ganha o sufixo "(cópia)".** Descoberto porque o teste falhou com 409: nome
de receita é único por cervejaria. Mas a colisão é o sintoma — o motivo de fundo é que **duas receitas
com o mesmo nome no mesmo catálogo fazem o cervejeiro pegar a errada no dia da brassa**.

**CC BY-SA se propaga**, e a resposta do fork diz isso: descobrir a obrigação só na hora de publicar
seria descobrir tarde. As demais licenças deixam o forkador escolher a dele, desde que a atribuição fique.

**Dois módulos passaram a publicar coisas novas, ambos na direção padrão do ADR-0016:**

- `recipe.RecipeImportCommands` — porta de escrita, que **delega ao `CreateRecipeUseCase`** em vez de
  reimplementar. Um caminho paralelo seria um segundo lugar onde volume contra capacidade e percentuais
  de mostura precisariam ser mantidos iguais, e divergiriam na primeira mudança.
- `catalog.IngredientDirectory` — nome para identificador, porque o retrato público carrega nomes.

**Entregue:** `V127`, `ForkOrigin` e exceções, porta, caso de uso, dois endpoints, 2 caminhos e 1 schema
no OpenAPI, e a tela. **7 testes de domínio e 9 de integração.**

### DEC-COM-005 (COM-004) — Aceitar registra concordância, e não altera nada

**A decisão central da história é uma recusa**, e ela existe para manter de pé as duas anteriores:

1. **O retrato publicado é congelado** (COM-001). Aplicar uma sugestão nele faria mudar o que o público
   já leu — exatamente o que congelar existe para impedir.
2. **A receita de verdade é privada.** Deixar que texto de alguém de fora a reescreva daria a estranhos
   uma chave que nem o link de colaboração dá.

Então aceitar é **registrar concordância**: fica escrito que o autor achou boa, com nome e data. Aplicar é
ato dele, na receita dele, e vira versão nova — que ele publica quando quiser. Há teste de integração que
captura o retrato **antes** e **depois** de aceitar e compara: ele não muda uma vírgula.

**A tela repete a decisão onde o usuário a lê:** o botão diz "Concordo", não "Aplicar", e o aviso embaixo
diz que concordar não altera a receita. Escrever "aplicar" prometeria uma mudança que não acontece, e o
autor descobriria a mentira na próxima brassa.

**Comentário não se aceita nem se recusa** — ele não propôs nada. Sem essa regra, a tela ofereceria dois
botões sem sentido e a contagem de "pendentes" incluiria elogios. O `CHECK` da migration guarda isso.

**Recusar não apaga**, e **não se decide duas vezes**: decidir de novo reescreveria quem decidiu e quando,
e é esse registro que faz da conversa um histórico em vez de uma caixa de entrada.

**O texto não vai para a auditoria.** Ele já está na própria linha, e duplicá-lo num rastro que sobrevive à
moderação recriaria o conteúdo que alguém mandou esconder.

**Entregue:** `V128`, `Contribution` e exceções, porta, caso de uso, quatro endpoints, 4 caminhos e 1
schema no OpenAPI, e a conversa na tela. **10 testes de domínio e 8 de integração.**

### DEC-COM-006 (COM-004) — O `TenantIsolationTest` pegou uma escrita sem escopo, e a correção não era óbvia

**O achado.** O `update` de contribuições escrevia com `WHERE id = :id` apenas. A garantia de isolamento
morava no handler — o padrão que a `OBS-REL-001` encontrou em dez escritas e que aquele teste existe para
substituir por barreira.

**A correção quase saiu errada.** O `brewery_id` da linha é de **quem escreveu**; quem decide é o **dono
da publicação**, de outra casa. Filtrar pela cervejaria da linha recusaria toda decisão legítima. A regra
real é "só mexe quem responde por esta publicação", e ela agora está no SQL:

```sql
WHERE id = :id
  AND publication_id IN (SELECT id FROM community_published_recipe WHERE brewery_id = :brewery)
```

É o quarto caso da mesma família nesta leva — depois da sobreposição de preço, da liberação única do lote
e da reserva de estoque: **a invariante que atravessa linhas mora no banco**, e o código continua checando
para dar mensagem boa, não para garantir.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:

| História | Estado | Evidência |
|---|---|---|
| COM-001 | A fazer | — |
| COM-002 | A fazer | — |
| COM-003 | A fazer | — |
| COM-004 | A fazer | — |
| COM-005 | A fazer | — |
