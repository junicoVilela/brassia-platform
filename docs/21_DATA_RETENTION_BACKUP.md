# Retenção, backup e recuperação

Classificar dados em operacional, auditoria/rastreabilidade, documentos, telemetria técnica, conteúdo de IA e dados pessoais. Cada classe define finalidade, acesso, retenção, exportação e descarte seguro conforme obrigação aplicável.

Banco e objetos precisam de backups coordenados, criptografados, com retenção e cópia fora do ambiente principal. Backup sem teste de restauração não é controle válido. O runbook deve registrar RPO/RTO, dependências, ordem de recuperação, verificação de integridade e responsável pela decisão.

**O teste de restauração é o `infra/runbooks/restore-drill.md`**, e a execução mais recente fica registrada no fim dele.

Exclusões legais não podem quebrar genealogia, estoque ou auditoria: quando necessário, anonimizar dados pessoais e preservar a evidência operacional mínima.

---

# A política

Decidida pelo mantenedor em 2026-08-26. Até esta data não havia política, e por isso não havia RPO —
nenhum ensaio produz uma janela de perda a partir de uma política ausente.

## RPO: 5 minutos

Arquivamento contínuo de WAL com recuperação a ponto no tempo (PITR).

**Por que 5 minutos e não 24 horas.** Apontamento de brassa, pedido e entrega se refazem com dor: alguém
tem no papel, no celular ou na cabeça. **A trilha de auditoria e a genealogia não se refazem** — elas são
a evidência, não o registro do trabalho, e são exatamente o que um recall precisa para dizer que este keg
carregou aquele lote. Uma casa que mantém três barreiras de build guardando a rastreabilidade não pode ter
um backup que a perde por atacado.

**Por que não zero.** Réplica em streaming custa outra máquina sempre ligada e compra segundos onde o WAL
compra minutos. Isso só se paga quando a **indisponibilidade** é o problema — e não é: o ensaio de
2026-08-19 mediu a volta em 56 segundos.

**O que o PITR resolve além do desastre, e talvez importe mais.** O incidente comum não é disco queimado:
é alguém rodando o comando errado às 15h40. Com dump diário, volta-se para a meia-noite e perde-se o dia.
Com PITR, volta-se para 15h39.

**Atenuante que entra na conta:** a fila offline da entrega vive **no aparelho** até sincronizar
(`PWA-002`). Perder o servidor não perde o que o entregador registrou e ainda não mandou.

## RTO: 4 horas

Folgado de propósito. A restauração leva minutos — o ensaio mediu 56 segundos. As outras três horas e
cinquenta são para **alguém perceber, decidir e conferir**. Prometer trinta minutos exigiria plantão, e
RTO que depende de plantão inexistente é número que não se cumpre no dia.

## Retenção: 30 dias de janela PITR

Trinta dias é o prazo em que se percebe corrupção lenta ou migration ruim. Antes disso o backup já não tem
uso prático: **restaurar um dump de três anos num schema duzentas migrations à frente não é plano**, é
esperança.

### Backup não é arquivo morto — e confundir os dois custa caro

A retenção legal plurianual da rastreabilidade **não** se resolve guardando backups velhos. Resolve-se
**exportando os dossiês**, que a plataforma já gera (`RPT-001`, JSON e PDF).

| | Backup | Arquivo |
|---|---|---|
| Responde | "o sistema volta" | "provo o que aconteceu em março de 2024" |
| Formato | dump + WAL, atado ao schema | dossiê exportado, legível sozinho |
| Prazo | 30 dias | o que a obrigação aplicável exigir |

Guardar backup por anos paga caro por um artefato que não cumpre nenhum dos dois papéis: velho demais para
restaurar, opaco demais para auditar.

> **A confirmar com quem responde pela conformidade:** o prazo legal aplicável à rastreabilidade de
> alimento nesta jurisdição. A política fixa o backup em 30 dias; o prazo do **arquivo** é obrigação
> externa e não se decide aqui.

## Cópia fora do ambiente

WAL e bases completas vão para bucket S3 **em conta separada** da que roda a aplicação, com chave de
criptografia em gerenciador de segredos distinto do cofre da aplicação.

A regra que dá sentido a isso: **quem comprometer a aplicação não pode apagar o backup.** Backup
criptografado cuja chave mora na mesma máquina do banco não é cópia fora de nada — é o mesmo dado, duas
vezes, com o mesmo dono.

## Banco e objetos: o banco manda

Assinaturas de entrega, PDFs de relatório e artefatos de rótulo vivem em armazenamento de objetos
(`OBJECT_STORAGE_PROVIDER=s3`), fora do Postgres.

**Decisão: restaura-se o banco ao ponto escolhido e os objetos ficam como estão.**

É seguro porque o armazenamento de objetos é, na prática, *append-only*: assinatura e relatório não se
editam. O efeito é objeto órfão — arquivo que existe e que nenhuma linha referencia —, e órfão é lixo, não
corrupção. O oposto seria pior: dossiê apontando para arquivo inexistente é um documento que mente.

Escrever isto importa mais do que parece: **sem a decisão registrada, alguém a toma sozinho no meio de um
incidente**, e às três da manhã.

## Quem decide restaurar

| Papel | Nome |
|---|---|
| Decide restaurar e escolhe o ponto no tempo | *a definir pelo mantenedor* |
| Executa o runbook | *a definir pelo mantenedor* |
| Confere a integridade e libera o retorno | *a definir pelo mantenedor* |

**Restaurar é destrutivo:** descarta tudo o que veio depois do ponto escolhido. Por isso a decisão tem
dono, e o dono é uma **pessoa nomeada**, não um cargo — cargo não atende o telefone.

Quem executa e quem libera podem ser a mesma pessoa numa casa pequena; quem decide deveria ser outra, pela
mesma razão que a carga de distribuição não é liberada por quem a montou (`LOG-001`).

## O que ainda não está provado

Escrito aqui porque quem lê "temos política de backup" precisa saber o que ela ainda não cobre:

- **O RPO está declarado, não medido.** Medi-lo exige o WAL archiving configurado e rodando, e observar o
  atraso real do arquivamento. Enquanto não houver ambiente, o número é compromisso — não medida.
- **O ensaio atual não exercita PITR.** Ele restaura um dump inteiro (`restore-drill.md`, passo 4).
  Recuperar até um instante escolhido é outra configuração e outro ensaio.
- **O passo de objetos nunca rodou com conteúdo.** Na execução de 2026-08-19 não havia objeto gravado.
- **O ensaio roda em máquina de desenvolvimento, com dados semeados.** Produção não existe
  (`REL-001`/`REL-005` seguem abertas), então não há cópia de produção para ensaiar. Os números são ordem
  de grandeza e prova de procedimento — não compromisso operacional.
