# Status — Sprint 15

Estado: NÃO INICIADA

## Controle das histórias

| História | Estado | Responsável | Evidência/PR | Observação |
|---|---|---|---|---|
| INT-001 | Concluída | Claude | `backend/.../sensor`, `V98__sensor_ingestion.sql`, `frontend/.../features/sensors` | Módulo `sensor` novo. Idempotência pela restrição única do banco, qualidade e atraso sinalizados como eixos independentes. Ver DEC-INT-002/003 e DEB-INT-001. |
| INT-002 | A fazer | — | — | — |
| PWA-001 | A fazer | — | — | — |
| PWA-002 | A fazer | — | — | — |
| INT-003 | A fazer | — | — | — |
| INT-006 | A fazer | — | — | — |
| SEC-B07 | A fazer | — | — | — |
| ~~INT-004~~ | Movida | — | — | Sprint 21 — ver DEC-INT-001 |
| ~~INT-005~~ | Movida | — | — | Sprint 21 — ver DEC-INT-001 |
| ~~INT-007~~ | Movida | — | — | Sprint 21 — ver DEC-INT-001 |

## Decisões e bloqueios

Registre aqui somente decisões temporárias, bloqueios e dependências. Decisão arquitetural permanente deve virar ADR; débito técnico deve ter identificador e critério de remoção.

### DEC-INT-001 — Conectores de terceiro saem para a Sprint 21

INT-004 (Brewfather), INT-005 (Brewer's Friend) e INT-007 (central de sincronização) foram movidas para
`sprints/sprint-21-external-connectors/`.

O motivo é verificabilidade, não prioridade. Os critérios de aceite dessas histórias são sobre comportamento
de fronteira — paginação, rate limit, backoff, timeout e revogação — e o projeto não tem credencial de teste
dos provedores. Implementá-las agora significaria exercitar o nosso código contra o contrato que **supomos**
que o provedor tem, e é exatamente a suposição que falha em integração com terceiro. A história seria marcada
como concluída sem que o critério tivesse sido cumprido, que é a forma mais cara de dívida: a que não parece
dívida.

INT-007 acompanha porque existe para exibir execuções, cursores, falhas, rate limit e conflitos **desses**
conectores. Sem eles seria uma tela sem conteúdo.

**Fica na Sprint 15** o que não depende de terceiro: INT-001 e INT-006 (ingestão de sensor e adapters de
dispositivo — o dispositivo é do próprio usuário e um broker MQTT roda em Testcontainers), INT-002 (webhook
de saída, cujo destino é configurado pelo usuário e testável contra servidor local), INT-003 (QR, que é
inteiramente nosso), PWA-001/002 e SEC-B07 (federação contra provedor que já temos validado desde a
Sprint 01-D).

**Consequência para a Sprint 21:** ela herda a dependência de inbox/idempotency e outbox construídos aqui, e
não pode começar sem credencial de teste — registrado como `BLQ-INT-001` no `STATUS.md` dela.

### DEC-INT-002 (INT-001) — A idempotência é da restrição única, não de uma consulta prévia

O caminho ingênuo — "procura se já existe, senão insere" — tem uma janela entre a pergunta e a escrita, e é
dentro dela que cai o reenvio de um gateway que despachou a mesma mensagem duas vezes em milissegundos. Ou
seja: a janela existe exatamente no cenário para o qual a idempotência existe. Quem decide é
`uq_sensor_reading_message (device_id, message_id)` com `ON CONFLICT DO NOTHING`; o caso de uso só lê o
resultado. `SensorIngestionIT.reenvioConcorrenteGravaUmaSo` dispara **oito requisições simultâneas** com a
mesma identidade contra PostgreSQL real e afirma 1×201 + 7×200, uma linha gravada.

Três consequências deliberadas:

- **A chave vem do dispositivo, não do conteúdo.** Um hash do payload trataria duas medições legitimamente
  idênticas — sensor parado, mesmo segundo, mesmo valor — como repetição, e descartaria uma leitura
  verdadeira. A repetição a reconhecer é a de *transporte*, e só quem enviou sabe que é o mesmo envio.
- **Repetida responde 200, não erro.** O dispositivo que não recebeu o ACK e reenviou fez a coisa certa;
  erro o ensinaria a continuar tentando. E `duplicate` viaja até a tela, porque distingue "o gateway está
  reenviando demais" de "estou recebendo o dobro de leituras" — mesmo sintoma no gráfico, soluções opostas.
- **A resposta devolve a leitura gravada, não a recém-montada.** Elas diferem no id e no `receivedAt`, e
  responder a segunda apagaria o atraso real do primeiro envio.

### DEC-INT-003 (INT-001) — Qualidade e atraso são sinalizados, e são eixos independentes

Recusar uma leitura ruim não deixa "nada": deixa um buraco na curva, e um buraco é indistinguível de "o
sensor não mediu" e de "não aconteceu nada". Gravada e marcada, a leitura conta duas coisas verdadeiras — o
dispositivo reportou naquele instante, e não se deve acreditar no número.

Atraso e qualidade ficam em colunas separadas porque descrevem problemas diferentes com providências
diferentes: uma leitura pode ter valor perfeito e ter chegado três horas tarde (rede), outra pode chegar na
hora com o sensor fora d'água (sensor). Um "status" único obrigaria a perder uma das duas informações.

Duas escolhas dentro disso:

- **`FUTURE_CLOCK` tem precedência sobre `OUT_OF_RANGE`.** Um dispositivo que volta de reset com o relógio
  de fábrica manda valores perfeitos com instante impossível; dizer "fora da faixa" responderia a pergunta
  errada — o problema não é o número, é que a leitura não pode ser posicionada na série.
- **O atraso é medido contra a régua do dispositivo**, não contra um limiar fixo: 30 s não significam nada
  num sensor horário e significam uma janela perdida num de 15 s. Sem `expectedInterval` cadastrado o
  atraso é medido e informado mas **não julgado** — afirmar atraso sem régua seria inventar um limiar.

**O que é recusado**, e por quê: grandeza ou unidade divergentes do cadastro (400) e dispositivo pausado ou
revogado (409). Erro de configuração e erro de estado não descrevem medição nenhuma — guardá-los
sinalizados contaminaria a série com linhas que ninguém sabe ler. O caso concreto é o firmware atualizado
que passa a mandar Fahrenheit sem avisar: converter em silêncio trocaria a escala da série histórica
inteira.

### DEC-INT-004 (INT-001) — Leitura de sensor não é auditada; o dispositivo é

O AGENTS.md pede auditoria para comando crítico, e telemetria não é comando. Um dispositivo de 30 segundos
gera 2.880 linhas por dia; auditar cada uma encheria a trilha de ruído até que ninguém mais encontrasse nela
a alteração de custo ou a liberação de lote que ela existe para guardar. O que **é** auditado é o que muda a
confiança na série: cadastrar, pausar e revogar. Uma lacuna de seis horas numa curva é uma pergunta sem
resposta até alguém descobrir que o dispositivo estava pausado, e quem o pausou.

A leitura em si é o próprio registro — imutável, com os dois relógios e a qualidade gravados. O repositório
não expõe `UPDATE` nem `DELETE`.

### DEB-INT-001 (INT-001) — A leitura de sensor não alimenta a curva de fermentação

`fermentation` já tem `FermentationReading` com `ReadingSource.SENSOR`, mas não publica porta de comando no
pacote raiz — o mesmo obstáculo registrado em DEB-AIA-001/002 da Sprint 14. Ligar a ingestão ao lote exigiria
criar `fermentation.FermentationCommands`, o que é mudança na história daquele módulo, não nesta.

Consequência prática: o sensor guarda a série dele, e quem quer ver a curva de fermentação continua
registrando a leitura à mão. **Critério de remoção:** quando `fermentation` publicar porta de comando, a
ingestão passa a encaminhar a leitura `GOOD` para o lote ativo do equipamento vinculado, na mesma transação.

Registro relacionado: `Measure` (sensor) e `ReadingKind` (fermentation) são enums separadas de propósito —
uma descreve o que um *dispositivo* reporta (tem `FLOW`), a outra o que se mede *de um lote* (tem `PH`).
Compartilhá-las criaria dependência entre módulos para economizar quatro constantes e amarraria a evolução
de um à do outro.

## Evidências de encerramento

- Build/commit:
- Testes executados:
- Migration aplicada:
- Contratos atualizados:
- Riscos remanescentes:
- Aceite:
