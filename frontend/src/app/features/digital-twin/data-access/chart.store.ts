import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ControlChart, ControlPoint, MeasurementKind } from '../domain/chart.model';
import { ChartApi } from './chart.api';

interface ChartError {
  status?: number;
  error?: { code?: string; detail?: string; available?: number; required?: number };
}

/** Coordenadas já resolvidas para o SVG, para o template não fazer aritmética. */
export interface PlottedPoint {
  x: number;
  y: number;
  point: ControlPoint;
  outOfLimits: boolean;
  index: number;
}

const VIEW_WIDTH = 720;
const VIEW_HEIGHT = 260;
const PADDING = 28;

/**
 * Estado da carta de controle (SPC-001).
 *
 * <p><strong>Histórico curto não é erro de sistema, e a tela não o trata como erro.</strong> É um estado
 * legítimo com providência concreta — medir mais, ou incluir mais lotes —, e por isso vira uma mensagem
 * que diz **quantas** medições faltam, não um "algo deu errado" genérico. A diferença é entre alguém saber
 * o que fazer e alguém recarregar a página.
 */
@Injectable()
export class ChartStore {
  private readonly api = inject(ChartApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly chart = signal<ControlChart | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Separado de `error`: falta de histórico tem providência, falha de sistema não tem. */
  readonly shortHistory = signal<string | null>(null);

  readonly hasChart = computed(() => this.chart() !== null);

  /**
   * A escala do eixo vertical.
   *
   * <p>Inclui os limites de controle além dos pontos: uma carta cujo eixo termina no maior ponto esconde
   * justamente a distância até o limite, que é a única coisa que o gráfico existe para mostrar.
   */
  private readonly scale = computed(() => {
    const chart = this.chart();
    if (!chart) {
      return null;
    }
    const values = [
      ...chart.points.map(p => p.value),
      chart.controlLimits.lowerControlLimit,
      chart.controlLimits.upperControlLimit,
    ];
    const min = Math.min(...values);
    const max = Math.max(...values);
    // Faixa zero acontece com processo perfeitamente constante; sem o piso, todo ponto cairia na mesma
    // linha e uma divisão por zero levaria NaN para dentro do atributo do SVG.
    const span = max - min || 1;
    return { min: min - span * 0.1, max: max + span * 0.1 };
  });

  readonly plotted = computed<PlottedPoint[]>(() => {
    const chart = this.chart();
    const scale = this.scale();
    if (!chart || !scale) {
      return [];
    }
    const limits = chart.controlLimits;
    const step =
      chart.points.length > 1 ? (VIEW_WIDTH - 2 * PADDING) / (chart.points.length - 1) : 0;
    return chart.points.map((point, index) => ({
      x: PADDING + index * step,
      y: this.yOf(point.value, scale),
      point,
      outOfLimits:
        point.value < limits.lowerControlLimit || point.value > limits.upperControlLimit,
      index,
    }));
  });

  /** A linha ligando os pontos. Sem ela, sequência e tendência não se enxergam. */
  readonly polyline = computed(() =>
    this.plotted()
      .map(p => `${p.x},${p.y}`)
      .join(' '),
  );

  readonly centerY = computed(() => this.lineY(c => c.centerLine));
  readonly upperY = computed(() => this.lineY(c => c.upperControlLimit));
  readonly lowerY = computed(() => this.lineY(c => c.lowerControlLimit));

  readonly viewBox = `0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`;
  readonly plotRight = VIEW_WIDTH - PADDING;
  readonly plotLeft = PADDING;

  analyze(recipeId: string, kind: MeasurementKind, batchIds: string[]): void {
    this.loading.set(true);
    this.error.set(null);
    this.shortHistory.set(null);
    this.chart.set(null);
    this.api
      .analyze({ recipeId, kind, batchIds })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: chart => this.chart.set(chart),
        error: (e: ChartError) => this.handle(e),
      });
  }

  clear(): void {
    this.chart.set(null);
    this.error.set(null);
    this.shortHistory.set(null);
  }

  private handle(e: ChartError): void {
    // `e.error` e não `e`: o HttpErrorResponse embrulha o corpo, e ler o código no nível errado faria todo
    // Problem Details cair na mensagem genérica.
    const code = e.error?.code;
    if (code === 'insufficient_control_history') {
      const faltam = (e.error?.required ?? 20) - (e.error?.available ?? 0);
      this.shortHistory.set(
        `Há ${e.error?.available ?? 0} medições e são necessárias ${e.error?.required ?? 20}: ` +
          `faltam ${faltam}. Meça mais neste lote ou inclua outros lotes da mesma receita. ` +
          'Limites calculados sobre poucos pontos passam qualquer coisa — e um controle que nunca ' +
          'dispara parece um processo saudável.',
      );
      return;
    }
    if (code === 'mixed_units_in_series') {
      this.error.set(
        e.error?.detail ??
          'As medições estão em unidades diferentes. Comparar números em unidades distintas produziria ' +
            'limites sem significado.',
      );
      return;
    }
    if (e.status === 403) {
      this.error.set('Você não tem permissão para ler o gêmeo digital desta cervejaria.');
      return;
    }
    this.error.set(e.error?.detail ?? 'Não foi possível montar a carta de controle.');
  }

  private lineY(pick: (limits: ControlChart['controlLimits']) => number): number | null {
    const chart = this.chart();
    const scale = this.scale();
    return chart && scale ? this.yOf(pick(chart.controlLimits), scale) : null;
  }

  private yOf(value: number, scale: { min: number; max: number }): number {
    const ratio = (value - scale.min) / (scale.max - scale.min);
    // SVG cresce para baixo; sem a inversão a carta sairia espelhada e uma tendência de alta desceria.
    return VIEW_HEIGHT - PADDING - ratio * (VIEW_HEIGHT - 2 * PADDING);
  }
}
