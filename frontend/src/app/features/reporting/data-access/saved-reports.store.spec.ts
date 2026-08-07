import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { UsersApi } from '../../security/users/data-access/users.api';
import { ReportRun, SavedReport } from '../domain/saved-report.model';
import { SavedReportsApi } from './saved-reports.api';
import { SavedReportsStore } from './saved-reports.store';

const ANA = '11111111-1111-1111-1111-111111111111';
const BRUNO = '22222222-2222-2222-2222-222222222222';

function report(over: Partial<SavedReport> = {}): SavedReport {
  return {
    id: 'r1',
    name: 'Painel mensal',
    kind: 'DASHBOARD',
    definitionVersion: 1,
    filters: {},
    timezone: 'America/Sao_Paulo',
    format: 'JSON',
    schedule: 'MONTHLY',
    retentionDays: 30,
    ownerUserId: ANA,
    recipients: [BRUNO],
    active: true,
    createdAt: '2026-08-07T12:00:00Z',
    ...over,
  };
}

function run(over: Partial<ReportRun> = {}): ReportRun {
  return {
    id: 'run1',
    reportId: 'r1',
    definitionVersion: 1,
    status: 'SUCCEEDED',
    refusalReason: null,
    periodFrom: '2026-07-08T03:00:00Z',
    periodTo: '2026-08-07T03:00:00Z',
    expiresAt: '2026-09-06T03:00:00Z',
    executedAt: '2026-08-07T12:00:00Z',
    expired: false,
    deliveries: [
      { userId: BRUNO, status: 'PENDING', detail: null, attempts: 0, lastAttemptAt: null },
    ],
    downloadToken: null,
    ...over,
  };
}

function setup(api: Partial<SavedReportsApi> = {}): SavedReportsStore {
  TestBed.configureTestingModule({
    providers: [
      SavedReportsStore,
      {
        provide: SavedReportsApi,
        useValue: {
          list: () => of([report()]),
          runs: () => of([run()]),
          run: () => of(run()),
          define: () => of(report()),
          activate: () => of(report({ active: false })),
          link: () => of({ token: 'tok', expiresAt: '2026-08-07T14:00:00Z' }),
          download: () => of('{}'),
          deliver: () => of(run()),
          ...api,
        },
      },
      {
        provide: UsersApi,
        useValue: {
          list: () =>
            of({
              content: [
                { id: ANA, email: 'ana@x', displayName: 'Ana', status: 'ACTIVE', emailVerifiedAt: null },
                { id: BRUNO, email: 'bruno@x', displayName: 'Bruno', status: 'ACTIVE', emailVerifiedAt: null },
              ],
              page: 0,
              size: 100,
              totalElements: 2,
              totalPages: 1,
            }),
        },
      },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(SavedReportsStore);
}

describe('SavedReportsStore', () => {
  it('mostra nome de pessoa no lugar do id, porque destinatário é usuário e não endereço', () => {
    const store = setup();
    store.load();

    expect(store.nameOf()(ANA)).toBe('Ana');
    // Usuário que saiu da lista não vira erro: aparece encurtado.
    expect(store.nameOf()('99999999-9999-9999-9999-999999999999')).toBe('99999999');
  });

  it('a tela sobrevive sem a lista de pessoas, mostrando id', () => {
    TestBed.resetTestingModule();
    const store = setupWithBrokenUsers();
    store.load();

    expect(store.people()).toEqual([]);
    expect(store.nameOf()(ANA)).toBe('11111111');
  });

  it('execução recusada não é erro: entra na lista com o motivo', () => {
    const recusada = run({ status: 'REFUSED', refusalReason: 'o dono perdeu a permissão' });
    const store = setup({ run: () => of(recusada), runs: () => of([recusada]) });
    store.load();

    store.run('r1');

    expect(store.error()).toBeNull();
    expect(store.runs()[0].status).toBe('REFUSED');
    expect(store.runs()[0].refusalReason).toContain('perdeu a permissão');
  });

  it('baixar passa pela emissão do link antes de pedir o arquivo', () => {
    const link = vi.fn(() => of({ token: 'tok', expiresAt: '2026-08-07T14:00:00Z' }));
    const download = vi.fn(() => of('{}'));
    const store = setup({ link, download });
    stubDownload(vi.fn());

    store.download(run(), 'Painel mensal');

    expect(link).toHaveBeenCalledWith('run1');
    // O link é pessoal e temporário: nunca se reaproveita um token guardado.
    expect(download).toHaveBeenCalledWith('tok');
  });

  it('traduz o link vencido', () => {
    const store = setup({ link: () => throwError(() => ({ status: 410 })) });

    store.download(run(), 'Painel');

    expect(store.error()).toContain('não vale mais');
  });

  it('traduz a recusa de alçada para criar e programar', () => {
    const store = setup({ define: () => throwError(() => ({ status: 403 })) });

    store.define({
      name: 'x',
      kind: 'DASHBOARD',
      filters: {},
      timezone: 'America/Sao_Paulo',
      format: 'JSON',
      schedule: 'DAILY',
      retentionDays: 30,
      ownerUserId: ANA,
      recipients: [],
    });

    expect(store.error()).toContain('alçada própria');
  });

  it('traduz nome repetido e definição alterada por outra pessoa', () => {
    const store = setup({ activate: () => throwError(() => ({ status: 409 })) });

    store.activate('r1', false);

    expect(store.error()).toContain('Já existe');
  });

  it('escolher o mesmo relatório duas vezes fecha o detalhe', () => {
    const store = setup();
    store.load();

    store.select('r1');
    store.select('r1');

    expect(store.selected()).toBeNull();
    expect(store.runs()).toEqual([]);
  });
});

function setupWithBrokenUsers(): SavedReportsStore {
  TestBed.configureTestingModule({
    providers: [
      SavedReportsStore,
      {
        provide: SavedReportsApi,
        useValue: { list: () => of([report()]), runs: () => of([]) },
      },
      { provide: UsersApi, useValue: { list: () => throwError(() => ({ status: 403 })) } },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(SavedReportsStore);
}

/** Substitui a âncora e o object URL para o download não sair do teste. */
function stubDownload(click: () => void): void {
  vi.spyOn(document, 'createElement').mockReturnValue({ click, href: '', download: '' } as never);
  URL.createObjectURL = vi.fn(() => 'blob:stub');
  URL.revokeObjectURL = vi.fn();
}
