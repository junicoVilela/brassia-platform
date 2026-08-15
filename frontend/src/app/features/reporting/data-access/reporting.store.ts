import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchReport } from '../domain/batch-report.model';
import { BatchOption, ReportingApi } from './reporting.api';

interface ReportError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado do relatório do lote (RPT-001).
 *
 * <p>O relatório é sempre relido: ele é derivado, e o mesmo lote responde diferente depois de um
 * envase ou de uma medição. Guardar o último em memória faria a tela mostrar um documento com data
 * de ontem sem avisar.
 */
@Injectable()
export class ReportingStore {
  private readonly api = inject(ReportingApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly batches = signal<BatchOption[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly selected = signal<BatchReport | null>(null);
  readonly selectedLoading = signal(false);
  readonly exporting = signal(false);

  /** Verdadeiro quando o lote teve medição e nenhuma saiu da faixa. */
  readonly qualityClean = computed(() => {
    const quality = this.selected()?.quality;
    return quality !== undefined && !quality.unmeasured && quality.outOfSpec.length === 0;
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .batches()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: batches => this.batches.set(batches),
        error: () => this.error.set('Não foi possível carregar os lotes.'),
      });
  }

  select(batchId: string): void {
    if (this.selected()?.batchId === batchId) {
      this.selected.set(null);
      return;
    }
    this.selected.set(null);
    this.error.set(null);
    this.selectedLoading.set(true);
    this.api
      .ofBatch(batchId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.selectedLoading.set(false)))
      .subscribe({
        next: report => this.selected.set(report),
        error: (e: ReportError) => this.error.set(this.messageFor(e)),
      });
  }

  /**
   * Exporta e baixa.
   *
   * <p>Passa pelo POST em vez de salvar o que já está na tela: é a chamada que deixa o rastro na
   * auditoria, e baixar o documento sem ela produziria um arquivo idêntico sem registro nenhum de
   * que ele saiu.
   */
  exportReport(batchId: string): void {
    this.exporting.set(true);
    this.error.set(null);
    this.api
      .export(batchId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.exporting.set(false)))
      .subscribe({
        next: report => {
          this.selected.set(report);
          this.download(report);
          this.toast.success('Relatório exportado. A exportação ficou registrada na auditoria.');
        },
        error: (e: ReportError) => this.error.set(this.messageFor(e)),
      });
  }

  /**
   * Exporta o documento impresso.
   *
   * <p>Não recarrega o relatório em tela: o PDF é o mesmo dossiê que já está aberto, e substituir o
   * estado por causa de um download faria a tela piscar sem motivo.
   */
  exportPdf(batchId: string, batchCode: string): void {
    this.exporting.set(true);
    this.error.set(null);
    this.api
      .exportPdf(batchId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.exporting.set(false)))
      .subscribe({
        next: blob => {
          this.saveAs(blob, `relatorio-${batchCode}.pdf`);
          this.toast.success('PDF exportado. A exportação ficou registrada na auditoria.');
        },
        error: (e: ReportError) => this.error.set(this.messageFor(e)),
      });
  }

  private download(report: BatchReport): void {
    this.saveAs(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' }),
      `relatorio-${report.batchCode}.json`);
  }

  private saveAs(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private messageFor(e: ReportError): string {
    if (e.code === 'unknown_batch' || e.status === 404) {
      return 'Este lote não existe nesta cervejaria.';
    }
    if (e.status === 403) {
      // Exportar tem alçada própria: ler na tela não dá direito de levar o documento embora.
      return 'Exportar o relatório é alçada própria, separada da de consultá-lo.';
    }
    return e.detail ?? 'Não foi possível carregar o relatório.';
  }
}
