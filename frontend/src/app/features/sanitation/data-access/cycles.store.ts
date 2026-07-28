import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { EquipmentApi } from '../../equipment/data-access/equipment.api';
import { Equipment } from '../../equipment/domain/equipment.model';
import { CleaningCycle, StartCycleRequest } from '../domain/cycle.model';
import { ProceduresApi } from './procedures.api';
import { CyclesApi } from './cycles.api';

/** Estado da tela de ciclos (CLN-003): listagem, POPs publicados, equipamentos e início. */
@Injectable()
export class CyclesStore {
  private readonly api = inject(CyclesApi);
  private readonly proceduresApi = inject(ProceduresApi);
  private readonly equipmentApi = inject(EquipmentApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<CleaningCycle[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  readonly canExecute = this.auth.hasPermission('sanitation.cycle.execute');
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  /** Códigos de POP publicados (distintos) para o seletor. */
  readonly publishedCodes = signal<string[]>([]);
  readonly equipment = signal<Equipment[]>([]);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar os ciclos.'),
      });
  }

  loadOptions(): void {
    forkJoin({ procedures: this.proceduresApi.list(), equipment: this.equipmentApi.list(0, 200) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ procedures, equipment }) => {
          this.publishedCodes.set([...new Set(procedures.filter(p => p.status === 'PUBLISHED').map(p => p.code))]);
          this.equipment.set(equipment.content);
        },
        error: () => this.actionError.set('Não foi possível carregar POPs/equipamentos.'),
      });
  }

  start(request: StartCycleRequest, onSuccess?: (id: string) => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.start(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ id }) => { this.toast.success('Ciclo iniciado.'); this.load(); onSuccess?.(id); },
        error: () => this.actionError.set('Não foi possível iniciar (POP publicado e equipamento válido?).'),
      });
  }
}
