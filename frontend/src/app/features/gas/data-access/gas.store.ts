import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  ConnectGasRequest,
  GasBlocker,
  GasComponent,
  GasConnection,
  GasConnectionDetail,
  GasCylinder,
  RegisterCylinderRequest,
} from '../domain/gas.model';
import { EquipmentOption, GasApi } from './gas.api';

/** Corpo Problem Details da recusa de conexão, como o backend o publica. */
interface ConnectError {
  status?: number;
  error?: { code?: string; blockers?: GasBlocker[] };
}

/** Estado da rede de gás (GAS-001). */
@Injectable()
export class GasStore {
  private readonly api = inject(GasApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly cylinders = signal<GasCylinder[]>([]);
  readonly components = signal<GasComponent[]>([]);
  readonly connections = signal<GasConnection[]>([]);
  readonly equipment = signal<EquipmentOption[]>([]);
  readonly detail = signal<GasConnectionDetail | null>(null);
  readonly openDetailOf = signal<string | null>(null);

  readonly onlyOpen = signal(true);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  /** Impedimentos da última tentativa de conexão, mostrados todos de uma vez. */
  readonly connectBlockers = signal<GasBlocker[]>([]);

  readonly empty = computed(() => !this.loading() && !this.error() && this.connections().length === 0);

  /** Só cilindro sem impedimento entra na montagem da linha. */
  readonly allocatableCylinders = computed(() => this.cylinders().filter(c => c.allocatable));
  readonly regulators = computed(() => this.components().filter(c => c.kind === 'REGULATOR' && c.active));
  readonly manifolds = computed(() => this.components().filter(c => c.kind === 'MANIFOLD' && c.active));
  readonly expiredCylinders = computed(() => this.cylinders().filter(c => c.expired));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.connections(this.onlyOpen())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: connections => this.connections.set(connections),
        error: () => this.error.set('Não foi possível carregar as conexões de gás.'),
      });
    this.loadCylinders();
  }

  loadCylinders(): void {
    this.api.cylinders()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: c => this.cylinders.set(c), error: () => this.cylinders.set([]) });
  }

  loadReferences(): void {
    this.api.components()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: c => this.components.set(c), error: () => this.components.set([]) });
    this.api.equipment()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: e => this.equipment.set(e), error: () => this.equipment.set([]) });
  }

  toggleOnlyOpen(onlyOpen: boolean): void {
    this.onlyOpen.set(onlyOpen);
    this.load();
  }

  registerCylinder(request: RegisterCylinderRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.registerCylinder(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Cilindro cadastrado.');
          this.loadCylinders();
        },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Código de cilindro já usado.'
            : 'Não foi possível cadastrar o cilindro (medidas inválidas).'),
      });
  }

  setBlock(cylinderId: string, blocked: boolean, reason: string | null): void {
    this.api.setCylinderBlock(cylinderId, blocked, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(blocked
            ? 'Cilindro bloqueado.'
            : 'Cilindro desbloqueado (a requalificação vencida continua impedindo o uso).');
          this.loadCylinders();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Desconecte o cilindro antes de bloqueá-lo.'
            : 'Não foi possível alterar o bloqueio (motivo é obrigatório).'),
      });
  }

  requalify(cylinderId: string, dueOn: string): void {
    this.api.requalify(cylinderId, dueOn)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Requalificação registrada.');
          this.loadCylinders();
        },
        error: () => this.toast.error('A nova requalificação precisa vencer no futuro.'),
      });
  }

  refill(cylinderId: string, contentKg: number): void {
    this.api.refill(cylinderId, contentKg)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Cilindro recarregado.');
          this.loadCylinders();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Cilindro conectado ou bloqueado não é recarregado.'
            : 'A massa informada passa da capacidade do cilindro.'),
      });
  }

  connect(request: ConnectGasRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.connectBlockers.set([]);
    this.actionError.set(null);
    this.api.connect(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Linha montada. Registre o teste de vazamento para liberá-la.');
          this.load();
        },
        error: (err: ConnectError) => {
          if (err?.error?.blockers?.length) {
            this.connectBlockers.set(err.error.blockers);
            return;
          }
          this.actionError.set('Não foi possível montar a linha (cilindro ou ponto de uso inválido).');
        },
      });
  }

  leakTest(connectionId: string, passed: boolean, method: string, pressureDropBar: number,
      note: string | null): void {
    this.api.leakTest(connectionId, passed, method, pressureDropBar, note)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(passed ? 'Teste aprovado; a linha está liberada.' : 'Teste reprovado; linha bloqueada.');
          this.refresh(connectionId);
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Conexão desconectada não aceita teste.'
            : 'Teste reprovado exige observação.'),
      });
  }

  pressure(connectionId: string, bar: number, tempC: number | null): void {
    this.api.pressure(connectionId, bar, tempC)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          if (result.overPressure) {
            this.toast.error('Sobrepressão: a leitura foi registrada e a linha foi bloqueada.');
          } else {
            this.toast.success('Leitura registrada.');
          }
          this.refresh(connectionId);
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Só linha servindo registra pressão.'
            : 'Não foi possível registrar a leitura.'),
      });
  }

  consumption(connectionId: string, kg: number, reason: string | null): void {
    this.api.consumption(connectionId, kg, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Consumo registrado.');
          this.refresh(connectionId);
          this.loadCylinders();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Só linha servindo consome gás.'
            : 'O consumo informado é maior que o conteúdo do cilindro.'),
      });
  }

  disconnect(connectionId: string, reason: string): void {
    this.api.disconnect(connectionId, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Linha desconectada; o cilindro voltou ao estoque.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Linha já desconectada.'
            : 'Não foi possível desconectar a linha.'),
      });
  }

  toggleDetail(connectionId: string): void {
    if (this.openDetailOf() === connectionId) {
      this.openDetailOf.set(null);
      this.detail.set(null);
      return;
    }
    this.openDetailOf.set(connectionId);
    this.loadDetail(connectionId);
  }

  /** Recarrega a lista e, se aberto, o detalhe da conexão tocada. */
  private refresh(connectionId: string): void {
    this.load();
    if (this.openDetailOf() === connectionId) {
      this.loadDetail(connectionId);
    }
  }

  private loadDetail(connectionId: string): void {
    this.api.connection(connectionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => this.detail.set(detail),
        error: () => this.toast.error('Não foi possível carregar o histórico da linha.'),
      });
  }
}
