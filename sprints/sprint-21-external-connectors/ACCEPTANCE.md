# Aceite — Sprint 21

- [ ] `BLQ-INT-001` está desbloqueado: houve execução real contra o provedor, com evidência registrada.
- [ ] Conector começa read-only e pede apenas o escopo necessário.
- [ ] Receita importada passa pelo pipeline canônico da Sprint 04 e não por mapeamento paralelo.
- [ ] Credencial fica em cofre, é mascarada na leitura e não aparece em log, evento ou exportação.
- [ ] Retry preserva cursor e não duplica nem perde registro.
- [ ] Rate limit e revogação do provedor produzem estado explícito, não falha genérica.
- [ ] Prévia mostra criar, atualizar, ignorar e conflitar antes de qualquer escrita local.
- [ ] Conflito não sobrescreve receita local em silêncio.
- [ ] Campo desconhecido ou falha parcial vira relatório visível ao usuário.
- [ ] Frontend trata loading, vazio, erro, conflito e acesso negado.
- [ ] `.ai/DEFINITION_OF_DONE.md` foi executado.
- [ ] Débitos e decisões restantes foram registrados, não escondidos em TODO.
