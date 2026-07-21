# Quando usar cada padrão

| Padrão | Vale a pena quando | Não usar quando |
|---|---|---|
| Value Object | unidade, precisão ou validação importam | simples texto sem comportamento |
| Aggregate | invariantes precisam ser atômicas | conjunto enorme de entidades carregadas juntas |
| Domain Service | regra cruza objetos e não pertence naturalmente a um deles | apenas delega repository |
| Application Service/Handler | coordena transação, autorização e portas | contém fórmula cervejeira central |
| Repository port | domínio complexo precisa ignorar JPA ou há mais de uma persistência | CRUD simples sem pressão do framework |
| Strategy | algoritmo muda por estilo/equipamento/provedor | existe uma única regra estável |
| Specification | filtros/regras combináveis e reutilizados | substituir `if` legível isolado |
| Factory | criação exige vários passos e invariantes | construtor/record já é claro |
| Domain Event | outros módulos reagem depois do commit lógico | etapa seguinte precisa responder sincronicamente |
| Outbox | evento sai do processo e não pode ser perdido | comunicação interna no mesmo commit |
| Idempotency key | comando pode ser repetido por rede/offline | consulta sem efeito |
| Optimistic lock | poucos conflitos e edição concorrente possível | contador de altíssima contenção |
| Read model | relatório cruza módulos ou exige formato próprio | CRUD pequeno atendido pelo agregado |
| Feature flag | deploy separado da ativação e rollback funcional | esconder código abandonado |

Toda nova abstração deve responder: qual variação, risco, teste ou dependência ela isola? Se a resposta for apenas “pode ser útil no futuro”, não criar.
