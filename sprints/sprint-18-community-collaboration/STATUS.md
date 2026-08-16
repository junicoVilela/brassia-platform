# Status — Sprint 18

Estado: **ENCERRADA em 2026-08-16** — COM-001 a COM-005 entregues; aceite pendente.

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

### DEC-COM-007 (COM-005) — Avaliação e denúncia entram; a revisão fica registrada como dúvida

**A escolha do mantenedor.** O critério da história pedia "executar moderação auditada". Entregar isso
exigiria decidir **quem** modera, e essa pergunta não tem resposta no modelo de segurança de hoje: todo
principal tem uma cervejaria, o autor não pode julgar denúncia contra a própria receita, e usar o grupo
de sistema `ADMINISTRATORS` como moderador global significa dar a alguém o poder de esconder publicação
de qualquer casa. Isso é decisão de modelo de segurança, não detalhe de implementação — então a fatia
entrega **nota, denúncia registrada e o autor vendo do que foi acusado**, e a autoridade de revisão vira
`DUV-COM-001`.

**A média nunca viaja sem a contagem.** "5,0" de uma avaliação e "5,0" de duzentas são o mesmo número e
significam coisas opostas; `meaningful` é o que permite a tela mostrar o primeiro como opinião e o segundo
como reputação. E **sem votos a média é nula, e não zero**: zero é a pior nota possível, e uma receita
recém-publicada nasceria parecendo péssima.

**Uma nota por pessoa, garantida pela chave primária composta.** Acumular transformaria a média numa
contagem de quem insistiu mais — o jeito mais simples de manipular reputação sem robô nenhum. O `ON
CONFLICT ... DO UPDATE` é consequência da chave, e não conveniência. É o quinto caso da mesma família na
leva: **a invariante que atravessa linhas mora no banco**.

**Denunciar REGISTRA: não esconde nada.** Uma denúncia que tirasse o conteúdo do ar seria uma arma —
qualquer pessoa derrubaria a receita de um concorrente escrevendo três linhas. E a mesma pessoa não
denuncia a mesma publicação duas vezes pelo mesmo motivo (índice único): a contagem de denúncias é sinal,
e um sinal que a mesma pessoa repete deixa de medir a comunidade e passa a medir a insistência.

**O autor vê as denúncias contra si, e não quem denunciou.** Saber do que se é acusado é o mínimo antes de
qualquer revisão existir; a identidade do denunciante exposta ao denunciado transformaria a denúncia em
convite à retaliação.

**Ninguém avalia nem denuncia a si mesmo** (409 `self_rating`). A nota do autor não informa ninguém, e se
ele quer tirar a publicação do ar, o botão é despublicar.

**Entregue:** `V129`, `Rating`, `RatingSummary`, `AbuseReport` e exceções, porta, caso de uso, quatro
endpoints, 2 caminhos e 2 schemas no OpenAPI, e a avaliação na tela. **11 testes de domínio, 12 de
integração e 4 de store.**

### DUV-COM-001 (COM-005) — Quem revisa uma denúncia?

**A pergunta.** O agregado `AbuseReport` já sabe ser revisado — registra quem, quando e o desfecho, recusa
revisão dupla e não apaga a denúncia improcedente, tudo coberto por teste. O que não existe é **quem pode
chamar isso**: nenhum papel da plataforma está acima das cervejarias.

**Por que não foi inventado.** As três saídas plausíveis levam a modelos de segurança diferentes:

1. O grupo de sistema `ADMINISTRATORS` (sem cervejaria) vira moderador global — e ganha o poder de
   esconder publicação de qualquer casa.
2. Cada cervejaria modera o que publica — mas então o autor julga a denúncia contra si, que é justamente o
   que a moderação existe para evitar.
3. Moderação distribuída por reputação — história inteira, e não um endpoint.

**O que fica pronto para qualquer uma delas.** A tabela já tem `reviewed_at`, `reviewed_by`, `outcome` e
`outcome_note`, com `CHECK` garantindo que uma revisão pela metade não existe. Falta o endpoint e o papel.

## Evidências de encerramento

- **Build/commit:** cinco PRs, um por história, mergeados em série na `main` — #237 (COM-001), #238
  (COM-002), #239 (COM-003), #240 (COM-004), #241 (COM-005).
- **Testes executados:** `mvnw clean verify` verde na árvore final — **1.397 unitários e 904 de
  integração** contra PostgreSQL real via Testcontainers, zero falhas. Frontend: **550 testes em 88
  arquivos**, build e lint limpos. `ModularityTest` e `TenantIsolationTest` verdes. A sprint começou com
  855 testes de integração e terminou com 904.
- **Migration aplicada:** `V125` a `V129`. Nenhuma destrutiva. As de maior consequência não são as que
  criam tabela, e sim as garantias: a chave primária `(publication_id, user_id)` da avaliação, o índice
  único da denúncia e o `CHECK` de revisão tudo-ou-nada.
- **Contratos atualizados:** `contracts/openapi.yaml` — **301 caminhos**, 17 a mais que no encerramento da
  Sprint 19, sem `$ref` órfã nem chave duplicada.
- **Riscos remanescentes:**
  - **A premissa de produção**, a mesma da Sprint 19: enquanto REL-001 e o ciclo da REL-005 seguirem
    abertos, isto é software que funciona e não opera.
  - **`DUV-COM-001`** — não há quem revise uma denúncia. A denúncia é registrada, o autor a vê, o agregado
    sabe ser revisado e a tabela tem os campos; falta decidir **quem pode**, e essa decisão é de modelo de
    segurança. Enquanto isso, a moderação é registro e não ação.
  - **A remoção de conteúdo é ato manual do autor.** Julgar procedente não esconde nada por conta própria
    — encadear automático faria a moderação executar antes de alguém decidir o que fazer.
- **Aceite:** pendente de validação manual. Junto com os aceites das Sprints 09, 16, 17 e 19.

### O que esta sprint ensinou, e que vale carregar

**Allowlist, e nunca blacklist.** O retrato público lista campo a campo o que sai. A blacklist teria o
comportamento oposto no ponto que importa: campo novo na receita **vaza por padrão** até alguém lembrar de
proibi-lo. Num módulo cujo assunto é o que sai de casa, o padrão errado não é incômodo — é vazamento.

**Congelar é o que permite compartilhar sem entregar a chave.** O retrato publicado, a atribuição do fork
e o nome de quem comentou: os três são cópias, e não ponteiros. Se fossem ponteiros, o autor renomear ou
fechar a receita mudaria o que outra pessoa já leu ou já copiou — e a cópia de um estranho continuaria
lendo o conteúdo privado dele.

**A invariante que atravessa linhas mora no banco** — pela quinta vez, contando as três da Sprint 19. Aqui
foram o escopo da decisão sobre contribuição (subconsulta pela publicação, e **não** pela cervejaria da
linha, que é a de quem escreveu) e a nota única por pessoa (chave primária composta). E de novo: o código
continua checando **para dar mensagem boa, não para garantir**.

**Registrar uma dúvida é entrega, e não pendência escondida.** A COM-005 podia ter inventado um moderador
global em vinte linhas. Inventá-lo significaria decidir, sem o mantenedor, que alguém pode esconder
publicação de qualquer cervejaria. O que a fatia entrega funciona sozinho — nota, denúncia registrada,
direito de resposta — e o que falta está escrito com as três saídas possíveis e o custo de cada uma.

**A média nunca viaja sem a contagem.** Vale além da avaliação: é o mesmo problema da previsão de demanda
da FCST-001, que se recusa a responder com pouco histórico. Um número sem a medida da sua própria
confiança convida a decisão errada com cara de dado.

| História | Estado | Evidência |
|---|---|---|
| COM-001 | Entregue | PR #237 · `V125` · retrato allowlist, 15 de domínio e 10 de integração |
| COM-002 | Entregue | PR #238 · `V126` · link não eleva visibilidade, 12 de domínio e 10 de integração |
| COM-003 | Entregue | PR #239 · `V127` · fork independente, 7 de domínio e 9 de integração |
| COM-004 | Entregue | PR #240 · `V128` · aceitar registra concordância, 10 de domínio e 8 de integração |
| COM-005 | Entregue | PR #241 · `V129` · nota e denúncia, 11 de domínio e 12 de integração · `DUV-COM-001` |
