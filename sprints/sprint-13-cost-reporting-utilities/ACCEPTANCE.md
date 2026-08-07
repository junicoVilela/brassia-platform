# Aceite — Sprint 13

- [x] Todas as histórias selecionadas atendem critérios específicos.
- [x] Nenhuma história posterior foi implementada parcialmente.
- [x] Testes de domínio, integração, autorização e tenant estão verdes.
- [x] OpenAPI, migrations, eventos e documentação estão consistentes.
- [x] Frontend trata loading, vazio, erro, conflito e acesso negado.
- [x] Observabilidade permite localizar a operação por traceId.
- [x] `.ai/DEFINITION_OF_DONE.md` foi executado.
- [x] Débitos e decisões restantes foram registrados, não escondidos em TODO.

## Observações para o aceite

**A sprint tem um fio condutor, e ele é a distinção entre derivar e guardar.** Quatro das seis
histórias não criaram tabela nenhuma: o custo enquanto aberto, a variação, o consumo por litro, o
dossiê e o painel são sobre o presente e se refazem a cada pergunta. As duas que guardam guardam
coisas diferentes — o custo **fechado**, que é a resposta assinada de um dia, e a **definição** de
relatório salvo, que é um acordo sobre para quem mandar o quê. Materializar os outros criaria
versões que discordariam da produção no dia seguinte, e seriam justamente elas as impressas.

**A segunda regra que atravessa a sprint é a recusa do zero silencioso.** Custo sem mão de obra
declara a lacuna em vez de somar zero; consumo por litro sem envase responde vazio e não "0 L/L";
conformidade sobre nenhuma medição vem com ressalva; genealogia com elo faltando diz que não prova
rastreabilidade; plano de material desconhecido não vira plano zero. Em todos os casos a alternativa
— um número redondo sem aviso — passaria por resposta boa.

**Eventos:** nenhuma história desta sprint publica evento de domínio, e é decisão, não esquecimento.
Nada aqui é reativo: painel, variação e dossiê são consultas, e a execução programada é disparada
por relógio, não por fato de negócio. Auditoria, essa sim, existe em todo comando — inclusive nos
dois que não mudam dado nenhum (exportar relatório e abrir link de download), porque o que eles
fazem é tirar informação de dentro do sistema.

**O que se ganhou de arquitetura:** a federação por porta, que a sprint 12 usou na genealogia,
apareceu mais três vezes — utilidades, custo e painel — e numa posição nova. A porta do painel mora
no `shared` porque o relatório já depende de produção, custo e qualidade, e essas dependências não
podem se inverter. Quando a direção óbvia fecharia ciclo, foi a direção que mudou, nunca a regra.

## Ressalvas a considerar antes de aceitar

1. **O custo do lote é sempre menor que a verdade** (`CST-001-A`, `CST-001-B`). Falta mão de obra,
   que não existe na plataforma, e utilidade, que existe mas por equipamento e não por lote. O
   número é utilizável para comparar lotes entre si; não é utilizável para formar preço sem alguém
   somar as duas parcelas por fora. A tela e o contrato dizem isso, mas quem exporta o JSON leva o
   total junto com as lacunas e pode ler só o total.

2. **A entrega programada não entrega** (`RPT-003-A`). A plataforma registra a definição, executa no
   período, produz o artefato, emite link e guarda a tentativa de entrega — mas não há transporte de
   e-mail. Na prática, hoje o destinatário precisa entrar na tela. É a mesma disciplina das
   notificações de recall da sprint 12, e vale confirmar se é aceitável para o uso pretendido antes
   de programar relatórios com destinatários.

3. **A base de preço da variação é a reserva, não um custo padrão.** A plataforma não tem custo
   padrão e esta sprint não inventou um. A comparação responde "paguei mais caro do que o que eu
   tinha separado?", que é útil e é honesta, mas **não é** variação contra padrão orçado. Se a casa
   quiser a segunda, é preciso decidir quem cadastra o padrão e com que periodicidade ele é revisto
   — a dúvida está registrada no `STATUS.md`.

4. **`GAS-001-A` foi adiado de propósito.** A sprint 13 previa fechá-lo; o critério da UTL-001 pede
   consumo por litro, não custo por litro, e criar preço de cilindro é escopo comercial. O débito
   segue aberto com o mesmo texto da sprint 10.

5. **O agendador é de instância única** (`RPT-003-B`). Duas instâncias não duplicam nada — a chave de
   idempotência e o índice único resolvem —, mas também não dividem trabalho. Vale confirmar antes
   de subir mais de uma instância em produção.

- **Aceite:** **Valdemir Vilela Junior, 2026-08-07** — aceita com as ressalvas acima.
