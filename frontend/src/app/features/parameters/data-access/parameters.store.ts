import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  CapaDeadlines,
  CapaPolicy,
  InstrumentTypeCode,
  Parameters,
  SeverityCode,
} from '../domain/parameters.model';
import { ParametersApi } from './parameters.api';

interface ParametersError {
  status?: number;
  detail?: string;
}

/**
 * Estado da tela de parametrização (PRM-001).
 *
 * <p>Cada seção salva sozinha, contra o seu módulo. Um botão único de "salvar tudo" daria a
 * impressão de atomicidade que não existe: são cinco endpoints independentes, e uma falha no
 * terceiro deixaria os dois primeiros gravados.
 */
@Injectable()
export class ParametersStore {
  private readonly api = inject(ParametersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly parameters = signal<Parameters | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Seção que está gravando, para o botão certo ficar ocupado. */
  readonly saving = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  /** Quantos parâmetros estão configurados — o resto segue no comportamento padrão. */
  readonly configuredCount = computed(() => {
    const p = this.parameters();
    if (!p) {
      return 0;
    }
    return (
      (p.cleaning.expiresByTime ? 1 : 0) +
      (p.gas.derivesDueDate ? 1 : 0) +
      (Object.keys(p.calibration.monthsByType).length > 0 ? 1 : 0) +
      (Object.keys(p.capa.bySeverity).length > 0 ? 1 : 0)
    );
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .loadAll()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: parameters => this.parameters.set(parameters),
        error: () => this.error.set('Não foi possível carregar os parâmetros.'),
      });
  }

  saveCleaning(validityHours: number | null): void {
    this.run('cleaning', this.api.saveCleaning(validityHours), policy =>
      this.patch(p => ({ ...p, cleaning: policy })),
    );
  }

  saveGas(requalificationMonths: number | null): void {
    this.run('gas', this.api.saveGas(requalificationMonths), policy =>
      this.patch(p => ({ ...p, gas: policy })),
    );
  }

  saveCalibration(monthsByType: Partial<Record<InstrumentTypeCode, number>>): void {
    // Tipo sem valor sai do mapa: é assim que se volta ao vencimento vindo do certificado.
    const cleaned: Record<string, number> = {};
    for (const [type, months] of Object.entries(monthsByType)) {
      if (months) {
        cleaned[type] = months;
      }
    }
    this.run('calibration', this.api.saveCalibration(cleaned), policy =>
      this.patch(p => ({ ...p, calibration: policy })),
    );
  }

  saveCapa(bySeverity: Partial<Record<SeverityCode, CapaDeadlines | null>>): void {
    const cleaned: CapaPolicy['bySeverity'] = {};
    for (const [severity, deadlines] of Object.entries(bySeverity)) {
      if (deadlines) {
        cleaned[severity as SeverityCode] = deadlines;
      }
    }
    this.run('capa', this.api.saveCapa(cleaned), policy => this.patch(p => ({ ...p, capa: policy })));
  }

  saveSensory(maxScore: number): void {
    this.run('sensory', this.api.saveSensory(maxScore), policy =>
      this.patch(p => ({ ...p, sensory: policy })),
    );
  }

  private patch(update: (current: Parameters) => Parameters): void {
    const current = this.parameters();
    if (current) {
      this.parameters.set(update(current));
    }
  }

  private run<T>(
    section: string,
    call: import('rxjs').Observable<T>,
    apply: (value: T) => void,
  ): void {
    this.saving.set(section);
    this.actionError.set(null);
    call
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(null)))
      .subscribe({
        next: value => {
          apply(value);
          this.toast.success('Parâmetro salvo.');
        },
        error: (e: ParametersError) =>
          this.actionError.set(e.detail ?? 'Não foi possível salvar o parâmetro.'),
      });
  }
}
