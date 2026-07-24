# Uso de padrões cervejeiros e licenciamento

Este documento é um gate de produto, não aconselhamento jurídico.

## BJCP

Em 2026, a edição corrente para cerveja continua sendo BJCP 2021; hidromel usa 2015 e sidra usa 2025.

O BJCP informa que:

- nome, numeração, parâmetros e impressão geral podem ser usados;
- o conteúdo integral não pode ser reproduzido automaticamente para qualquer finalidade;
- um aplicativo de gestão deve indicar a permissão, o copyright e o site oficial;
- deve utilizar a edição vigente;
- deve contatar o Communications Director, explicar o uso e compartilhar telas;
- aplicativos que disponibilizam as diretrizes devem cumprir as condições indicadas pelo BJCP.

### Política BrassIA

Antes da permissão formal:

- carregar somente dados permitidos: código, nome, faixas e impressão geral;
- mostrar link para a fonte oficial;
- não embutir texto integral de aroma, sabor, aparência, sensação, história e exemplos;
- marcar o dataset como `LIMITED_PERMISSION`.

Depois da permissão:

- registrar evidência e escopo;
- exibir copyright e atribuição dentro da aplicação;
- atualizar para a versão vigente;
- conservar a versão usada por receitas históricas.

## Brewers Association

As diretrizes são atualizadas anualmente. A edição corrente analisada é 2026 e inclui Rice Lager.

O uso exige atribuição. Quando o conteúdo é oferecido por produto pago ou associado a conteúdo gerador de receita, a entidade solicita pedido de permissão. O texto republicado não deve ser alterado sem a indicação apropriada.

### Política BrassIA

- tratar cada ano como dataset independente;
- executar job anual de comparação;
- não publicar texto completo antes de confirmar permissão compatível com o modelo comercial;
- manter a frase de atribuição exigida;
- permitir desativar o dataset sem quebrar receitas históricas.

## BeerJSON

O repositório oficial declara licença MIT. A BrassIA deve:

- fixar a versão 1.0;
- manter aviso de copyright/licença quando redistribuir schema ou pacote;
- validar contra o schema oficial;
- guardar extensões BrassIA em namespace próprio;
- não afirmar que BeerJSON fornece ingredientes ou estilos prontos.

## BeerXML

Usar como formato aberto de compatibilidade. A importação deve reportar limitações, principalmente processos modernos, número de etapas e campos não padronizados.

## Dados de fabricantes

- especificação técnica é vinculada ao fabricante, produto e data;
- texto de marketing não é copiado;
- imagens e logotipos exigem autorização;
- PDF/COA original pode ser armazenado como evidência privada do cliente;
- republicação global exige licença ou acordo;
- contribuição de usuário deve informar a origem.

## Gate de publicação

Nenhum dataset global é publicado sem:

- proprietário identificado;
- URL e data de acesso;
- versão;
- licença ou permissão registrada;
- atribuição definida;
- revisão técnica;
- checksum;
- responsável pela próxima revisão.

## Fontes

- https://www.bjcp.org/faq/i-want-to-use-your-style-guidelines-can-i/
- https://www.bjcp.org/bjcp-style-guidelines/
- https://www.brewersassociation.org/edu/brewers-association-beer-style-guidelines/
- https://www.brewersassociation.org/educational-publications/beer-styles/
- https://github.com/beerjson/beerjson

