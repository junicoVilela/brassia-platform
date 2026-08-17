import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  Container,
  ContainerFill,
  ContainerIdentifier,
  ContainerLoan,
  ContainerSanitation,
  InspectionPolicy,
  ContainerKind,
  ContainerLocation,
  ContainerState,
  LOAN_REFUSAL_REASONS,
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

  /** Os empréstimos em aberto — e, filtrada, a fila de atrasados. */
  readonly loans = signal<ContainerLoan[]>([]);
  readonly onlyOverdue = signal(false);
  readonly sanitations = signal<ContainerSanitation[]>([]);

  /**
   * Quantos vasilhames estão atrasados.
   *
   * <p>Atrasado é o que ainda não voltou depois do prazo — quem devolveu tarde já é história, e contar
   * os dois juntos faria a cobrança do dia ligar para quem já devolveu.
   */
  readonly overdueCount = computed(() => this.loans().filter(l => l.overdue).length);

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

  loadLoans(): void {
    const hoje = new Date().toISOString().slice(0, 10);
    this.api
      .loans(this.onlyOverdue() ? hoje : null)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.loans.set(list) });
  }

  toggleOverdue(only: boolean): void {
    this.onlyOverdue.set(only);
    this.loadLoans();
  }

  lend(container: Container, customerId: string, customerName: string, dueOn: string,
    depositAmount: number | null): void {
    this.saving.set(true);
    this.api
      .lend(container.id, {
        customerId,
        customerName,
        dueOn,
        // Ausência de caução é NULO, e não zero: zero somaria no relatório de valores retidos como se
        // houvesse dinheiro parado.
        depositAmount: depositAmount && depositAmount > 0 ? depositAmount : null,
        depositCurrency: depositAmount && depositAmount > 0 ? 'BRL' : null,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Empréstimo registrado.');
          this.loadLoans();
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível emprestar.')),
      });
  }

  returnLoan(loan: ContainerLoan): void {
    this.api
      .returnLoan(loan.containerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // "A devolver", e não "devolvida": o estorno é lançamento financeiro, e dizer o contrário
          // faria a tela afirmar um pagamento que ninguém fez.
          this.toast.success('Devolução registrada. A caução fica a devolver ao cliente.');
          this.loadLoans();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível encerrar.')),
      });
  }

  declareLoss(loan: ContainerLoan, reason: string): void {
    this.api
      .declareLoss(loan.containerId, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // Perda não é descarte: dizer isso aqui é o que impede as duas coisas de virarem a mesma
          // linha no inventário.
          this.toast.success('Perda registrada. O vasilhame saiu do inventário e a caução fica retida.');
          this.loadLoans();
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar a perda.')),
      });
  }

  /** Os perdidos que ainda podem voltar — a fila que a tela oferece para recuperar. */
  readonly lost = computed(() => this.loans().filter(l => l.lostAt !== null && !l.recoveredAt));

  recoverLoan(loan: ContainerLoan, reason: string): void {
    this.api
      .recoverLoan(loan.containerId, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // "Voltou ao inventário, sujo" — e não "perda desfeita": a perda aconteceu, e o registro dela
          // continua. Dizer o contrário faria a tela prometer um apagamento que não houve.
          this.toast.success(
            'Vasilhame de volta ao inventário, para lavar. A caução volta a ser devida ao cliente.',
          );
          this.loadLoans();
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível recuperar.')),
      });
  }

  readonly policies = signal<InspectionPolicy[]>([]);

  /** A validade sugerida pela política, quando há uma. Nula não afrouxa nada — só não sugere. */
  readonly suggestedValidUntil = signal<string | null>(null);

  loadPolicies(): void {
    this.api
      .inspectionPolicies()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.policies.set(list) });
  }

  definePolicy(kind: Container['kind'], intervalMonths: number, note: string | null): void {
    this.api
      .defineInspectionPolicy({ kind, intervalMonths, note })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Periodicidade definida.');
          this.loadPolicies();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível definir.')),
      });
  }

  loadSuggestion(container: Container): void {
    this.suggestedValidUntil.set(null);
    this.api
      .inspectionSuggestion(container.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: r => this.suggestedValidUntil.set(r?.validUntil ?? null) });
  }

  loadSanitations(container: Container): void {
    this.api
      .sanitations(container.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.sanitations.set(list) });
  }

  sanitize(container: Container, method: string, note: string | null): void {
    this.api
      .sanitize(container.id, { method, note })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Higienização registrada.');
          this.loadSanitations(container);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar.')),
      });
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
      const frase =
        NOT_FILLABLE_REASONS[reason] ?? FILL_REFUSAL_REASONS[reason] ?? LOAN_REFUSAL_REASONS[reason];
      if (frase) {
        return frase;
      }
    }
    return e?.error?.detail ?? fallback;
  }
}
