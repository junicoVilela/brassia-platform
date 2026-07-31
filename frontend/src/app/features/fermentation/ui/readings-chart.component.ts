import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FermentationReading, ReadingSource } from '../domain/reading.model';

interface Point {
  x: number;
  y: number;
  reading: FermentationReading;
  label: string;
}

interface Series {
  source: ReadingSource;
  label: string;
  color: string;
  path: string;
  points: Point[];
}

const WIDTH = 960;
const HEIGHT = 300;
const PAD = { top: 16, right: 24, bottom: 32, left: 56 };

/**
 * Curva de uma grandeza ao longo do tempo (FER-002). A origem é codificada por cor **e**
 * forma (sensor = círculo em linha cheia; manual = losango em linha tracejada), e a leitura
 * sinalizada como implausível ganha um anel de status — nunca é omitida da série.
 */
@Component({
  selector: 'app-readings-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .chart-svg { width: 100%; height: auto; overflow: visible; }
    .grid-line { stroke: var(--bs-border-color); stroke-width: 1; }
    .axis-text { fill: var(--bs-secondary-color); font-size: 12px; }
    .series-line { fill: none; stroke-width: 2; }
    .marker-invalid { fill: none; stroke: var(--bs-danger); stroke-width: 2; }
  `,
  template: `
    <figure class="mb-0">
      <figcaption class="d-flex flex-wrap gap-3 align-items-center mb-2 small">
        @for (s of series(); track s.source) {
          <span class="d-inline-flex align-items-center gap-1">
            <svg width="14" height="14" aria-hidden="true">
              @if (s.source === 'SENSOR') {
                <circle cx="7" cy="7" r="5" [attr.fill]="s.color"></circle>
              } @else {
                <polygon points="7,1 13,7 7,13 1,7" [attr.fill]="s.color"></polygon>
              }
            </svg>
            <span>{{ s.label }} ({{ s.points.length }})</span>
          </span>
        }
        @if (invalidCount() > 0) {
          <span class="d-inline-flex align-items-center gap-1 text-danger">
            <i class="ri-error-warning-line" aria-hidden="true"></i>
            {{ invalidCount() }} sinalizada(s) fora da faixa plausível
          </span>
        }
      </figcaption>

      <svg class="chart-svg" [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img"
           [attr.aria-label]="'Curva de ' + unitLabel() + ' por instante, separando leituras manuais e de sensor'">
        @for (t of yTicks(); track t.value) {
          <line class="grid-line" [attr.x1]="pad.left" [attr.x2]="width - pad.right" [attr.y1]="t.y" [attr.y2]="t.y"></line>
          <text class="axis-text" [attr.x]="pad.left - 8" [attr.y]="t.y + 4" text-anchor="end">{{ t.label }}</text>
        }
        @for (t of xTicks(); track t.label) {
          <text class="axis-text" [attr.x]="t.x" [attr.y]="height - 8" [attr.text-anchor]="t.anchor">{{ t.label }}</text>
        }

        @for (s of series(); track s.source) {
          @if (s.points.length > 1) {
            <path class="series-line" [attr.d]="s.path" [attr.stroke]="s.color"
                  [attr.stroke-dasharray]="s.source === 'MANUAL' ? '6 4' : null"></path>
          }
          @for (p of s.points; track p.reading.id) {
            <g>
              @if (s.source === 'SENSOR') {
                <circle [attr.cx]="p.x" [attr.cy]="p.y" r="5" [attr.fill]="s.color"
                        stroke="var(--bs-body-bg)" stroke-width="2"></circle>
              } @else {
                <polygon [attr.points]="diamond(p)" [attr.fill]="s.color"
                         stroke="var(--bs-body-bg)" stroke-width="2"></polygon>
              }
              @if (!p.reading.valid) {
                <circle class="marker-invalid" [attr.cx]="p.x" [attr.cy]="p.y" r="9"></circle>
              }
              <title>{{ p.label }}</title>
            </g>
          }
        }
      </svg>
    </figure>
  `,
})
export class ReadingsChartComponent {
  readonly readings = input.required<FermentationReading[]>();

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly pad = PAD;

  protected readonly invalidCount = computed(() => this.readings().filter(r => !r.valid).length);
  protected readonly unitLabel = computed(() => this.readings()[0]?.unit ?? '');

  /** Escala compartilhada pelas duas origens: um único eixo de valor, nunca dois. */
  private readonly scale = computed(() => {
    const items = this.readings();
    const times = items.map(r => Date.parse(r.measuredAt));
    const values = items.map(r => r.value);
    const minT = Math.min(...times);
    const maxT = Math.max(...times);
    const minV = Math.min(...values);
    const maxV = Math.max(...values);
    // Séries constantes (ou de ponto único) ganham uma folga para não colapsar no eixo.
    const padV = maxV - minV === 0 ? Math.abs(maxV) * 0.05 + 0.5 : (maxV - minV) * 0.1;
    return {
      minT, maxT,
      minV: minV - padV,
      maxV: maxV + padV,
      x: (t: number) => maxT === minT
        ? (PAD.left + WIDTH - PAD.right) / 2
        : PAD.left + ((t - minT) / (maxT - minT)) * (WIDTH - PAD.left - PAD.right),
      y: (v: number) => {
        const lo = minV - padV;
        const hi = maxV + padV;
        return HEIGHT - PAD.bottom - ((v - lo) / (hi - lo)) * (HEIGHT - PAD.top - PAD.bottom);
      },
    };
  });

  protected readonly series = computed<Series[]>(() => {
    const items = this.readings();
    const scale = this.scale();
    const build = (source: ReadingSource, label: string, color: string): Series => {
      const points = items
        .filter(r => r.source === source)
        .map(r => ({
          x: scale.x(Date.parse(r.measuredAt)),
          y: scale.y(r.value),
          reading: r,
          label: `${label} · ${r.value} ${r.unit} · ${new Date(r.measuredAt).toLocaleString('pt-BR')}`
            + (r.valid ? '' : ` · ${r.invalidReason}`),
        }));
      const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
      return { source, label, color, path, points };
    };
    // Hues validados para CVD nos temas claro e escuro (ver ADR de dataviz).
    return [
      build('SENSOR', 'Sensor', '#2f7ef0'),
      build('MANUAL', 'Manual', '#c9761f'),
    ].filter(s => s.points.length > 0);
  });

  protected readonly yTicks = computed(() => {
    const scale = this.scale();
    const step = (scale.maxV - scale.minV) / 4;
    return Array.from({ length: 5 }, (_, i) => {
      const value = scale.minV + step * i;
      return { value, y: scale.y(value), label: this.format(value) };
    });
  });

  protected readonly xTicks = computed(() => {
    const scale = this.scale();
    const at = (t: number) => new Date(t).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
    if (scale.minT === scale.maxT) {
      return [{ x: scale.x(scale.minT), label: at(scale.minT), anchor: 'middle' }];
    }
    return [
      { x: PAD.left, label: at(scale.minT), anchor: 'start' },
      { x: WIDTH - PAD.right, label: at(scale.maxT), anchor: 'end' },
    ];
  });

  protected diamond(p: Point): string {
    return `${p.x},${p.y - 6} ${p.x + 6},${p.y} ${p.x},${p.y + 6} ${p.x - 6},${p.y}`;
  }

  private format(value: number): string {
    const span = this.scale().maxV - this.scale().minV;
    return value.toFixed(span < 1 ? 3 : span < 10 ? 2 : 1);
  }
}
