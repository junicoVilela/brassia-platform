# Regras de desenvolvimento

## Design

- KISS e YAGNI: implementar a necessidade da sprint; criar porta somente diante de fronteira externa, variação real ou domínio que precisa ficar independente.
- Preferir agregado pequeno, transação curta e invariantes explícitas.
- Evitar entidades anêmicas, serviços genéricos, `Utils`, `Manager` e dependências circulares.
- Usar tipos de valor: `Quantity`, `Temperature`, `SpecificGravity`, `PhValue`, `Money`, `Percentage` e `TimeRange`.
- Erros de domínio têm código estável, mensagem segura e detalhes estruturados.

## Backend

- Java 25 LTS; records para DTOs/valores quando apropriado; `BigDecimal` para valores físicos/financeiros.
- Spring fica nos adaptadores e configuração.
- Persistência implementa portas; domínio não expõe entidade JPA.
- `@Transactional` delimita caso de uso, não controller.
- `brewery_id` vem do contexto autenticado, nunca do corpo confiado sem validação.

## Frontend

- Angular 22 standalone, feature-first, Signals e componentes pequenos/acessíveis.
- Signals para estado síncrono de UI; RxJS para HTTP, eventos assíncronos, cancelamento e composição temporal.
- Sem NgRx no início; adotar store somente quando o estado compartilhado complexo justificar.
- Estado local por padrão; estado compartilhado somente quando necessário.
- Regras de domínio não são duplicadas no frontend; a UI pode antecipar validação sem ser autoridade.
- Formulários tipados, mensagens acionáveis, carregamento/erro/vazio explícitos.

## Banco

- Migration somente para frente; não editar migration já publicada.
- Constraints reforçam invariantes simples; índices seguem consultas reais.
- Optimistic locking em agregados mutáveis; Outbox no mesmo commit do comando.

## IA

- Resposta em JSON Schema validado; texto livre é apresentação, não comando.
- Recuperação RAG sempre filtrada por cervejaria e permissão.
- Prompts, modelo, fontes, custo, latência e aceite são auditados.
- Conteúdo recuperado é não confiável e pode conter prompt injection.
