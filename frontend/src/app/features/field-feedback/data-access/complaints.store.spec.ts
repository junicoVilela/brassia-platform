import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Complaint, RegisterComplaintRequest } from '../domain/complaint.model';
import { ComplaintsStore } from './complaints.store';

/**
 * Estado das reclamações (FLD-001).
 *
 * <p>O que estes testes fixam: o contato nunca fica em cache — cada leitura vai ao servidor, porque cada
 * leitura precisa gerar seu registro de auditoria — e a recusa de encerramento explica as duas saídas
 * possíveis, que são opostas.
 */
describe('ComplaintsStore', () => {
  let store: ComplaintsStore;
  let http: HttpTestingController;

  const URL = '/api/v1/field-feedback/complaints';

  function complaint(overrides: Partial<Complaint> = {}): Complaint {
    return {
      id: 'c1',
      batchId: 'b1',
      reference: 'SAC-1',
      category: 'OFF_FLAVOR',
      severity: 'QUALITY',
      description: 'Gosto de papelão',
      storage: {
        temperatureCelsius: null,
        daysSincePurchase: null,
        exposedToLight: null,
        notes: null,
        conditionsKnown: false,
      },
      sample: { status: 'UNKNOWN', location: null, analyzable: false },
      requiredActions: [],
      pendingActions: [],
      outcomes: [],
      status: 'OPEN',
      closingNote: null,
      closedBy: null,
      closedAt: null,
      registeredBy: 'u1',
      registeredAt: '2026-08-09T10:00:00Z',
      ...overrides,
    };
  }

  const request: RegisterComplaintRequest = {
    batchId: 'b1',
    category: 'OFF_FLAVOR',
    severity: 'QUALITY',
    description: 'x',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ComplaintsStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ComplaintsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('separa abertas, encerradas e bloqueadas', () => {
    store.load();
    http.expectOne(URL).flush([
      complaint({ id: 'a', status: 'OPEN' }),
      complaint({ id: 'b', status: 'OPEN', pendingActions: ['QUARANTINE'] }),
      complaint({ id: 'c', status: 'CLOSED' }),
    ]);

    expect(store.open().map(c => c.id)).toEqual(['a', 'b']);
    expect(store.closed().map(c => c.id)).toEqual(['c']);
    // A fila que não pode ser esquecida.
    expect(store.blocked().map(c => c.id)).toEqual(['b']);
  });

  it('RECUSA DE ENCERRAMENTO explica as duas saídas, que são opostas', () => {
    // Atender ou dispensar por escrito. Uma mensagem genérica deixaria a pessoa procurando um botão.
    store.close('c1', 'pronto');
    http.expectOne(`${URL}/c1/closure`).flush(
      { code: 'pending_required_actions', pendingActions: ['QUARANTINE', 'ROOT_CAUSE_ANALYSIS'] },
      { status: 422, statusText: 'erro' },
    );

    expect(store.complaints()).toEqual([]);
  });

  it('O CONTATO NUNCA VEM DE CACHE: cada leitura vai ao servidor', () => {
    // Reaproveitar o valor já carregado economizaria uma chamada e apagaria o rastro do segundo acesso.
    store.loadContact('c1');
    http.expectOne(`${URL}/c1/contact`).flush({
      name: 'Fulana',
      email: null,
      phone: null,
      address: null,
      erased: false,
      erasedAt: null,
      recordedAt: '2026-08-09T10:00:00Z',
    });
    expect(store.contact()?.name).toBe('Fulana');

    store.loadContact('c1');
    // Segunda chamada de verdade, e não reuso.
    http.expectOne(`${URL}/c1/contact`).flush({
      name: 'Fulana',
      email: null,
      phone: null,
      address: null,
      erased: false,
      erasedAt: null,
      recordedAt: '2026-08-09T10:00:00Z',
    });
  });

  it('trocar de reclamação limpa o contato anterior da memória', () => {
    // Dado pessoal disponível para quem não pediu é a versão em cliente do problema resolvido no servidor.
    store.loadContact('c1');
    http.expectOne(`${URL}/c1/contact`).flush({
      name: 'Fulana',
      email: null,
      phone: null,
      address: null,
      erased: false,
      erasedAt: null,
      recordedAt: '2026-08-09T10:00:00Z',
    });

    store.loadContact('c2');

    expect(store.contact()).toBeNull();
    expect(store.contactFor()).toBe('c2');
    http.expectOne(`${URL}/c2/contact`).flush({
      name: null,
      email: null,
      phone: null,
      address: null,
      erased: true,
      erasedAt: '2026-08-09T11:00:00Z',
      recordedAt: '2026-08-09T10:00:00Z',
    });
    expect(store.contact()?.erased).toBe(true);
  });

  it('403 no contato explica que a permissão é própria e a leitura fica registrada', () => {
    store.loadContact('c1');
    http.expectOne(`${URL}/c1/contact`).flush({}, { status: 403, statusText: 'proibido' });

    expect(store.contactError()).toContain('permissão própria');
    expect(store.contactError()).toContain('registrada');
  });

  it('clearContact esvazia tudo', () => {
    store.loadContact('c1');
    http.expectOne(`${URL}/c1/contact`).flush({}, { status: 403, statusText: 'proibido' });

    store.clearContact();

    expect(store.contact()).toBeNull();
    expect(store.contactFor()).toBeNull();
    expect(store.contactError()).toBeNull();
  });

  it('apagar o contato limpa a memória e recarrega nada da pessoa', () => {
    store.eraseContact('c1');
    http.expectOne(`${URL}/c1/contact`).flush(null);

    expect(store.contact()).toBeNull();
    expect(store.contactFor()).toBeNull();
  });

  it('registrar avisa quantas ações passaram a ser exigidas', () => {
    let chamado = false;
    store.register(request, () => (chamado = true));
    http.expectOne(URL).flush(
      complaint({
        requiredActions: [{ code: 'QUARANTINE', description: 'Quarentenar' }],
        pendingActions: ['QUARANTINE'],
      }),
    );

    expect(chamado).toBe(true);
    http.expectOne(URL).flush([]);
  });

  it('lote inexistente devolve a orientação do servidor', () => {
    store.register(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'unknown_complaint_batch', detail: 'o lote não existe nesta cervejaria' },
      { status: 422, statusText: 'erro' },
    );

    expect(store.registerError()).toContain('não existe');
  });
});
