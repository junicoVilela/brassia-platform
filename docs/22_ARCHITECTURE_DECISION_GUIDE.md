# Guia de decisão arquitetural

Arquiteturas não são selos de qualidade. Cada estrutura deve reduzir um risco real com um custo aceitável. Para este sistema, a decisão é um **monólito modular com hexagonal pragmática**.

| Abordagem | Usar quando | Evitar quando | Decisão no projeto |
|---|---|---|---|
| Camadas tradicionais | CRUD, validações simples e poucas integrações | regra cresce e começa a depender de controller/JPA | permitida em módulos de apoio |
| Monólito modular | uma equipe pequena precisa de limites fortes e operação simples | módulos não possuem fronteiras de negócio | padrão obrigatório |
| Hexagonal | domínio complexo, integrações variáveis, cálculos e máquinas de estado | CRUD sem lógica ou única implementação trivial | obrigatória nos módulos críticos |
| Clean Architecture | é preciso reforçar dependências para dentro e independência de framework | usada como várias camadas adicionais além da hexagonal | princípios adotados; não é uma segunda estrutura |
| DDD tático | existem linguagem, invariantes e agregados importantes | cadastro simples tratado como agregado complexo | seletivo, principalmente produção/receita/estoque |
| Eventos internos | desacoplar efeitos após uma mudança já confirmada | substituir chamada síncrona que precisa de resposta imediata | usar eventos de domínio internos |
| Outbox | publicar evento para sistema externo sem perder consistência | evento apenas dentro do mesmo processo | usar somente na fronteira externa |
| CQRS leve | dashboard/relatório precisa de consulta diferente da escrita | duplicar banco e pipeline para CRUD | read models no mesmo PostgreSQL |
| Event Sourcing | histórico de eventos é o próprio modelo e replay é requisito central | adotado apenas por “auditoria completa” | não usar; ledger imutável onde necessário |
| Microsserviços | escala, disponibilidade, segurança ou equipes exigem deploy independente | uma pessoa desenvolve e opera um produto ainda em formação | não usar antes de métricas e ADR |

## Critério para hexagonal completa

Aplicar quando pelo menos dois itens forem verdadeiros:

- regra de negócio possui invariantes ou estados relevantes;
- existem duas ou mais entradas, saídas ou implementações possíveis;
- framework/banco dificultaria teste do comportamento;
- integração externa pode mudar ou falhar;
- erro pode causar perda de lote, saldo, rastreabilidade ou segurança.

`recipe`, `production`, `inventory`, `sanitation`, `fermentation`, `packaging`, `quality`, `traceability`, `costing` e `ai` atendem esse critério. `brewery settings`, catálogos simples e preferências podem começar em camadas enxutas.

## Quando extrair um microsserviço

Somente com evidência de pelo menos um destes fatores: escala muito diferente, disponibilidade independente, fronteira regulatória/segurança, tecnologia inevitavelmente diferente, equipe autônoma ou deploy que bloqueia o restante. O ADR deve mostrar ownership de dados, API/eventos, observabilidade, idempotência, falhas distribuídas e custo operacional.
