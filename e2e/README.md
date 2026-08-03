# Testes E2E

Jornadas críticas de ponta a ponta: navegador real, frontend servido pelo
`ng serve` e **API real** com PostgreSQL atrás. Ferramenta: Playwright, conforme
`docs/12_TESTING_STRATEGY.md`.

## O que estes testes cobrem — e por que existem

Unitário e integração cobrem cada lado separadamente e não enxergam o que só
aparece quando as duas pontas se encontram: formato de resposta divergente do que
o frontend espera, sessão por cookie, CSRF, guards de rota e erro de render.

O primeiro defeito pego por este harness foi exatamente disso: `/catalog/ingredients`
e `/equipment` devolvem página (`{content, page, size, ...}`), mas as telas de
envase e gases os tratavam como array. A tela "carregava" e mostrava o estado
vazio — os selects de embalagem e de linha ficavam vazios e o formulário,
inutilizável. Nenhum teste de unidade viu, porque os mocks devolviam arrays.

Por isso o `test` daqui é estendido (`tests/support.ts`): **erro de JavaScript no
console reprova o teste**. Sem essa guarda, uma tela quebrada passa em qualquer
asserção de conteúdo.

## Rodar localmente

Três peças precisam estar de pé. Em terminais separados:

```bash
# 1. banco
docker compose up -d postgres

# 2. backend com o perfil local (cria admin e cervejaria de bootstrap)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. testes (sobem o `ng serve` sozinhos)
cd e2e && npm install && npx playwright install chromium && npm test
```

O `ng serve` é iniciado pelo próprio Playwright (`webServer` na config) e
reaproveitado se já estiver rodando.

### Variáveis

| Variável | Padrão | Para quê |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:4200` | Onde o frontend responde |
| `E2E_API_URL` | `http://localhost:8080` | Alvo do proxy `/api` — útil quando a 8080 está ocupada |
| `E2E_ADMIN_EMAIL` | `admin@brassia.local` | Admin de bootstrap do perfil `local` |
| `E2E_ADMIN_PASSWORD` | `admin-local-123` | Idem |

As credenciais são descartáveis e valem só em desenvolvimento — vêm de
`application-local.yml` e nunca devem existir fora dele.

## Escopo

Os testes **não criam dado de produção** e assumem banco recém-criado: as telas
são verificadas no estado vazio. Jornadas que criam lote, plano e envase pedem
massa de dados própria e ficam para quando houver seed dedicado — hoje seriam
frágeis e lentas sem acrescentar cobertura que os testes de integração já dão.

Um navegador (Chromium). Sem paralelismo: as jornadas compartilham o banco e o
usuário de bootstrap.

## Tema Fila na CI

O tema é asset pago e **não versionado** (`frontend/THEME_SETUP.md`), então na CI
os arquivos não existem e o `ng serve` devolve o `index.html` no lugar deles. É o
comportamento previsto — a aplicação funciona sem o visual do tema. A guarda de
erros do console ignora esse ruído (`ruidoEsperado` em `tests/support.ts`).

Consequência a saber: **o E2E da CI não valida aparência**, só comportamento.
Fidelidade visual continua sendo conferida à parte, com o tema instalado.
