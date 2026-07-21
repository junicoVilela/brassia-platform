# Identidade do produto — BrassIA

## Marca

- Nome oficial: **BrassIA**
- Pronúncia sugerida: “Brass-IA”
- Descrição curta: **Plataforma inteligente de gestão cervejeira**
- Slogan: **Inteligência em cada lote**
- Marca mantenedora: **Brew**

A grafia `BrassIA` deve ser preservada em títulos, interface, documentação comercial e mensagens destinadas ao usuário. Ela combina brassagem com inteligência artificial, sem limitar o produto ao módulo de IA.

## Identificadores técnicos

| Elemento | Identificador canônico |
|---|---|
| Repositório/monorepo | `brassia-platform` |
| Backend | `brassia-api` |
| Frontend | `brassia-web` |
| Maven groupId | `br.com.brew` |
| Maven artifactId | `brassia-api` |
| Pacote Java raiz | `br.com.brew.brassia` |
| Classe principal | `BrassiaApplication` |
| Angular project | `brassia-web` |
| Banco PostgreSQL | `brassia` |
| Schema PostgreSQL | `brassia` |
| Usuário local do banco | `brassia_app` |
| Docker Compose project | `brassia` |
| Prefixo de métricas | `brassia` |

## Regras de nomenclatura

1. Usar `BrassIA` somente para o nome de produto apresentado a pessoas.
2. Usar `brassia` em banco, schema, métricas, labels e identificadores sem separador.
3. Usar `brassia-*` em repositórios, imagens, artefatos implantáveis e aplicações web.
4. Usar `Brassia*` em classes Java; siglas internas seguem o padrão Java do projeto.
5. Não renomear termos do domínio cervejeiro para encaixar a marca.
6. Não usar `Brew Management`, `brew-management`, `brew_management` ou `br.com.brew.management` em novos arquivos.

## Validação do namespace

O namespace Java por domínio reverso pressupõe que a organização controla o domínio correspondente. Antes de publicar bibliotecas ou integrar sistemas externos, confirmar o controle de `brew.com.br`; se ele não for controlado pelo projeto, substituir `br.com.brew` por um domínio reverso realmente pertencente à organização, preservando o sufixo `.brassia`.

## Nota de proteção da marca

A escolha técnica não substitui a busca formal de anterioridade e o pedido de registro. Antes de lançamento público ou investimento comercial, validar marcas semelhantes nas classes aplicáveis do INPI, além de domínio e identificadores nas lojas e redes escolhidas.
