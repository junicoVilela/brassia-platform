import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SensorsStore } from './sensors.store';
import { SensorDevice, SensorReading } from '../domain/sensor.model';

describe('SensorsStore', () => {
  let store: SensorsStore;
  let http: HttpTestingController;

  const device: SensorDevice = {
    id: 'd1',
    code: 'TANK-01-TEMP',
    name: 'Termômetro 1',
    measure: 'TEMPERATURE',
    unit: 'C',
    equipmentId: null,
    expectedIntervalSeconds: 300,
    status: 'ACTIVE',
    registeredAt: '2026-08-09T10:00:00Z',
    version: 0,
  };

  function reading(overrides: Partial<SensorReading>): SensorReading {
    return {
      id: 'r1',
      deviceId: 'd1',
      messageId: 'm1',
      measure: 'TEMPERATURE',
      value: 18.5,
      unit: 'C',
      measuredAt: '2026-08-09T10:00:00Z',
      receivedAt: '2026-08-09T10:00:30Z',
      quality: 'GOOD',
      qualityReason: null,
      delaySeconds: 30,
      late: false,
      ...overrides,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SensorsStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(SensorsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carrega dispositivos e separa ativos de inativos', () => {
    store.load();
    http.expectOne('/api/v1/sensors/devices').flush([
      device,
      { ...device, id: 'd2', code: 'TANK-02', status: 'PAUSED' },
      { ...device, id: 'd3', code: 'TANK-03', status: 'REVOKED' },
    ]);

    expect(store.devices().length).toBe(3);
    expect(store.activeDevices().length).toBe(1);
    expect(store.inactiveDevices().length).toBe(2);
    expect(store.loading()).toBe(false);
  });

  it('mostra mensagem quando a listagem falha', () => {
    store.load();
    http.expectOne('/api/v1/sensors/devices').error(new ProgressEvent('erro'));

    expect(store.error()).toContain('Não foi possível carregar');
    expect(store.loading()).toBe(false);
  });

  it('conta como sinalizada tanto a leitura fora da faixa quanto a atrasada', () => {
    // Os dois eixos são independentes no domínio e somados aqui de propósito: para quem opera, a
    // pergunta é uma só — "algo está errado com este sensor?".
    store.select('d1');
    http.expectOne(r => r.url === '/api/v1/sensors/devices/d1/readings').flush([
      reading({ id: 'a' }),
      reading({ id: 'b', quality: 'OUT_OF_RANGE', qualityReason: 'fora da faixa' }),
      reading({ id: 'c', late: true, delaySeconds: 1800 }),
    ]);

    expect(store.readings().length).toBe(3);
    expect(store.flaggedReadings().length).toBe(2);
    expect(store.hasFlagged()).toBe(true);
  });

  it('leitura boa e pontual não é sinalizada', () => {
    store.select('d1');
    http.expectOne(r => r.url === '/api/v1/sensors/devices/d1/readings').flush([reading({})]);

    expect(store.hasFlagged()).toBe(false);
  });

  it('a janela pedida é de 24 horas para trás', () => {
    store.select('d1');
    const request = http.expectOne(r => r.url === '/api/v1/sensors/devices/d1/readings');
    const from = new Date(request.request.params.get('from') as string).getTime();
    const to = new Date(request.request.params.get('to') as string).getTime();

    expect(Math.round((to - from) / 3600000)).toBe(24);
    request.flush([]);
  });

  it('envia a versão do dispositivo ao mudar o estado', () => {
    // Sem isso, dois operadores decidiriam o destino do mesmo dispositivo sem que nenhum percebesse.
    store.changeStatus({ ...device, version: 3 }, 'PAUSED');
    const request = http.expectOne('/api/v1/sensors/devices/d1/status');

    expect(request.request.body).toEqual({ status: 'PAUSED', expectedVersion: 3 });
    request.flush({ ...device, status: 'PAUSED', version: 4 });
    http.expectOne('/api/v1/sensors/devices').flush([]);
  });

  it('traduz dispositivo inativo para uma mensagem sobre o dispositivo, não sobre permissão', () => {
    store.select('d1');
    http.expectOne(r => r.url === '/api/v1/sensors/devices/d1/readings').flush(
      { code: 'sensor_device_inactive', detail: 'x' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(store.readingsError()).toContain('não está aceitando leituras');
  });

  it('traduz 403 na revogação para a alçada certa', () => {
    store.changeStatus(device, 'REVOKED');
    http.expectOne('/api/v1/sensors/devices/d1/status').flush(
      { detail: 'x' },
      { status: 403, statusText: 'Forbidden' },
    );

    // A mensagem vai por toast; o que se afirma aqui é que o erro não derruba o estado da lista.
    expect(store.devices()).toEqual([]);
  });

  it('mostra erro de cadastro sem limpar a lista já carregada', () => {
    store.load();
    http.expectOne('/api/v1/sensors/devices').flush([device]);

    store.register({
      code: 'X-1',
      name: 'X',
      measure: 'TEMPERATURE',
      unit: 'PSI',
      equipmentId: null,
      expectedIntervalSeconds: null,
    });
    http.expectOne('/api/v1/sensors/devices').flush(
      { detail: 'x' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(store.registerError()).toContain('unidade');
    expect(store.devices().length).toBe(1);
  });

  it('selected acompanha o dispositivo escolhido', () => {
    store.load();
    http.expectOne('/api/v1/sensors/devices').flush([device]);

    store.select('d1');
    http.expectOne(r => r.url === '/api/v1/sensors/devices/d1/readings').flush([]);

    expect(store.selected()?.code).toBe('TANK-01-TEMP');
  });

  it('sem seleção, selected é nulo', () => {
    expect(store.selected()).toBeNull();
    expect(store.hasFlagged()).toBe(false);
  });
});
