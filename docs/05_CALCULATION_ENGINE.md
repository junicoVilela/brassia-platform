# Motor de cálculos

Cálculos são determinísticos, versionados e independentes da IA. Cada resultado guarda método, versão, entradas normalizadas e arredondamento.

## Conjuntos

- balanço de volumes, absorção, evaporação e perdas;
- OG/FG, eficiência, atenuação e ABV;
- IBU por método selecionado e cor;
- água, mistura de fontes, íons, sais e acidificação;
- pitch rate, viabilidade e starter;
- carbonatação/priming;
- diluição/concentração por balanço de massa;
- custo planejado e realizado.

## Regras

Não espalhar fórmulas em controllers ou componentes. Testes usam datasets dourados com tolerância explícita. Mudança de fórmula cria versão e não recalcula histórico silenciosamente.
