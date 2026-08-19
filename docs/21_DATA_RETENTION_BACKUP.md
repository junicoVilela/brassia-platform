# Retenção, backup e recuperação

Classificar dados em operacional, auditoria/rastreabilidade, documentos, telemetria técnica, conteúdo de IA e dados pessoais. Cada classe define finalidade, acesso, retenção, exportação e descarte seguro conforme obrigação aplicável.

Banco e objetos precisam de backups coordenados, criptografados, com retenção e cópia fora do ambiente principal. Backup sem teste de restauração não é controle válido. O runbook deve registrar RPO/RTO, dependências, ordem de recuperação, verificação de integridade e responsável pela decisão.

**O teste de restauração é o `infra/runbooks/restore-drill.md`**, e a execução mais recente fica registrada no fim dele. Duas ressalvas que o documento não pode omitir, porque quem lê "temos teste de restauração" precisa saber o que ele cobre:

- **O RTO está medido; o RPO não.** O ensaio mede quanto tempo leva para voltar a servir a partir de um dump. A janela de perda depende de frequência de backup, retenção e cópia fora do ambiente — política que ainda não existe. Enquanto não existir, não há RPO, e nenhum ensaio o produz.
- **O ensaio roda em máquina de desenvolvimento, com dados semeados.** Produção não existe (REL-001/REL-005 seguem abertas), então não há cópia de produção para ensaiar. Os números são ordem de grandeza e prova de que o procedimento funciona — não compromisso operacional.

Exclusões legais não podem quebrar genealogia, estoque ou auditoria: quando necessário, anonimizar dados pessoais e preservar a evidência operacional mínima.
