import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { NodeType } from '../domain/genealogy.model';
import { Quarantine, QuarantineDetail } from '../domain/quarantine.model';
import { QuarantinesApi } from './quarantines.api';

interface QuarantineError {
  status?: number;
  code?: string;
  detail?: string;
  quarantineId?: string;
}

/**
 * Estado das quarentenas (FDS-002).
 *
 * <p>O detalhe é sempre recarregado do servidor, nunca montado a partir da lista: o alcance é
 * derivado do grafo no momento da pergunta, e guardá-lo aqui recriaria no frontend a mesma cópia
 * envelhecida que o backend recusa a manter.
 */
@Injectable()
export class QuarantinesStore {
  private readonly api = inject(QuarantinesApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly quarantines = signal<Quarantine[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly onlyOpen = signal(true);

  readonly detail = signal<QuarantineDetail | null>(null);
  readonly detailLoading = signal(false);
  /** O que está gravando (`open` ou `release:<id>`), para o botão certo ficar ocupado. */
  readonly saving = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly openCount = computed(
    () => this.quarantines().filter(quarantine => quarantine.status === 'OPEN').length,
  );

  /** Nós alcançados só por intenção: bloqueiam, mas não são fato registrado. */
  readonly suspectedCount = computed(
    () => (this.detail()?.affected ?? []).filter(affected => affected.suspected).length,
  );

  load(onlyOpen = this.onlyOpen()): void {
    this.onlyOpen.set(onlyOpen);
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(onlyOpen)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: quarantines => this.quarantines.set(quarantines),
        error: () => this.error.set('Não foi possível carregar as quarentenas.'),
      });
  }

  /** Abre o detalhe; o mesmo id duas vezes fecha, porque a linha funciona como acordeão. */
  select(id: string): void {
    if (this.detail()?.quarantine.id === id) {
      this.detail.set(null);
      return;
    }
    this.detail.set(null);
    this.detailLoading.set(true);
    this.api
      .detail(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.detailLoading.set(false)))
      .subscribe({
        next: detail => this.detail.set(detail),
        error: () => this.actionError.set('Não foi possível carregar o alcance desta quarentena.'),
      });
  }

  open(nodeType: NodeType, nodeId: string, reason: string): void {
    this.run('open', this.api.open(nodeType, nodeId, reason), 'Quarentena aberta.');
  }

  release(id: string, justification: string): void {
    this.run(`release:${id}`, this.api.release(id, justification), 'Quarentena liberada.');
  }

  private run<T>(key: string, call: Observable<T>, message: string): void {
    this.saving.set(key);
    this.actionError.set(null);
    call.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(null))).subscribe({
      next: () => {
        this.toast.success(message);
        this.detail.set(null);
        this.load();
      },
      error: (e: QuarantineError) => this.actionError.set(this.messageFor(e)),
    });
  }

  private messageFor(e: QuarantineError): string {
    if (e.code === 'already_quarantined') {
      // Recusa de propósito: duas quarentenas do mesmo nó partiriam a investigação em duas.
      return 'Este item já está em quarentena. Abra a que existe em vez de criar outra.';
    }
    if (e.code === 'unknown_node') {
      return 'Este nó não existe nesta cervejaria.';
    }
    if (e.code === 'unknown_quarantine') {
      return 'Esta quarentena não existe mais.';
    }
    if (e.status === 403) {
      return 'Liberar quarentena é alçada própria, separada da de abrir.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
