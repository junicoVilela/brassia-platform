# Release, compatibilidade e migrations

Releases são versionados semanticamente e geram notas com histórias, migrations, mudanças de contrato, riscos e procedimento operacional. A pipeline produz artefatos imutáveis; ambientes promovem o mesmo artefato.

## Banco sem rollback destrutivo

Aplicar expand/contract: adicionar estrutura compatível, publicar aplicação que lê/escreve os dois formatos quando necessário, migrar dados de forma observável e só remover estrutura antiga em release posterior. Migration publicada não é editada. Antes do deploy, ensaiar cópia representativa, tempo de lock, backup e restauração.

## Compatibilidade

Mudança incompatível em API, evento, schema de IA ou exportação exige nova versão e janela de transição. Feature flags protegem funções incompletas, têm proprietário e data de remoção; não substituem autorização.
