# Entradas para criação do repositório

Preencha somente o que ainda não puder ser descoberto com segurança. A IA deve fazer uma única pergunta compacta quando algum campo obrigatório estiver ausente.

## Obrigatórias

- Provedor Git: GitHub, GitLab, Bitbucket ou Azure DevOps
- Organização/conta proprietária:
- Nome do repositório: `brassia-platform`
- Visibilidade: privado, interno ou público
- Descrição curta: `BrassIA — Plataforma inteligente de gestão cervejeira`

## Opcionais com padrão seguro

- Branch principal: `main`
- Licença: nenhuma para repositório privado; confirmar antes de publicar código aberto
- Estratégia: trunk-based com branches curtas e pull request
- Proteção de `main`: pull request, CI verde e bloqueio de force-push
- Exclusão automática de branch após merge: habilitada quando suportada

## Regras de segurança

- Antes de criar qualquer recurso remoto, mostrar provedor, proprietário, nome e visibilidade resolvidos.
- Verificar autenticação e autorização sem exibir token, cookie, chave SSH ou segredo.
- Se o repositório já existir, executar `fetch` e inspecionar branches/histórico antes de escrever.
- Nunca usar force-push, apagar branch, reescrever histórico, mudar visibilidade ou substituir um remoto existente sem autorização explícita.
- Não inicializar remotamente com arquivos que conflitem com o projeto local.
- Não versionar `.env`, credenciais, certificados privados, dumps, tokens, diretórios de build ou arquivos de IDE com dados pessoais.
