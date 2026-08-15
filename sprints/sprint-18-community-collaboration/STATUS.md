# Status — Sprint 18

Estado: **ATIVA desde 2026-08-15** — COM-001 e COM-002 entregues; COM-003 a COM-005 pendentes.

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
