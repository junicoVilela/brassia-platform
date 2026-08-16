import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  Container,
  ContainerFill,
  ContainerIdentifier,
  ContainerKind,
  ContainerLocation,
  ContainerState,
  FILL_REFUSAL_REASONS,
  IdentifierTechnology,
  LocationKind,
  NOT_FILLABLE_REASONS,
  Ownership,
} from '../domain/container.model';
import { ContainersApi } from './containers.api';

interface ApiError {
  status?: number;
  error?: { code?: string; detail?: string; reasonCode?: string };
}

/**
 * Estado dos contêineres (CON-001).
 *
 * <p>Depois de qualquer movimento a lista é <strong>relida</strong>: `fillable` é composto no servidor
 * a partir de avaria, estado e inspeção, e uma lista em cache mostraria como disponível um keg que
 * acabou de voltar sujo.
 */
@Injectable()
export class ContainersStore {
  private readonly api = inject(ContainersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly containers = signal<Container[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly filter = signal<ContainerState | null>(null);

  readonly selected = signal<Container | null>(null);
  readonly identifiers = signal<ContainerIdentifier[]>([]);

  /** O histórico do vasilhame aberto: o que já esteve dentro, e por onde ele andou. */
  readonly fills = signal<ContainerFill[]>([]);
  readonly locations = signal<ContainerLocation[]>([]);
  readonly historyOf = signal<Container | null>(null);

  /** O que a leitura de um código encontrou — e só isso: ler não autoriza nada. */
  readonly scanned = signal<Container | null>(null);

  /** Quantos estão prontos para encher agora. É a pergunta que o encarregado faz de manhã. */
  readonly readyToFill = computed(() => this.containers().filter(c => c.fillable).length);

  /** O que voltou e ainda ninguém lavou. Fila de trabalho, e não estoque. */
  readonly awaitingCleaning = computed(
    () => this.containers().filter(c => c.state === 'RETURNED').length,
  );

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.filter())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: list => this.containers.set(list),
        error: (e: ApiError) =>
          this.error.set(this.message(e, 'Não foi possível carregar os contêineres.')),
      });
  }

  filterBy(state: ContainerState | null): void {
    this.filter.set(state);
    this.load();
  }

  register(code: string, kind: ContainerKind, nominalCapacityLiters: number,
    ownership: Ownership): void {
    this.saving.set(true);
    this.api
      .register({ code, kind, nominalCapacityLiters, ownership })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          // "Cadastrado, falta inspecionar": dizer só "cadastrado" deixaria o operador achar que o keg
          // já serve — e ele não serve até alguém atestar a inspeção.
          this.toast.success('Contêiner cadastrado. Ele só pode ser enchido depois da inspeção.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível cadastrar.')),
      });
  }

  move(container: Container, to: ContainerState): void {
    this.api
      .move(container.id, to)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível mover.')),
      });
  }

  inspect(container: Container, validUntil: string, note: string | null): void {
    this.saving.set(true);
    this.api
      .inspect(container.id, {
        performedAt: new Date().toISOString(),
        // O campo é `date`; o servidor espera um instante — meia-noite local em UTC, e não a string crua.
        validUntil: new Date(`${validUntil}T23:59:59`).toISOString(),
        note,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Inspeção registrada.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar.')),
      });
  }

  markCondition(container: Container, condemned: boolean): void {
    this.api
      .condition(container.id, condemned)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar.')),
      });
  }

  retire(container: Container, reason: string): void {
    this.api
      .retire(container.id, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Contêiner baixado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível dar baixa.')),
      });
  }

  fill(container: Container, finishedLotId: string, volumeLiters: number): void {
    this.saving.set(true);
    this.api
      .fill(container.id, { finishedLotId, volumeLiters })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Enchimento registrado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível encher.')),
      });
  }

  emptyFill(container: Container): void {
    this.api
      .emptyFill(container.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // "Período fechado", e não "conteúdo apagado": o vínculo continua respondendo pelo passado.
          this.toast.success('Conteúdo encerrado. O registro do que esteve dentro continua.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível esvaziar.')),
      });
  }

  openHistory(container: Container): void {
    this.historyOf.set(container);
    this.fills.set([]);
    this.locations.set([]);
    this.api
      .fills(container.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.fills.set(list) });
    this.api
      .locations(container.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.locations.set(list) });
  }

  closeHistory(): void {
    this.historyOf.set(null);
    this.fills.set([]);
    this.locations.set([]);
  }

  locate(container: Container, kind: LocationKind, place: string | null): void {
    this.api
      .locate(container.id, { kind, place })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.openHistory(container),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar.')),
      });
  }

  openIdentifiers(container: Container): void {
    this.selected.set(container);
    this.identifiers.set([]);
    this.reloadIdentifiers(container.id);
  }

  closeIdentifiers(): void {
    this.selected.set(null);
    this.identifiers.set([]);
  }

  assign(value: string, technology: IdentifierTechnology): void {
    const container = this.selected();
    if (!container) {
      return;
    }
    this.saving.set(true);
    this.api
      .assign(container.id, { value, technology })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => this.reloadIdentifiers(container.id),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível etiquetar.')),
      });
  }

  retireIdentifier(identifier: ContainerIdentifier): void {
    const container = this.selected();
    if (!container) {
      return;
    }
    this.api
      .retireIdentifier(identifier.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reloadIdentifiers(container.id),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível aposentar.')),
      });
  }

  /** Ler um código identifica, e não autoriza: o resultado é um vasilhame na tela, e nada mais. */
  scan(value: string): void {
    this.scanned.set(null);
    this.api
      .resolve(value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: c => this.scanned.set(c),
        error: (e: ApiError) =>
          this.toast.error(this.message(e, 'Nenhum contêiner responde por esse código.')),
      });
  }

  clearScan(): void {
    this.scanned.set(null);
  }

  private reloadIdentifiers(containerId: string): void {
    this.api
      .identifiers(containerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.identifiers.set(list) });
  }

  /**
   * A recusa de encher vem com o motivo, e o motivo vira frase.
   *
   * <p>Sem isso o operador tentaria outro keg até um passar, sem nunca saber o que havia de errado com
   * o primeiro.
   */
  private message(e: ApiError, fallback: string): string {
    const reason = e?.error?.reasonCode;
    if (reason) {
      // Duas famílias de motivo: o vasilhame e o líquido. Misturá-las daria ao operador uma mensagem
      // que não diz o que trocar.
      const frase = NOT_FILLABLE_REASONS[reason] ?? FILL_REFUSAL_REASONS[reason];
      if (frase) {
        return frase;
      }
    }
    return e?.error?.detail ?? fallback;
  }
}
