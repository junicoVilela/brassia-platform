import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { Contact, ContactPurpose, Customer, DueContact } from '../domain/customer.model';
import { CrmApi } from './crm.api';

interface ApiError {
  status?: number;
  error?: { code?: string; detail?: string };
}

/**
 * Estado de clientes, contatos e consentimentos (CRM-001).
 *
 * <p>Os contatos são sempre relidos do servidor depois de uma decisão, e não remendados na memória: a
 * permissão por finalidade é derivada do livro inteiro, e recalcular isso no cliente seria manter duas
 * implementações da mesma regra — que divergem na primeira mudança.
 */
@Injectable()
export class CrmStore {
  private readonly api = inject(CrmApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly customers = signal<Customer[]>([]);
  readonly loading = signal(false);

  /**
   * Contatos que passaram do prazo de retenção.
   *
   * <p>A lista existe para ser conferida: anonimizar é irreversível, e o número sozinho não se confere.
   */
  readonly retentionQueue = signal<DueContact[]>([]);
  readonly error = signal<string | null>(null);
  readonly onlyActive = signal(true);

  readonly selected = signal<Customer | null>(null);
  readonly contacts = signal<Contact[]>([]);
  readonly contactsLoading = signal(false);
  readonly saving = signal(false);

  /** Nulo é estado legítimo: a casa não decidiu, e enquanto não decidir nada expira. */
  readonly retentionDays = signal<number | null>(null);

  readonly hasCustomers = computed(() => this.customers().length > 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .customers(this.onlyActive())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: list => this.customers.set(list),
        error: (e: ApiError) => this.error.set(this.message(e, 'Não foi possível carregar os clientes.')),
      });
    this.api
      .retentionPolicy()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: p => this.retentionDays.set(p.daysAfterLastInteraction) });
  }

  toggleOnlyActive(value: boolean): void {
    this.onlyActive.set(value);
    this.load();
  }

  select(customer: Customer): void {
    this.selected.set(customer);
    this.loadContacts(customer.id);
  }

  loadContacts(customerId: string): void {
    this.contactsLoading.set(true);
    this.api
      .contacts(customerId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.contactsLoading.set(false)),
      )
      .subscribe({
        next: list => this.contacts.set(list),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível carregar os contatos.')),
      });
  }

  createCustomer(legalName: string, tradeName: string | null, taxId: string | null): void {
    this.saving.set(true);
    this.api
      .createCustomer({ legalName, tradeName, taxId })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Cliente cadastrado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível cadastrar o cliente.')),
      });
  }

  setActive(customer: Customer, active: boolean): void {
    this.api
      .setActive(customer.id, active)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(active ? 'Cliente reativado.' : 'Cliente desativado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível alterar a situação.')),
      });
  }

  createContact(
    customerId: string,
    body: { name: string; email: string | null; phone: string | null; role: string | null },
  ): void {
    this.saving.set(true);
    this.api
      .createContact(customerId, body)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Contato cadastrado.');
          this.loadContacts(customerId);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível cadastrar o contato.')),
      });
  }

  recordConsent(
    contact: Contact,
    purpose: ContactPurpose,
    granted: boolean,
    decidedAt: string,
    source: string,
  ): void {
    this.api
      .recordConsent(contact.id, { purpose, granted, decidedAt, source })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(granted ? 'Consentimento registrado.' : 'Revogação registrada.');
          this.loadContacts(contact.customerId);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível registrar a decisão.')),
      });
  }

  anonymize(contact: Contact): void {
    this.api
      .anonymize(contact.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Contato anonimizado.');
          this.loadContacts(contact.customerId);
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível anonimizar o contato.')),
      });
  }

  saveRetention(days: number): void {
    this.api
      .saveRetentionPolicy(days)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: p => {
          this.retentionDays.set(p.daysAfterLastInteraction);
          this.toast.success('Prazo de retenção definido.');
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível definir o prazo.')),
      });
  }

  /** A mensagem do servidor vence a genérica: ela diz qual documento colidiu, ou por que a recusa. */
  loadRetentionQueue(): void {
    this.api
      .retentionQueue()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.retentionQueue.set(list) });
  }

  private message(e: ApiError, fallback: string): string {
    return e?.error?.detail ?? fallback;
  }
}
