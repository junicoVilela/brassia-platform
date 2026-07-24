import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { Equipment } from '../domain/equipment.model';
import { Maintenance, ScheduleMaintenanceRequest } from '../domain/maintenance.model';
import { EquipmentApi } from './equipment.api';
import { MaintenanceApi } from './maintenance.api';

/** Estado da tela de manutenção: escolher equipamento, listar janelas e agendar/cancelar. */
@Injectable()
export class MaintenanceStore {
  private readonly equipmentApi = inject(EquipmentApi);
  private readonly api = inject(MaintenanceApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly equipmentState = signal<Equipment[]>([]);
  private readonly selectedIdState = signal<string | null>(null);
  private readonly windowsState = signal<Maintenance[]>([]);

  readonly equipment = this.equipmentState.asReadonly();
  readonly selectedId = this.selectedIdState.asReadonly();
  readonly windows = this.windowsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !!this.selectedIdState() && !this.loading() && this.windows().length === 0);

  loadEquipment(): void {
    this.equipmentApi.list(0, 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.equipmentState.set(page.content),
        error: () => this.error.set('Não foi possível carregar os equipamentos.'),
      });
  }

  select(equipmentId: string | null): void {
    this.selectedIdState.set(equipmentId);
    this.windowsState.set([]);
    this.actionError.set(null);
    if (equipmentId) {
      this.loadWindows();
    }
  }

  private loadWindows(): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.list(id)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: windows => this.windowsState.set(windows),
        error: () => this.error.set('Não foi possível carregar as janelas.'),
      });
  }

  schedule(request: ScheduleMaintenanceRequest, onSuccess?: () => void): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.schedule(id, request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Manutenção agendada.');
          this.loadWindows();
        },
        error: () => this.actionError.set('Não foi possível agendar (equipamento indisponível no período ou dados inválidos).'),
      });
  }

  cancel(maintenanceId: string): void {
    const id = this.selectedIdState();
    if (!id) {
      return;
    }
    this.api.cancel(id, maintenanceId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toast.success('Janela de manutenção cancelada.'); this.loadWindows(); },
        error: () => this.actionError.set('Não foi possível cancelar a janela.'),
      });
  }
}
