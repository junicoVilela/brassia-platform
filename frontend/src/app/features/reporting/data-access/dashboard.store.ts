import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import {
  Dashboard,
  IndicatorGroup,
  OperationalIndicator,
} from '../domain/dashboard.model';
import { ReportingApi } from './reporting.api';

interface DashboardError {
  status?: number;
  detail?: string;
}

/** Período em datas locais (`YYYY-MM-DD`), que é como se pede um painel. */
export interface Period {
  from: string;
  to: string;
}

/** Um bloco do painel, já com os indicadores dele. */
export interface DashboardSection {
  group: IndicatorGroup;
  indicators: OperationalIndicator[];
}

/**
 * Estado do painel operacional (RPT-002).
 *
 * <p>Os blocos são montados na ordem em que o servidor mandou, e não numa ordem escolhida aqui: o
 * backend já ordena por grupo e por código, e reordenar de novo faria os cartões trocarem de lugar
 * entre uma tela e outra. Painel que embaralha cansa quem o usa todo dia.
 *
 * <p>O fim do período é inclusivo para quem pede e exclusivo para quem calcula — mesma conversão da
 * tela de utilidades, e pelo mesmo motivo: quem escolhe "até 31/08" quer o dia 31 inteiro.
 */
@Injectable()
export class DashboardStore {
  private readonly api = inject(ReportingApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly dashboard = signal<Dashboard | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly period = signal<Period>(lastDays(30));

  readonly sections = computed<DashboardSection[]>(() => {
    const indicators = this.dashboard()?.indicators ?? [];
    const sections: DashboardSection[] = [];
    for (const indicator of indicators) {
      const current = sections.at(-1);
      if (current?.group === indicator.group) {
        current.indicators.push(indicator);
      } else {
        sections.push({ group: indicator.group, indicators: [indicator] });
      }
    }
    return sections;
  });

  /** Indicadores com ressalva — o painel não os esconde, destaca. */
  readonly withGap = computed(
    () => this.dashboard()?.indicators.filter(indicator => indicator.gap !== null) ?? [],
  );

  load(period: Period = this.period()): void {
    if (period.from > period.to) {
      // O backend também recusa; barrar aqui evita uma ida ao servidor para ouvir isso.
      this.error.set('O início do período é depois do fim.');
      return;
    }
    this.period.set(period);
    this.loading.set(true);
    this.error.set(null);
    this.api
      .dashboard(startOf(period.from), startOf(nextDay(period.to)))
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: dashboard => this.dashboard.set(dashboard),
        error: (e: DashboardError) => {
          // Painel pela metade seria indistinguível de um painel normal: some inteiro.
          this.dashboard.set(null);
          this.error.set(this.messageFor(e));
        },
      });
  }

  private messageFor(e: DashboardError): string {
    if (e.status === 403) {
      return 'Consultar o painel operacional é alçada própria.';
    }
    return e.detail ?? 'Não foi possível carregar o painel do período.';
  }
}

/** Um período que termina hoje e começa `days` dias antes, em datas locais. */
export function lastDays(days: number): Period {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - days);
  return { from: asDate(from), to: asDate(to) };
}

function asDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** Meia-noite local do dia: a fábrica opera no fuso dela, não em UTC. */
function startOf(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day).toISOString();
}

function nextDay(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  const next = new Date(year, month - 1, day);
  next.setDate(next.getDate() + 1);
  return asDate(next);
}
