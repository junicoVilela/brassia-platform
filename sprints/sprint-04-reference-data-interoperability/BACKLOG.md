# Backlog — Sprint 04

## REF-001 — Registro de fontes, versões, licença e proveniência

**Objetivo:** Registrar a origem e as condições de uso de qualquer dado técnico incorporado ao catálogo.

**Critérios específicos:**

- Fonte registra tipo, proprietário, URL, licença, permissão, atribuição, frequência de revisão e responsável.
- Dataset registra versão, vigência, checksum, data de obtenção e payload original imutável.
- Conteúdo com permissão pendente não pode alcançar o estado `PUBLISHED`.
- Toda alteração relevante gera auditoria sem incluir credenciais ou documentos restritos.
- A consulta de um item publicado mostra fonte e versão de forma legível.

## REF-002 — Pipeline de staging, validação, revisão e publicação

**Objetivo:** Importar dados sem contaminar o catálogo utilizado por receitas.

**Critérios específicos:**

- Job percorre `RECEIVED → VALIDATING → REVIEW_REQUIRED → PUBLISHED` ou estado terminal de falha.
- Schema, tamanho, MIME, unidades, chaves naturais e duplicidades são validados.
- Erros por linha/campo são baixáveis e uma falha não deixa persistência parcial.
- Publicação exige permissão específica e confirmação do impacto.
- Rollback cria nova publicação; nunca apaga auditoria ou snapshot histórico.

## STD-001 — Conjuntos versionados de estilos cervejeiros

**Objetivo:** Consultar e comparar receitas com diferentes autoridades e edições.

**Critérios específicos:**

- Suporta BJCP Beer 2021, Mead 2015, Cider 2025, Brewers Association 2026 e perfil interno.
- Cada conjunto mantém autoridade, edição, idioma, vigência, atribuição e nível de permissão.
- Sem autorização ampliada, BJCP limita-se a código, nome, parâmetros e impressão geral permitida.
- Faixas de OG, FG, ABV, IBU e cor aceitam ausência e registram unidade original.
- Fora da faixa gera aviso explicável, nunca bloqueio automático de receita.
- Receitas publicadas preservam o snapshot do estilo usado.

## CAT-003 — Enriquecimento de maltes, lúpulos, culturas e adjuntos

**Objetivo:** Ampliar o catálogo já entregue na Sprint 02 com propriedades técnicas e fontes verificáveis.

**Critérios específicos:**

- Fermentáveis suportam fabricante, origem, cor, extrato, umidade, proteína, FAN e poder diastático quando publicados.
- Lúpulos suportam safra, alfa/beta, cohumulone, óleos, forma, finalidade e descritores.
- Culturas suportam laboratório, código, atenuação, temperatura, floculação, tolerância, POF e STA1.
- Valores variáveis por safra/lote pertencem ao estoque; o catálogo guarda faixas de referência.
- Equivalentes e substitutos mostram diferenças e fonte, sem afirmar identidade.
- Importação de fabricante depende de arquivo ou API autorizada e passa por revisão.

## WTR-003 — Perfis de água, laudos e alvos versionados

**Objetivo:** Separar água real, análise laboratorial, perfil alvo, mistura e tratamento.

**Critérios específicos:**

- Fonte, laudo datado e perfil alvo são entidades diferentes.
- Ca, Mg, Na, Cl, SO4, HCO3, alcalinidade, dureza e pH aceitam método, unidade e incerteza.
- Mistura mantém volumes de origem e tratamento proposto.
- Balanço de cargas mostra tolerância e alerta de inconsistência.
- Perfil histórico de cidade é educativo e não é aplicado automaticamente.
- Receita publicada preserva o laudo e o perfil alvo utilizados.

## REC-007 — Importar e exportar BeerJSON 1.0

**Objetivo:** Tornar BeerJSON o formato preferencial de intercâmbio externo.

**Critérios específicos:**

- Valida o documento contra a versão 1.0 fixada do schema.
- Importação mostra prévia, mapeamentos, avisos, perdas e campos não reconhecidos antes de confirmar.
- Exportação cobre receita, ingredientes, água, equipamento, processos e extensões BrassIA em namespace próprio.
- Valores e unidades originais são preservados quando possível.
- Importar o mesmo arquivo é idempotente.
- Testes de round-trip documentam diferenças semânticas aceitáveis.

## REC-008 — Compatibilidade legada BeerXML 1.0

**Objetivo:** Receber e fornecer receitas para ferramentas que ainda utilizam BeerXML.

**Critérios específicos:**

- Parser bloqueia entidades externas e limites abusivos.
- Importação suporta coleções e informa extensões desconhecidas.
- Relatório evidencia perda de etapas, campos ou precisão.
- Nenhuma extensão específica de fornecedor vira regra de domínio.
- Exportação informa que BeerXML pode representar menos dados que BrassIA/BeerJSON.

## REC-009 — Assistente de formulação e comparação com estilo

**Objetivo:** Guiar a criação a partir de intenção, estilo, equipamento e volume.

**Critérios específicos:**

- Usuário escolhe objetivo sensorial, conjunto/estilo, volume e equipamento.
- A tela compara metas e faixas por atributo, mostrando método e versão.
- Sugestões determinísticas explicam o impacto previsto e exigem confirmação.
- O assistente não substitui silenciosamente ingrediente nem altera receita publicada.
- Perfil personalizado funciona mesmo sem padrão oficial.

## CAL-001 — Hub de calculadoras cervejeiras

**Objetivo:** Disponibilizar cálculos independentes e aplicáveis a receita/lote.

**Critérios específicos:**

- Cobre conversões, gravidade, ABV, atenuação, cor, IBU, volumes, eficiência, água, pH estimado, pitch, starter e correções de OG.
- Frontend não replica fórmulas; utiliza o mesmo serviço determinístico do domínio.
- Resultado mostra entradas, método, versão, hipóteses, precisão, tolerância e alertas.
- Aplicar resultado exige permissão e informa quais campos serão alterados.
- Datasets dourados, análise dimensional, limites e regressão por versão ficam verdes.

## REC-010 — Substituições técnicas explicáveis

**Objetivo:** Ranqueiar alternativas quando um ingrediente não está disponível.

**Critérios específicos:**

- Ranking usa propriedades técnicas configuradas por tipo de ingrediente.
- Resultado mostra similaridades, diferenças, fonte, confiança e impacto estimado.
- Disponibilidade de estoque pode filtrar, mas não altera a equivalência técnica.
- IA pode redigir explicação, mas não calcular score nem inventar propriedades.
- A substituição sempre cria alteração explícita na versão em edição.

## Critérios transversais

- Operação respeita estado, permissão, `brewery_id`, concorrência e idempotência.
- Erro usa Problem Details RFC 9457 e não deixa persistência parcial.
- Comando relevante gera auditoria e evento quando aplicável.
- Testes cobrem sucesso, limite, falha, arquivo hostil, outra cervejaria e repetição.
