import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { IngredientsApi } from '../../catalog/data-access/ingredients.api';
import { Ingredient } from '../../catalog/domain/ingredient.model';
import { SuppliersApi } from '../../purchasing/data-access/suppliers.api';
import { Supplier } from '../../purchasing/domain/supplier.model';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  LotProperty,
  RecordLotPropertyRequest,
  RecordMovementRequest,
  ReceiveStockLotRequest,
  ReserveStockRequest,
  ReserveStockResult,
  StockBalance,
  StockLot,
  StockMovement,
} from '../domain/stock-lot.model';
import { InventoryApi } from './inventory.api';

/** Estado do estoque: lotes recebidos + catálogos de apoio (ingredientes, fornecedores). */
@Injectable()
export class InventoryStore {
  private readonly api = inject(InventoryApi);
  private readonly ingredientsApi = inject(IngredientsApi);
  private readonly suppliersApi = inject(SuppliersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly lotsState = signal<StockLot[]>([]);
  private readonly ingredientsState = signal<Ingredient[]>([]);
  private readonly suppliersState = signal<Supplier[]>([]);

  readonly lots = this.lotsState.asReadonly();
  readonly ingredients = this.ingredientsState.asReadonly();
  readonly suppliers = this.suppliersState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.lots().length === 0);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  /** Resultado/erro da última reserva FEFO. */
  readonly reservation = signal<ReserveStockResult | null>(null);
  readonly reservationError = signal<string | null>(null);
  readonly reserving = signal(false);

  /** Lote selecionado para ver/registrar movimentos, seu saldo e ledger. */
  readonly movementsLotId = signal<string | null>(null);
  readonly balance = signal<StockBalance | null>(null);
  readonly movements = signal<StockMovement[]>([]);
  readonly movementsLoading = signal(false);
  readonly movementError = signal<string | null>(null);
  readonly recording = signal(false);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: lots => this.lotsState.set(lots),
        error: () => this.error.set('Não foi possível carregar os lotes de estoque.'),
      });
    this.ingredientsApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.ingredientsState.set(page.content), error: () => {} });
    this.suppliersApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: items => this.suppliersState.set(items), error: () => {} });
  }

  receive(request: ReceiveStockLotRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.receive(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Lote recebido.'); this.load(); },
        error: () => this.actionError.set('Não foi possível receber o lote (ingrediente/fornecedor inválido ou dados incorretos).'),
      });
  }

  reserve(request: ReserveStockRequest, onSuccess?: () => void): void {
    this.reserving.set(true);
    this.reservationError.set(null);
    this.reservation.set(null);
    this.api.reserve(request)
      .pipe(finalize(() => this.reserving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => { this.reservation.set(result); onSuccess?.(); this.toast.success('Estoque reservado.'); this.load(); },
        error: (err: { status?: number }) =>
          this.reservationError.set(err?.status === 409
            ? 'Disponível insuficiente para reservar (FEFO).'
            : 'Não foi possível reservar o estoque.'),
      });
  }

  /** Abre (ou fecha) o painel de movimentos de um lote e carrega saldo + ledger. */
  showMovements(lotId: string): void {
    if (this.movementsLotId() === lotId) {
      this.movementsLotId.set(null);
      return;
    }
    this.movementsLotId.set(lotId);
    this.movementError.set(null);
    this.refreshMovements(lotId);
  }

  recordMovement(lotId: string, request: RecordMovementRequest, onSuccess?: () => void): void {
    this.recording.set(true);
    this.movementError.set(null);
    this.api.recordMovement(lotId, request)
      .pipe(finalize(() => this.recording.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Movimento registrado.'); this.refreshMovements(lotId); },
        error: (err: { status?: number }) =>
          this.movementError.set(err?.status === 409
            ? 'Saldo insuficiente para a saída.'
            : 'Não foi possível registrar o movimento.'),
      });
  }

  private refreshMovements(lotId: string): void {
    this.movementsLoading.set(true);
    this.balance.set(null);
    this.movements.set([]);
    this.api.balance(lotId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: b => this.balance.set(b), error: () => {} });
    this.api.movements(lotId)
      .pipe(finalize(() => this.movementsLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: m => this.movements.set(m), error: () => this.movementError.set('Não foi possível carregar os movimentos.') });
  }

  /** Lote selecionado para ver/registrar valores medidos (COA) — STK-005. */
  readonly propertiesLotId = signal<string | null>(null);
  readonly properties = signal<LotProperty[]>([]);
  readonly propertiesLoading = signal(false);
  readonly propertyError = signal<string | null>(null);
  readonly recordingProperty = signal(false);

  /** Abre (ou fecha) o painel de valores de um lote. */
  showProperties(lotId: string): void {
    if (this.propertiesLotId() === lotId) {
      this.propertiesLotId.set(null);
      return;
    }
    this.propertiesLotId.set(lotId);
    this.propertyError.set(null);
    this.refreshProperties(lotId);
  }

  recordProperty(lotId: string, request: RecordLotPropertyRequest, onSuccess?: () => void): void {
    this.recordingProperty.set(true);
    this.propertyError.set(null);
    this.api.recordProperty(lotId, request)
      .pipe(finalize(() => this.recordingProperty.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Valor vinculado ao lote.'); this.refreshProperties(lotId); },
        error: (err: { status?: number }) =>
          this.propertyError.set(err?.status === 409
            ? 'Essa propriedade já foi registrada para o lote (imutável).'
            : 'Não foi possível vincular o valor.'),
      });
  }

  private refreshProperties(lotId: string): void {
    this.propertiesLoading.set(true);
    this.properties.set([]);
    this.api.properties(lotId)
      .pipe(finalize(() => this.propertiesLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: p => this.properties.set(p), error: () => this.propertyError.set('Não foi possível carregar os valores.') });
  }

  ingredientName(id: string): string {
    return this.ingredientsState().find(i => i.id === id)?.name ?? id;
  }

  supplierName(id: string): string {
    return this.suppliersState().find(s => s.id === id)?.name ?? id;
  }
}
