# Catálogo de calculadoras

## Regra geral

Toda calculadora usa o mesmo motor do domínio. Ela pode ser aberta isoladamente ou dentro de receita/lote, mas nunca possui uma fórmula duplicada no frontend.

Cada resultado apresenta:

- entradas e unidades;
- fórmula/método e versão;
- resultado não arredondado e exibido;
- tolerância e faixa recomendada;
- alertas e hipóteses;
- botão para aplicar à receita ou lote quando seguro.

## Sprint 04 — ampliação de receita, água e formulação

As Sprints 02–03 fornecem cadastros e motor básico. Esta sprint acrescenta o hub reutilizável, métodos versionados que ainda não existirem e aplicação segura dos resultados.

- conversões de massa, volume, temperatura, pressão, cor e gravidade;
- OG, FG, ABV, atenuação aparente/real;
- Brix, SG e Plato;
- correção de refratômetro com álcool;
- eficiência de conversão, mostura, lautering e brewhouse;
- volume total, mostura, lavagem, pré-fervura e fermentador;
- absorção de grãos e lúpulo, evaporação, contração e perdas;
- cor por Morey como padrão, com método versionado;
- IBU por Tinseth como padrão e Rager como alternativa;
- contribuição de whirlpool/hop stand com parâmetros explícitos;
- mash thickness e grist ratio;
- temperatura de strike, infusão, repouso e decoção;
- mistura de água, sais e ácidos;
- alcalinidade residual e estimativa de pH da mostura;
- balanço iônico e relação sulfato/cloreto;
- ajuste de OG por diluição, concentração, DME ou açúcar;
- escala por volume, OG e eficiência;
- pitch rate e starter básico;
- calorias estimadas.

## Sprint 07 — execução

- correção de volume pré-fervura;
- correção de densidade pré-fervura e pós-fervura;
- água de reposição;
- tempo adicional de fervura;
- adição de extrato/açúcar;
- correção por temperatura de hidrômetro;
- capacidade térmica do mash tun;
- vazão e tempo de transferência;
- previsão de volume no fermentador.

## Sprint 09 — fermentação

- pitch rate avançado;
- starter em etapas;
- viabilidade por data e armazenamento;
- atenuação e FG estimada;
- taxa de fermentação e estabilidade;
- correção/calibração de densímetro eletrônico;
- pressão versus temperatura;
- coleta e reutilização de cultura.

## Sprint 10 — envase

- priming por açúcar e temperatura;
- carbonatação forçada;
- volumes de CO2;
- equilíbrio pressão/temperatura;
- comprimento e balanceamento de linha;
- conversão de pressão;
- unidades e perdas de envase.

## Sprint 13 — custo e utilidades

- custo por receita, lote, litro e embalagem;
- custo de perdas;
- consumo de água, energia e gás por litro;
- rendimento do lote;
- ponto de equilíbrio opcional na edição Pro.

## Testes

- datasets dourados publicados no repositório;
- comparação com exemplos manuais independentes;
- limites, zero, unidades alternativas e arredondamento;
- propriedade dimensional;
- regressão por versão de método;
- nenhuma alteração retroativa em snapshot publicado.
