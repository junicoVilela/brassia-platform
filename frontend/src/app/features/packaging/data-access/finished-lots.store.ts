import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { FinishedLot, RecordShipmentRequest, Shipment } from '../domain/finished-lot.model';
import { FinishedLotsApi } from './finished-lots.api';

interface ShipmentError {
  status?: number;
  code?: string;
  detail?: string;
  shipment?: { available: number };
  blockers?: { code: string; message: string }[];
}

/** Um lote com o que já saiu dele — é o par que a tela precisa mostrar junto. */
export interface LotWithShipments {
  readonly lot: FinishedLot;
  readonly shipments: readonly Shipment[];
  readonly shipped: number;
  /** Unidades que ainda não têm destino registrado: o que a fábrica acha que ainda tem. */
  readonly remaining: number;
}

/**
 * Estado dos lotes de produto acabado e das suas saídas (TRC-001-D).
 *
 * <p>O saldo por lote é calculado aqui e não vem do servidor porque é aritmética de duas listas que
 * a tela já carregou; o que o servidor guarda — e recusa violar — é a regra: não se expede mais do
 * que o lote tem.
 */
@Injectable()
export class FinishedLotsStore {
  private readonly api = inject(FinishedLotsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly lots = signal<FinishedLot[]>([]);
  readonly shipments = signal<Shipment[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly saving = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly rows = computed<LotWithShipments[]>(() => {
    const byLot = new Map<string, Shipment[]>();
    for (const shipment of this.shipments()) {
      byLot.set(shipment.finishedLotId, [...(byLot.get(shipment.finishedLotId) ?? []), shipment]);
    }
    return this.lots().map(lot => {
      const shipments = byLot.get(lot.id) ?? [];
      const shipped = shipments.reduce((total, shipment) => total + shipment.units, 0);
      return { lot, shipments, shipped, remaining: lot.units - shipped };
    });
  });

  /** Lotes sem nenhuma saída: num recall, é o que não se sabe onde está. */
  readonly withoutDestination = computed(() => this.rows().filter(row => row.shipments.length === 0));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ lots: this.api.lots(), shipments: this.api.shipments() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ lots, shipments }) => {
          this.lots.set(lots);
          this.shipments.set(shipments);
        },
        error: () => this.error.set('Não foi possível carregar os lotes de produto acabado.'),
      });
  }

  ship(request: RecordShipmentRequest): void {
    this.saving.set(request.finishedLotId);
    this.actionError.set(null);
    this.api
      .ship(request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(null)))
      .subscribe({
        next: shipment => {
          this.shipments.set([shipment, ...this.shipments()]);
          this.toast.success('Expedição registrada.');
        },
        error: (e: ShipmentError) => this.actionError.set(this.messageFor(e)),
      });
  }

  /**
   * Estorna e recarrega.
   *
   * <p>Recarrega em vez de remendar a lista local: o estorno muda o saldo sem destino do lote, e um
   * saldo remendado na tela divergiria do que o servidor calcula na próxima abertura.
   */
  reverseShipment(shipmentId: string, reason: string): void {
    this.actionError.set(null);
    this.api
      .reverseShipment(shipmentId, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Expedição estornada.');
          this.load();
        },
        error: (e: ShipmentError) => this.actionError.set(this.messageFor(e)),
      });
  }

  private messageFor(e: ShipmentError): string {
    if (e.code === 'shipment_exceeds_lot') {
      return `Este lote só tem ${e.shipment?.available ?? 0} unidade(s) sem destino registrado.`;
    }
    if (e.code === 'packaging_blocked') {
      // O bloqueio da quarentena chega aqui: a contenção alcança a saída (FDS-002).
      return e.blockers?.[0]?.message ?? 'A saída deste lote está bloqueada.';
    }
    return e.detail ?? 'Não foi possível registrar a expedição.';
  }
}
