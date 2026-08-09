import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ScanApi } from './scan.api';

/**
 * A resolução de um código (INT-003).
 *
 * <p>O que se afirma aqui é que a leitura é uma pergunta — `GET`, sem corpo — e que o código viaja como
 * parâmetro. É isso que permite ao QR conter um link que o aplicativo de câmera do telefone abre sozinho.
 */
describe('ScanApi', () => {
  let api: ScanApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ScanApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('resolve por GET, com o código no parâmetro', () => {
    api.resolve('brassia://equipamento/TANQUE-01').subscribe();

    const request = http.expectOne(r => r.url === '/api/v1/integration/scan');

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('code')).toBe('brassia://equipamento/TANQUE-01');
    request.flush({ type: 'equipamento', identifier: 'TANQUE-01', route: '/equipment' });
  });

  it('devolve o destino resolvido', () => {
    let resolvido: { route: string } | undefined;
    api.resolve('brassia://lote/L-1').subscribe(r => (resolvido = r));

    http
      .expectOne(r => r.url === '/api/v1/integration/scan')
      .flush({ type: 'lote', identifier: 'L-1', route: '/production/batches' });

    expect(resolvido?.route).toBe('/production/batches');
  });
});
