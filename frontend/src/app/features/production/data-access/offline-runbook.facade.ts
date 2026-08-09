import { Injectable, computed, inject } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { NetworkStatusService } from '../../../core/offline/network-status.service';
import { OfflineRunbook, OfflineRunbookStore } from '../../../core/offline/offline-runbook.store';
import { Batch } from '../domain/batch.model';

/**
 * Liga o roteiro offline à tela de lotes (PWA-001).
 *
 * <p>Existe para que a identidade — quem está logado, em qual cervejaria — não precise ser passada em
 * cada chamada pela página. O `OfflineRunbookStore` exige os dois em toda leitura e escrita **de
 * propósito**: sem eles a proteção viraria uma convenção que alguém esquece.
 */
@Injectable({ providedIn: 'root' })
export class OfflineRunbookFacade {
  private readonly store = inject(OfflineRunbookStore);
  private readonly auth = inject(AuthService);
  private readonly network = inject(NetworkStatusService);

  readonly online = this.network.online;
  readonly available = this.store.available;
  readonly savedCount = computed(() => this.store.count());

  isAvailable(batchId: string): boolean {
    return this.store.isAvailable(batchId);
  }

  savedAt(batchId: string): Date | null {
    return this.store.savedAt(batchId);
  }

  /**
   * Guarda o roteiro de um lote para uso sem rede.
   *
   * <p>**Só os campos do roteiro viajam para o disco.** A conversão explícita aqui não é mapeamento
   * burocrático: é o que garante que um campo novo em `Batch` — um custo, um responsável — não passe a
   * ser gravado no aparelho só porque a API começou a devolvê-lo.
   */
  save(batch: Batch): boolean {
    const user = this.auth.user();
    if (!user?.activeBrewery) {
      return false;
    }
    const runbook: OfflineRunbook = {
      batchId: batch.id,
      code: batch.code,
      recipeName: batch.recipeName,
      recipeVersion: batch.recipeVersion,
      volumeLiters: batch.volumeLiters,
      status: batch.status,
      startedAt: batch.startedAt,
      steps: batch.steps.map(step => ({
        id: step.id,
        sequence: step.sequence,
        type: step.type,
        label: step.label,
        status: step.status,
        completedAt: step.completedAt,
      })),
    };
    this.store.save(user.userId, user.activeBrewery.id, runbook);
    return true;
  }

  /** Lê o roteiro salvo, se ele for desta pessoa, desta cervejaria e recente. */
  read(batchId: string): OfflineRunbook | null {
    const user = this.auth.user();
    if (!user?.activeBrewery) {
      return null;
    }
    return this.store.read(user.userId, user.activeBrewery.id, batchId);
  }

  discard(batchId: string): void {
    this.store.discard(batchId);
  }
}
