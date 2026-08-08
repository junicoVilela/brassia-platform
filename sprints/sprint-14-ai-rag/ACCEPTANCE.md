# Aceite — Sprint 14

- [x] Todas as histórias selecionadas atendem critérios específicos.
- [x] Nenhuma história posterior foi implementada parcialmente.
- [x] Testes de domínio, integração, autorização e tenant estão verdes.
- [x] OpenAPI, migrations, eventos e documentação estão consistentes.
- [x] Frontend trata loading, vazio, erro, conflito e acesso negado.
- [x] Observabilidade permite localizar a operação por traceId.
- [x] `.ai/DEFINITION_OF_DONE.md` foi executado.
- [x] Débitos e decisões restantes foram registrados, não escondidos em TODO.

## Notas do aceite

**Sobre "nenhuma história posterior implementada parcialmente":** o inverso é o que precisa ser dito — cada
história desta sprint deixou deliberadamente ausente o que pertencia à seguinte. O schema de resposta da
RAG-002 e o da AIA-002 foram construídos **sem campo de comando**, e um comando devolvido pelo modelo derrubava
a resposta inteira com 502; a AIA-003 abriu esse campo, e o abriu para uma allowlist fechada. A ausência estava
coberta por teste antes de existir a história que a preenche.

**Sobre autorização e tenant:** cada história tem teste negativo próprio. O da AIA-003 é o que a sprint existe
para produzir: quem tem `ai.command.propose` recebe **403 no aceite**, e a proposta continua pendente —
`CommandProposalIT.proporNaoDaDireitoDeConfirmar`. Isolamento por cervejaria responde 404 e não 403, para não
contar que a proposta existe em algum lugar.

**Sobre eventos:** nenhuma história desta sprint publica evento de domínio, e isso é escolha, não omissão. A IA
não altera nada — o único efeito de negócio possível é a decisão humana registrada, e ela é auditoria, não
integração. Quando DEB-AIA-002 for resolvido, o comando executado publicará o evento do módulo dono da ação.

**Não exercitado, declarado:** uma geração bem-sucedida contra o provedor real. O caminho habilitado foi
verificado manualmente com chave inválida (fallback entre os dois modelos e ledger corretos); pôr chave de
terceiro na CI não é opção. Ver a pendência declarada no STATUS.
