# Workflow de sprint para IA

1. Selecionar sprint e história.
2. Ler documentos obrigatórios indicados em `AI_EXECUTION.md`.
3. Produzir plano de fatia vertical e contratos.
4. Escrever primeiro os testes de domínio/aceite que esclarecem a regra.
5. Implementar domínio, aplicação, persistência, API e UI mínima.
6. Executar testes, inspeção de arquitetura e migrations.
7. Revisar segurança, multi-tenancy e observabilidade.
8. Passar pelo portão de entrega (abaixo) antes de escrever "Concluída".
9. Atualizar documentação e entregar relatório.

Capacidade é variável: se uma sprint não couber, divida histórias; não reduza qualidade nem misture sprints.

## Portão de entrega

Uma história só é declarada **Concluída** depois que estas duas coisas existirem — não depois de a sprint fechar,
não na próxima sprint, não quando alguém for procurar:

1. **A jornada E2E que o `TEST_PLAN.md` da sprint pediu está escrita e passando.** Se o plano de testes pede um
   caminho ponta a ponta, esse caminho é parte da história, e não um item separado a combinar depois.
2. **Uma revisão de código correu sobre o diff da história**, e os achados foram corrigidos ou registrados como
   débito com efeito e critério de remoção.

Se algum dos dois não couber no tempo, a história fica **Parcial** e o que falta vai para o STATUS com nome.
"Concluída" é uma afirmação sobre o que foi conferido, não sobre o que foi digitado.

### Por que este portão existe

As sprints 19 e 20 fecharam com histórias marcadas como concluídas cujos planos de teste pediam jornadas E2E que
ninguém escreveu — e a ausência não apareceu em relatório nenhum (`DEB-SAL-004`, `DEB-LOG-002`). Quando essas
jornadas e as revisões finalmente correram, elas encontraram defeitos em código já declarado pronto, entre eles:

- crédito conferido **depois** de reservar estoque, enquanto cinco documentos afirmavam o contrário (`DEB-SAL-005`);
- oito stores do frontend lendo `e.error?.code` onde o interceptor entrega o campo no primeiro nível — nenhuma
  mensagem do servidor chegava à tela;
- escrita de vasilhame sem conferir `version`, gravando por cima de outra operação sem erro (`DEB-CON-003`).

Nenhum desses defeitos era sutil. Todos sobreviveram porque a conferência veio depois da declaração de pronto.
