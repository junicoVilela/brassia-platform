# Status — Sprint 18

Estado: **ATIVA desde 2026-08-15** — COM-001 entregue; COM-002 a COM-005 pendentes.

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
