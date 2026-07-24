# Estratégia de APIs e integrações externas

## Matriz de decisão

| Fonte | Tipo | Uso proposto | Autenticação | Prioridade | Limitação principal |
|---|---|---|---|---|---|
| BeerJSON 1.0 | Schema/pacote | Importar e exportar receita, catálogo e perfis | Nenhuma | P0 / Sprint 04 | Não é API nem base de ingredientes |
| BeerXML 1.0 | Formato legado | Compatibilidade com software antigo | Nenhuma | P1 / Sprint 04 | Modelo limitado e perda semântica |
| BJCP | Site/documentos/JSON externo referenciado | Dataset de estilos versionado | Nenhuma | P0 / Sprint 04 | Uso de conteúdo exige regras e permissão |
| Brewers Association | Página/PDF anual | Segundo conjunto de estilos | Nenhuma | P1 / Sprint 04 | Atualização anual e condições de uso |
| Brewfather REST API v2 | REST | Receitas, lotes e inventário do próprio usuário | Basic com user id + API key e escopos | P1 / Sprint 15 | Limite informado de 500 chamadas/hora/chave |
| Brewer's Friend API v1 | REST | Receitas, sessões e fermentação do próprio usuário | `X-API-KEY` | P2 / Sprint 15 | API antiga; BeerXML pode ser mais completo |
| Grainfather | Arquivo/webhook/ecossistema | Migração por formato e ingestão de leituras | Conforme integração | P2 / Sprint 15 | Não depender de API pública não documentada |
| Sensores | HTTP/MQTT/webhook | Temperatura, densidade, pressão e vazão | Chave por dispositivo ou mTLS | P1 / Sprint 15 | Payloads, relógios e frequência variam |
| Catálogos de fabricantes | CSV/XLSX/PDF/JSON autorizado | Atualizar ingredientes e especificações | Variável | P1 contínua | Não há API neutra consolidada |

## Conclusões

- BeerJSON é o contrato canônico externo.
- BeerXML é adaptador de compatibilidade.
- Brewfather e Brewer's Friend sincronizam somente dados pertencentes ao usuário autenticado.
- A base global de ingredientes de concorrentes não deve ser copiada nem tratada como API pública.
- Fontes sem licença, SLA ou documentação ficam desativadas por padrão.

## Arquitetura

Cada conector implementa portas separadas:

```text
RecipeImportPort
RecipeExportPort
ExternalRecipeSyncPort
ExternalInventorySyncPort
SensorReadingIngestPort
ReferenceDatasetImportPort
```

O domínio não conhece DTOs externos. Cada adapter converte o payload para um modelo canônico, gera relatório de compatibilidade e somente então chama casos de uso.

## Pipeline

1. Receber arquivo ou iniciar sincronização.
2. Validar tamanho, MIME, extensão e schema.
3. Guardar payload bruto com criptografia e retenção definida.
4. Normalizar unidades sem perder o valor original.
5. Mapear entidades e registrar campos ignorados.
6. Exibir prévia, conflitos e impacto.
7. Confirmar criação, atualização ou vínculo.
8. Persistir de forma idempotente.
9. Auditar origem, ator, checksum e resultado.

## Estratégias por integração

### Brewfather

- usar API v2;
- solicitar somente escopos necessários;
- começar com importação unilateral de receitas;
- suportar paginação e `start_after`;
- respeitar rate limit com backoff e cache;
- segredos ficam em cofre/secret store;
- adicionar lotes e inventário somente após resolver conflitos;
- nunca tentar acessar a base global de ingredientes.

### Brewer's Friend

- iniciar com importação de receita;
- preferir o endpoint BeerXML quando o JSON não trouxer todos os ingredientes;
- registrar a versão antiga da API como risco;
- aplicar timeout, retry limitado e circuit breaker;
- não assumir escrita ou sincronização bidirecional sem endpoint documentado.

### Grainfather e outros softwares

- priorizar BeerJSON/BeerXML;
- permitir upload e prévia de compatibilidade;
- para dispositivos, receber webhook/custom stream;
- implementar controle remoto apenas com documentação oficial e opt-in explícito.

### Sensores

Payload canônico:

```json
{
  "deviceId": "string",
  "externalReadingId": "string",
  "measuredAt": "2026-07-23T18:00:00-03:00",
  "receivedAt": "2026-07-23T18:00:03-03:00",
  "temperatureC": 18.4,
  "specificGravity": 1.012,
  "pressureKpa": 80.2,
  "batteryPercent": 74,
  "signal": -67,
  "quality": "RAW"
}
```

Regras:

- idempotência por dispositivo + identificador externo;
- armazenar `measuredAt` e `receivedAt`;
- rejeitar timestamp absurdo e sinalizar leitura atrasada;
- preservar bruto e corrigido;
- não promover leitura ruidosa diretamente a decisão crítica;
- autenticação separada por dispositivo.

## Segurança

- credenciais nunca entram em receita, log, exportação ou evento;
- rotacionar e revogar chaves;
- criptografar segredo em repouso;
- bloquear SSRF em webhooks e importadores de URL;
- impor allowlist de conteúdo, limites e varredura de arquivo;
- aplicar tenant e permissão em todos os jobs;
- assinatura HMAC para webhooks emitidos;
- inbox/outbox para idempotência e retry.

## Endpoints internos sugeridos

```text
POST /api/v1/recipes/imports
GET  /api/v1/recipes/imports/{id}
POST /api/v1/recipes/imports/{id}/confirm
GET  /api/v1/recipes/{id}/exports/{format}

GET  /api/v1/reference/styles
GET  /api/v1/reference/ingredients
POST /api/v1/reference/import-jobs
GET  /api/v1/reference/import-jobs/{id}
POST /api/v1/reference/import-jobs/{id}/publish

POST /api/v1/integrations
POST /api/v1/integrations/{id}/test
POST /api/v1/integrations/{id}/sync
GET  /api/v1/integrations/{id}/runs

POST /api/v1/device-stream/{streamKey}
```

## Referências

- https://beerjson.github.io/beerjson/
- https://github.com/beerjson/beerjson
- https://beerxml.com/
- https://docs.brewfather.app/api
- https://docs.brewfather.app/integrations
- https://docs.brewersfriend.com/api/recipes
- https://www.bjcp.org/bjcp-style-guidelines/
- https://www.brewersassociation.org/edu/brewers-association-beer-style-guidelines/
