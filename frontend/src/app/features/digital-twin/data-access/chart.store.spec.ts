import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ControlChart } from '../domain/chart.model';
import { ChartStore } from './chart.store';

/**
 * Estado da carta de controle (SPC-001).
 *
 * <p>O que estes testes fixam na interface: o eixo enquadra os <em>limites</em> e não só os pontos, não
 * produz NaN em série constante, não sai espelhado, e histórico curto vira providência em vez de erro.
 */
describe('ChartStore', () => {
  let store: ChartStore;
  let http: HttpTestingController;

  const URL = '/api/v1/digital-twin/control-charts';

  function chartWith(
    values: number[],
    limits: Partial<ControlChart['controlLimits']> = {},
  ): ControlChart {
    return {
      kind: 'TEMPERATURE',
      unit: 'C',
      points: values.map((value, i) => ({
        batchId: `b${i}`,
        value,
        measuredAt: `2026-08-0${(i % 9) + 1}T10:00:00Z`,
      })),
      controlLimits: {
        centerLine: 20,
        lowerControlLimit: 17,
        upperControlLimit: 23,
        sigma: 1,
        sampleSize: values.length,
        ...limits,
      },
      signals: [],
      inControl: true,
    };
  }

  function analyzeAnd(chart: ControlChart): void {
    store.analyze('r1', 'TEMPERATURE', ['b1']);
    http.expectOne(URL).flush(chart);
  }

  function analyzeAndFail(body: object, status: number): void {
    store.analyze('r1', 'TEMPERATURE', ['b1']);
    http.expectOne(URL).flush(body, { status, statusText: 'erro' });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ChartStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ChartStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('guarda a carta devolvida', () => {
    analyzeAnd(chartWith([19, 20, 21]));

    expect(store.hasChart()).toBe(true);
    expect(store.plotted().length).toBe(3);
  });

  it('A ESCALA INCLUI OS LIMITES, não só os pontos', () => {
    // Uma carta cujo eixo termina no maior ponto esconde a distância até o limite — que é a única coisa
    // que o gráfico existe para mostrar.
    analyzeAnd(chartWith([20, 20, 20]));

    const pointY = store.plotted()[0].y;
    expect(store.upperY()!).toBeLessThan(pointY);
    expect(store.lowerY()!).toBeGreaterThan(pointY);
  });

  it('não gera NaN quando a faixa inteira é zero', () => {
    // Processo perfeitamente constante: sem o piso, a divisão por zero levaria NaN para o atributo do SVG.
    analyzeAnd(chartWith([20, 20], { lowerControlLimit: 20, upperControlLimit: 20 }));

    for (const plot of store.plotted()) {
      expect(Number.isNaN(plot.y)).toBe(false);
    }
    expect(store.polyline()).not.toContain('NaN');
  });

  it('O EIXO NÃO SAI ESPELHADO: valor maior fica mais acima', () => {
    // SVG cresce para baixo. Sem a inversão, uma tendência de alta desceria no desenho.
    analyzeAnd(chartWith([18, 22]));

    const [baixo, alto] = store.plotted();
    expect(alto.y).toBeLessThan(baixo.y);
  });

  it('marca o ponto fora dos limites', () => {
    analyzeAnd(chartWith([20, 30]));

    expect(store.plotted()[0].outOfLimits).toBe(false);
    expect(store.plotted()[1].outOfLimits).toBe(true);
  });

  it('HISTÓRICO CURTO não vira erro: vira quantas faltam', () => {
    // Falta de histórico tem providência concreta; falha de sistema não tem. Misturar as duas faria
    // alguém recarregar a página em vez de medir mais.
    analyzeAndFail({ code: 'insufficient_control_history', available: 12, required: 20 }, 422);

    expect(store.error()).toBeNull();
    expect(store.shortHistory()).toContain('faltam 8');
  });

  it('lê o código em e.error e não em e', () => {
    // O HttpErrorResponse embrulha o corpo; ler no nível errado faria todo Problem Details cair na
    // mensagem genérica.
    analyzeAndFail({ code: 'mixed_units_in_series', detail: 'série em C e F' }, 422);

    expect(store.error()).toBe('série em C e F');
    expect(store.shortHistory()).toBeNull();
  });

  it('403 diz que é permissão, não falha', () => {
    analyzeAndFail({}, 403);

    expect(store.error()).toContain('permissão');
  });

  it('limpa a carta anterior antes de buscar outra', () => {
    // Sem isso, a carta velha continuaria na tela enquanto a nova carrega — e uma carta de outra grandeza
    // parece uma carta válida.
    analyzeAnd(chartWith([19, 20, 21]));

    analyzeAndFail({}, 500);

    expect(store.chart()).toBeNull();
  });

  it('clear devolve o estado inicial', () => {
    analyzeAnd(chartWith([19, 20, 21]));

    store.clear();

    expect(store.chart()).toBeNull();
    expect(store.error()).toBeNull();
    expect(store.shortHistory()).toBeNull();
  });
});
