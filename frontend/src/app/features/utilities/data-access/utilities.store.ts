import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { UtilityReport } from '../domain/utility-indicator.model';
import { UtilitiesApi } from './utilities.api';

interface UtilityError {
  status?: number;
  detail?: string;
}

/** Período em datas locais (`YYYY-MM-DD`), que é como se pede um relatório. */
export interface Period {
  from: string;
  to: string;
}

/**
 * Estado do indicador de utilidades (UTL-001).
 *
 * <p>Nada é guardado entre consultas: o relatório é derivado no servidor, e uma cópia local
 * envelheceria assim que um ciclo fosse lançado com atraso — que é justamente o motivo de o
 * indicador não ter tabela.
 *
 * <p>O fim do período é <strong>inclusivo para quem pede e exclusivo para quem calcula</strong>:
 * quem escolhe "até 31/08" quer o dia 31 inteiro, e o backend corta em `to`. A conversão é feita
 * aqui, uma vez, para a tela não ter de explicar isso ao usuário.
 */
@Injectable()
export class UtilitiesStore {
  private readonly api = inject(UtilitiesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly report = signal<UtilityReport | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly period = signal<Period>(lastDays(30));

  /** Verdadeiro quando não houve envase: o consumo existe, o por litro não. */
  readonly withoutPackaging = computed(() => {
    const report = this.report();
    return report !== null && report.packagedLiters <= 0;
  });

  /** Utilidades cujo número não fala pela fábrica inteira — cobertura parcial ou não declarada. */
  readonly partiallyMeasured = computed(
    () => this.report()?.indicators.filter(indicator => !indicator.fullyMeasured) ?? [],
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
      .indicators(startOf(period.from), startOf(nextDay(period.to)))
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: report => this.report.set(report),
        error: (e: UtilityError) => {
          this.report.set(null);
          this.error.set(this.messageFor(e));
        },
      });
  }

  private messageFor(e: UtilityError): string {
    if (e.status === 403) {
      return 'Consultar o consumo de utilidades é alçada própria.';
    }
    return e.detail ?? 'Não foi possível carregar o consumo do período.';
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

/** Meia-noite local do dia, em instante — o consumo é lido no fuso de quem opera a fábrica. */
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
