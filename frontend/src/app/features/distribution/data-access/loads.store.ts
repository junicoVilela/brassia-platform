import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  DeliveryOutcome,
  Load,
  NOT_RECORDABLE_REASONS,
  NOT_SHIPPABLE_REASONS,
  ProofOfDelivery,
  SyncResult,
} from '../domain/load.model';
import { LoadsApi } from './loads.api';

interface ApiError {
  status?: number;
  error?: { code?: string; detail?: string; reasonCode?: string };
}

/**
 * Estado das cargas (LOG-001).
 *
 * <p>Depois de qualquer ação a carga aberta é <strong>relida</strong>: `frozen`, o volume carregado e o
 * roteiro são compostos no servidor, e uma cópia em cache mostraria botões de editar numa carga que
 * acabou de ser conferida.
 */
@Injectable()
export class LoadsStore {
  private readonly api = inject(LoadsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loads = signal<Load[]>([]);
  readonly selected = signal<Load | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly day = signal<string | null>(null);

  /**
   * Conflitos de sincronização esperando decisão.
   *
   * <p>Último-a-escrever-ganha descartaria em silêncio o registro de quem estava lá — ou o do
   * escritório —, então eles ficam aqui até alguém olhar.
   */
  readonly conflicts = signal<SyncResult[]>([]);

  /** As provas da carga aberta — original e correção, na ordem. */
  readonly proofs = signal<ProofOfDelivery[]>([]);

  /** Paradas já registradas: a tela some com o botão em vez de deixar o 409 explicar. */
  readonly recordedStops = computed(
    () => new Set(this.proofs().filter(p => !p.correctsProofId).map(p => p.stopId)),
  );

  /** Cargas que esperam conferência — a fila de quem tem a alçada de liberar. */
  readonly awaitingRelease = computed(
    () => this.loads().filter(l => l.status === 'PLANNED').length,
  );

  readonly onTheRoad = computed(() => this.loads().filter(l => l.status === 'IN_ROUTE').length);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.day())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: list => this.loads.set(list),
        error: (e: ApiError) => this.error.set(this.message(e, 'Não foi possível carregar as cargas.')),
      });
  }

  filterByDay(day: string | null): void {
    this.day.set(day);
    this.load();
  }

  open(load: Load): void {
    this.reload(load.id);
    this.reloadProofs(load.id);
  }

  close(): void {
    this.selected.set(null);
    this.proofs.set([]);
  }

  /**
   * Registra o que aconteceu na parada.
   *
   * <p>Sem consentimento não vai assinatura — e a entrega acontece do mesmo jeito. A coordenada, quando
   * há, é arredondada no servidor: a cheia não é guardada em lugar nenhum.
   */
  recordProof(load: Load, stopId: string, outcome: DeliveryOutcome, delivered: string[],
    collected: string[], note: string | null, consentedByName: string | null): void {
    this.saving.set(true);
    this.api
      .recordProof(load.id, stopId, {
        outcome,
        delivered,
        collected,
        note,
        signatureConsent: consentedByName
          ? {
              kind: 'SIGNATURE',
              storageKey: `assinatura/${load.id}/${stopId}`,
              consentedByName,
              purpose: 'comprovar a entrega',
            }
          : null,
        latitude: null,
        longitude: null,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Entrega registrada.');
          this.reloadProofs(load.id);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar.')),
      });
  }

  correctProof(load: Load, stopId: string, outcome: DeliveryOutcome, delivered: string[],
    collected: string[], reason: string): void {
    this.api
      .correctProof(stopId, { outcome, delivered, collected, reason })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // "Correção registrada", e não "entrega corrigida": a original continua de pé, e dizer o
          // contrário faria a tela prometer um apagamento que não houve.
          this.toast.success('Correção registrada. O registro anterior continua no histórico.');
          this.reloadProofs(load.id);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível corrigir.')),
      });
  }

  loadConflicts(): void {
    this.api
      .syncConflicts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.conflicts.set(list) });
  }

  private reloadProofs(loadId: string): void {
    this.api
      .proofsOfLoad(loadId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.proofs.set(list) });
  }

  plan(code: string, scheduledFor: string, capacityLiters: number): void {
    this.saving.set(true);
    this.api
      .plan({ code, scheduledFor, capacityLiters })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Carga criada.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível criar a carga.')),
      });
  }

  addStop(load: Load, customerId: string, customerName: string, sequence: number,
    windowFrom: string | null, windowTo: string | null): void {
    this.api
      .addStop(load.id, { customerId, customerName, sequence, windowFrom, windowTo })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(load.id),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível criar a parada.')),
      });
  }

  removeStop(load: Load, stopId: string): void {
    this.api
      .removeStop(load.id, stopId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(load.id),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível remover.')),
      });
  }

  loadContainer(load: Load, stopId: string, containerId: string): void {
    this.api
      .loadContainer(load.id, stopId, containerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(load.id),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível carregar.')),
      });
  }

  unloadContainer(load: Load, containerId: string): void {
    this.api
      .unloadContainer(load.id, containerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(load.id),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível descarregar.')),
      });
  }

  assign(load: Load, driverId: string, vehicle: string | null): void {
    this.api
      .assign(load.id, { driverId, vehicle })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload(load.id),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível atribuir.')),
      });
  }

  release(load: Load): void {
    this.api
      .release(load.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Carga conferida e liberada.');
          this.reload(load.id);
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível liberar.')),
      });
  }

  reopen(load: Load): void {
    this.api
      .reopen(load.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // A conferência cai junto, e a tela diz isso: senão o operador reabre achando que só
          // destravou a edição.
          this.toast.success('Carga reaberta. A conferência anterior foi desfeita.');
          this.reload(load.id);
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível reabrir.')),
      });
  }

  depart(load: Load): void {
    this.act(this.api.depart(load.id), load.id, 'Carga na rua.');
  }

  closeLoad(load: Load): void {
    this.act(this.api.close(load.id), load.id, 'Carga encerrada.');
  }

  cancel(load: Load): void {
    this.act(this.api.cancel(load.id), load.id, 'Carga cancelada.');
  }

  private act(call: ReturnType<LoadsApi['depart']>, id: string, mensagem: string): void {
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.toast.success(mensagem);
        this.reload(id);
        this.load();
      },
      error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível concluir.')),
    });
  }

  private reload(id: string): void {
    this.api
      .read(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: l => this.selected.set(l) });
  }

  /**
   * O motivo da recusa vira a frase de quem opera.
   *
   * <p>Keg vazio se enche, lote não liberado se cobra da qualidade, quarentena não se resolve hoje: sem
   * o motivo, as três viram "não deu" e o operador tenta outro keg até um passar.
   */
  private message(e: ApiError, fallback: string): string {
    const reason = e?.error?.reasonCode;
    if (reason) {
      const frase = NOT_SHIPPABLE_REASONS[reason] ?? NOT_RECORDABLE_REASONS[reason];
      if (frase) {
        return frase;
      }
    }
    return e?.error?.detail ?? fallback;
  }
}
