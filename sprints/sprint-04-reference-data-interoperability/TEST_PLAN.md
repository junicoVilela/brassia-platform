# Plano de testes — Sprint 04

## Domínio e persistência

- Transições de fonte, dataset e job de importação.
- Imutabilidade de payload bruto, publicação e snapshot de receita.
- Vigência, descontinuação e comparação entre versões.
- Deduplicação conservadora e revisão de homônimos.
- Migração a partir do estado real da Sprint 03.

## Contratos e segurança de arquivos

- JSON Schema BeerJSON 1.0 e contratos BrassIA.
- BeerXML bem formado, múltiplas receitas e extensões desconhecidas.
- XXE, entity expansion, zip bomb, MIME divergente, arquivo excessivo e timeout.
- Campos desconhecidos, unidade inválida e valor fora do domínio.
- Idempotência por checksum e chave do cliente.

## Cálculos

- Datasets dourados independentes.
- Conversão reversível de unidades.
- Propriedade dimensional e propagação de precisão.
- Limites, zero, ausentes e arredondamento.
- Regressão por versão de método.
- Frontend e backend retornam o mesmo resultado.

## Licença e proveniência

- Publicação bloqueada para `UNKNOWN`, `PENDING` e `DENIED`.
- Atribuição e link visíveis para cada conjunto de estilo.
- Conteúdo restrito ausente quando o nível é `LIMITED_PERMISSION`.
- Atualização não altera receita histórica.

## Autorização e tenant

- Curador global, administrador da cervejaria e usuário comum.
- Fonte global versus perfil privado da cervejaria.
- Acesso cruzado entre cervejarias.
- Auditoria sem payload sensível ou credencial.

## E2E principal

1. Cadastrar fonte autorizada.
2. Enviar dataset e analisar relatório.
3. Corrigir/aceitar mapeamentos.
4. Publicar dataset.
5. Criar receita usando estilo e ingredientes publicados.
6. Exportar BeerJSON.
7. Importar o arquivo em uma nova receita.
8. Comparar resultados e snapshots.

## Saída obrigatória

- relatório de cobertura por história;
- arquivos dourados e origem dos resultados esperados;
- relatório de compatibilidade BeerJSON/BeerXML;
- evidência de teste de migração;
- evidência do gate de licenciamento.
