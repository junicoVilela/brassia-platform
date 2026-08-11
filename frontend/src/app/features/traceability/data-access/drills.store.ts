import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { DrillReport, RecallDrill, DrillCapaAction } from '../domain/drill.model';
import { NodeType } from '../domain/genealogy.model';
import { DrillsApi } from './drills.api';

interface DrillError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado dos simulados de recall (FDS-004).
 *
 * <p>O relatório vem do servidor a cada abertura porque metade dele é derivada do grafo enquanto o
 * simulado corre — e é assim que ele mostra o alvo se movendo, quando alguém expede no meio do
 * exercício. Depois de encerrado, o mesmo endpoint devolve os números congelados.
 */
@Injectable()
export class DrillsStore {
  private readonly api = inject(DrillsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly drills = signal<RecallDrill[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly report = signal<DrillReport | null>(null);
  readonly reportLoading = signal(false);
  readonly saving = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly running = computed(() => this.drills().filter(drill => drill.status === 'RUNNING'));

  /** Média dos percentuais localizados dos simulados encerrados — a tendência da casa. */
  readonly averageCoverage = computed(() => {
    const measured = this.drills()
      .map(drill => drill.locatedPercent)
      .filter((percent): percent is number => percent !== null);
    if (measured.length === 0) {
      return null;
    }
    return Math.round(measured.reduce((total, percent) => total + percent, 0) / measured.length);
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: drills => this.drills.set(drills),
        error: () => this.error.set('Não foi possível carregar os simulados.'),
      });
  }

  select(id: string): void {
    if (this.report()?.drill.id === id) {
      this.report.set(null);
      return;
    }
    this.report.set(null);
    this.reloadReport(id);
  }

  start(nodeType: NodeType, nodeId: string, note: string | null): void {
    this.run('start', this.api.start(nodeType, nodeId, note), 'Simulado iniciado.', null);
  }

  finish(
    id: string,
    unitsLocated: number,
    summary: string,
    actions: string | null,
    nonConformityId: string | null = null,
    capaActions: DrillCapaAction[] = [],
  ): void {
    this.run(
      `finish:${id}`,
      this.api.finish(id, unitsLocated, summary, actions, nonConformityId, capaActions),
      nonConformityId ? 'Simulado encerrado e ações abertas no CAPA.' : 'Simulado encerrado.',
      id,
    );
  }

  private run<T>(key: string, call: Observable<T>, message: string, reload: string | null): void {
    this.saving.set(key);
    this.actionError.set(null);
    call.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(null))).subscribe({
      next: () => {
        this.toast.success(message);
        this.load();
        if (reload) {
          this.reloadReport(reload);
        }
      },
      error: (e: DrillError) => this.actionError.set(this.messageFor(e)),
    });
  }

  private reloadReport(id: string): void {
    this.reportLoading.set(true);
    this.api
      .report(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.reportLoading.set(false)))
      .subscribe({
        next: report => this.report.set(report),
        error: () => this.actionError.set('Não foi possível carregar o relatório.'),
      });
  }

  private messageFor(e: DrillError): string {
    if (e.code === 'unknown_node') {
      return 'Este nó não existe nesta cervejaria.';
    }
    if (e.code === 'unknown_drill') {
      return 'Este simulado não existe mais.';
    }
    if (e.status === 400) {
      // O caso real: dizer que localizou mais unidades do que saíram da fábrica.
      return 'Confira as unidades localizadas: não dá para localizar mais do que saiu.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
