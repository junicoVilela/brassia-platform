import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { Contact, Customer } from '../domain/customer.model';
import { CrmApi } from './crm.api';
import { CrmStore } from './crm.store';

function customer(over: Partial<Customer> = {}): Customer {
  return {
    id: 'c1',
    legalName: 'Central Bebidas Ltda',
    tradeName: 'Bar Central',
    displayName: 'Bar Central',
    taxId: '12.345.678/0001-90',
    active: true,
    ...over,
  };
}

function contact(over: Partial<Contact> = {}): Contact {
  return {
    id: 'k1',
    customerId: 'c1',
    name: 'Ana Ribeiro',
    email: 'ana@bar.com.br',
    phone: null,
    role: 'compras',
    anonymized: false,
    anonymizedAt: null,
    purposes: [
      { purpose: 'TRANSACTIONAL', basis: 'CONTRACT', allowedNow: true },
      { purpose: 'MARKETING', basis: 'CONSENT', allowedNow: false },
      { purpose: 'SURVEY', basis: 'CONSENT', allowedNow: false },
    ],
    consentHistory: [],
    ...over,
  };
}

function setup(api: Partial<CrmApi>) {
  api.retentionQueue ??= () => of([]);
  const toast = { success: vi.fn(), error: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      CrmStore,
      { provide: CrmApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(CrmStore), toast };
}

describe('CrmStore', () => {
  it('carrega clientes e a política de retenção', () => {
    const { store } = setup({
      customers: () => of([customer()]),
      retentionPolicy: () => of({ daysAfterLastInteraction: 365 }),
    } as Partial<CrmApi>);

    store.load();

    expect(store.customers()).toHaveLength(1);
    expect(store.retentionDays()).toBe(365);
    expect(store.loading()).toBe(false);
  });

  it('trata prazo nulo como lacuna, e não como zero', () => {
    // Sem política nada expira. Zero anonimizaria no ato do cadastro, que não é política e sim defeito.
    const { store } = setup({
      customers: () => of([]),
      retentionPolicy: () => of({ daysAfterLastInteraction: null }),
    } as Partial<CrmApi>);

    store.load();

    expect(store.retentionDays()).toBeNull();
  });

  it('mostra a mensagem do servidor, que diz qual documento colidiu', () => {
    const { store, toast } = setup({
      customers: () => of([]),
      retentionPolicy: () => of({ daysAfterLastInteraction: null }),
      createCustomer: () =>
        throwError(() => ({
          status: 409,
          error: { code: 'crm_duplicate_tax_id', detail: 'o documento 123 já está cadastrado' },
        })),
    } as Partial<CrmApi>);

    store.createCustomer('Segundo Ltda', null, '123');

    expect(toast.error).toHaveBeenCalledWith('o documento 123 já está cadastrado');
  });

  it('relê os contatos do servidor depois de registrar uma decisão', () => {
    // A permissão por finalidade é derivada do livro inteiro. Remendar em memória seria manter duas
    // implementações da mesma regra, e elas divergiriam na primeira mudança.
    const contacts = vi.fn().mockReturnValue(of([contact()]));
    const recordConsent = vi.fn().mockReturnValue(of(void 0));
    const { store } = setup({ contacts, recordConsent } as Partial<CrmApi>);

    store.recordConsent(contact(), 'MARKETING', true, '2026-03-10T12:00:00.000Z', 'site');

    expect(recordConsent).toHaveBeenCalledWith('k1', {
      purpose: 'MARKETING',
      granted: true,
      decidedAt: '2026-03-10T12:00:00.000Z',
      source: 'site',
    });
    expect(contacts).toHaveBeenCalledWith('c1');
  });

  it('recarrega a lista ao desativar, porque o cliente sai da lista padrão sem deixar de existir', () => {
    const customers = vi.fn().mockReturnValue(of([]));
    const setActive = vi.fn().mockReturnValue(of(void 0));
    const { store, toast } = setup({
      customers,
      setActive,
      retentionPolicy: () => of({ daysAfterLastInteraction: null }),
    } as Partial<CrmApi>);

    store.setActive(customer(), false);

    expect(setActive).toHaveBeenCalledWith('c1', false);
    expect(toast.success).toHaveBeenCalledWith('Cliente desativado.');
    expect(customers).toHaveBeenCalled();
  });

  it('recarrega os contatos depois de anonimizar', () => {
    const contacts = vi.fn().mockReturnValue(of([contact({ anonymized: true, name: null })]));
    const anonymize = vi.fn().mockReturnValue(of(void 0));
    const { store } = setup({ contacts, anonymize } as Partial<CrmApi>);

    store.anonymize(contact());

    expect(anonymize).toHaveBeenCalledWith('k1');
    expect(store.contacts()[0].anonymized).toBe(true);
  });

  it('a fila de retenção traz a origem da data, e não só o vencimento', () => {
    // Anonimizar é irreversível: "vence em março" sem dizer que a conta partiu de uma entrega de 2024 é
    // um número que ninguém consegue conferir — e conferir é o ponto.
    const { store } = setup({
      retentionQueue: () =>
        of([
          {
            contactId: 'c1',
            customerId: 'cli1',
            name: 'Bruno',
            lastRelationship: '2024-06-20',
            source: 'última entrega',
            dueSince: '2025-06-20',
          },
        ]),
    } as Partial<CrmApi>);

    store.loadRetentionQueue();

    expect(store.retentionQueue()).toHaveLength(1);
    expect(store.retentionQueue()[0].source).toBe('última entrega');
  });
});
