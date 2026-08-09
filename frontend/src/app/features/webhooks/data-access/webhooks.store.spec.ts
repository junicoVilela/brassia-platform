import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WebhookDelivery, WebhookSubscription } from '../domain/webhook.model';
import { WebhooksStore } from './webhooks.store';

describe('WebhooksStore', () => {
  let store: WebhooksStore;
  let http: HttpTestingController;

  const subscription: WebhookSubscription = {
    id: 's1',
    name: 'ERP',
    endpoint: 'https://erp.example.com/hooks',
    events: ['brew_order.released'],
    status: 'ACTIVE',
    secretHint: 'abcd…********',
    createdAt: '2026-08-09T10:00:00Z',
    version: 0,
  };

  function delivery(overrides: Partial<WebhookDelivery>): WebhookDelivery {
    return {
      id: 'd1',
      eventType: 'brew_order.released',
      eventId: 'order-1',
      status: 'DELIVERED',
      attempts: 1,
      nextAttemptAt: null,
      deliveredAt: '2026-08-09T10:00:30Z',
      lastResponseStatus: 200,
      lastError: null,
      createdAt: '2026-08-09T10:00:00Z',
      ...overrides,
    };
  }

  /** `load()` dispara duas chamadas; a ordem de resposta não importa para o estado. */
  function flushLoad(subscriptions: WebhookSubscription[], types: string[] = []): void {
    http.expectOne('/api/v1/integration/webhooks/event-types').flush(types);
    http.expectOne('/api/v1/integration/webhooks').flush(subscriptions);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WebhooksStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(WebhooksStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carrega assinaturas e os tipos de evento permitidos', () => {
    store.load();
    flushLoad([subscription], ['brew_order.released', 'recipe.published']);

    expect(store.subscriptions().length).toBe(1);
    expect(store.eventTypes().length).toBe(2);
    expect(store.loading()).toBe(false);
  });

  it('a lista de tipos vem do servidor, não é mantida pela tela', () => {
    // Se a tela mantivesse a própria lista, um tipo novo no backend só apareceria depois de alguém
    // lembrar de atualizar o frontend — e a allowlist deixaria de ser uma só.
    store.load();
    flushLoad([], ['inventado.novo']);

    expect(store.eventTypes()).toEqual(['inventado.novo']);
  });

  it('revela o segredo depois de criar e o esconde quando dispensado', () => {
    store.create({ name: 'ERP', endpoint: 'https://x.example.com/h', events: ['recipe.published'] });
    http.expectOne('/api/v1/integration/webhooks').flush({
      subscription,
      secret: 'segredo-em-claro',
      warning: 'Guarde agora.',
    });
    flushLoad([subscription]);

    expect(store.revealedSecret()).toBe('segredo-em-claro');

    store.dismissSecret();
    expect(store.revealedSecret()).toBeNull();
  });

  it('recarregar a lista não traz o segredo de volta', () => {
    // É o que faz "exibido uma única vez" ser verdade e não intenção.
    store.create({ name: 'ERP', endpoint: 'https://x.example.com/h', events: ['recipe.published'] });
    http.expectOne('/api/v1/integration/webhooks').flush({
      subscription,
      secret: 'segredo-em-claro',
      warning: 'x',
    });
    flushLoad([subscription]);
    store.dismissSecret();

    store.load();
    flushLoad([subscription]);

    expect(store.revealedSecret()).toBeNull();
  });

  it('só a entrega esgotada conta como falha; pendente com tentativas é o retry funcionando', () => {
    store.select('s1');
    http.expectOne('/api/v1/integration/webhooks/s1/deliveries').flush([
      delivery({ id: 'a' }),
      delivery({ id: 'b', status: 'PENDING', attempts: 2, deliveredAt: null, nextAttemptAt: 'x' }),
      delivery({ id: 'c', status: 'EXHAUSTED', attempts: 5, deliveredAt: null, lastError: 'timeout' }),
    ]);

    expect(store.deliveries().length).toBe(3);
    expect(store.failedDeliveries().length).toBe(1);
    expect(store.hasFailures()).toBe(true);
  });

  it('sem entrega esgotada não há alarme', () => {
    store.select('s1');
    http
      .expectOne('/api/v1/integration/webhooks/s1/deliveries')
      .flush([delivery({}), delivery({ id: 'b', status: 'PENDING', attempts: 3, deliveredAt: null })]);

    expect(store.hasFailures()).toBe(false);
  });

  it('envia a versão ao mudar o estado', () => {
    store.changeStatus({ ...subscription, version: 4 }, 'PAUSED');
    const request = http.expectOne('/api/v1/integration/webhooks/s1/status');

    expect(request.request.body).toEqual({ status: 'PAUSED', expectedVersion: 4 });
    request.flush({ ...subscription, status: 'PAUSED', version: 5 });
    flushLoad([]);
  });

  it('traduz 403 para a alçada de mandar dados para fora', () => {
    store.create({ name: 'X', endpoint: 'https://x.example.com/h', events: ['recipe.published'] });
    http
      .expectOne('/api/v1/integration/webhooks')
      .flush({ detail: 'x' }, { status: 403, statusText: 'Forbidden' });

    expect(store.createError()).toContain('para fora');
  });

  it('traduz 400 mencionando https e evento obrigatório', () => {
    store.create({ name: 'X', endpoint: 'http://inseguro', events: [] });
    http
      .expectOne('/api/v1/integration/webhooks')
      .flush({ detail: 'x' }, { status: 400, statusText: 'Bad Request' });

    expect(store.createError()).toContain('https');
  });

  it('assinatura desconhecida vira mensagem sobre a cervejaria, não sobre permissão', () => {
    store.select('s1');
    http.expectOne('/api/v1/integration/webhooks/s1/deliveries').flush(
      { code: 'unknown_webhook_subscription', detail: 'x' },
      { status: 404, statusText: 'Not Found' },
    );

    expect(store.deliveriesError()).toContain('não existe nesta cervejaria');
  });

  it('sem seleção, selected é nulo', () => {
    expect(store.selected()).toBeNull();
    expect(store.hasFailures()).toBe(false);
  });
});
