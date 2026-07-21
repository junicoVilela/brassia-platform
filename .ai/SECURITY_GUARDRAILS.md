# Guardrails de segurança

- Negar por padrão e conceder pelo menor privilégio.
- Nunca confiar em `brewery_id`, papel, preço, saldo ou status enviados pelo cliente.
- Validar autorização no caso de uso e reforçar filtro de tenant na persistência.
- Não incluir segredo, token, PII, conteúdo de documento ou prompt completo em log.
- Uploads recebem validação de tamanho, tipo, extensão, malware e nome gerado.
- Rate limit e idempotência para comandos críticos e integrações.
- IA não executa SQL livre, não acessa ferramenta fora de allowlist e não altera estado sem confirmação.
- Produtos químicos e equipamentos pressurizados sempre exigem procedimento aprovado; software não substitui fabricante ou responsável técnico.
