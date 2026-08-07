import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, switchMap } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { UsersApi } from '../../security/users/data-access/users.api';
import { SecurityUserSummary } from '../../security/users/domain/user.model';
import { ReportRun, SavedReport, SavedReportRequest } from '../domain/saved-report.model';
import { SavedReportsApi } from './saved-reports.api';

interface SavedReportError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado dos relatórios salvos (RPT-003).
 *
 * <p>Carrega a lista de usuários junto com as definições porque dono e destinatários são
 * <strong>usuários da plataforma</strong>, não endereços digitados: a tela precisa poder mostrar
 * nome em vez de UUID, e precisa oferecer escolha em vez de campo livre.
 */
@Injectable()
export class SavedReportsStore {
  private readonly api = inject(SavedReportsApi);
  private readonly users = inject(UsersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly reports = signal<SavedReport[]>([]);
  readonly people = signal<SecurityUserSummary[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly selected = signal<SavedReport | null>(null);
  readonly runs = signal<ReportRun[]>([]);
  readonly runsLoading = signal(false);
  readonly running = signal(false);

  /** Nome de quem tem id, ou o id encurtado quando o usuário não está mais na lista. */
  readonly nameOf = computed(() => {
    const byId = new Map(this.people().map(person => [person.id, person.displayName]));
    return (userId: string) => byId.get(userId) ?? userId.slice(0, 8);
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: reports => this.reports.set(reports),
        error: (e: SavedReportError) => this.error.set(this.messageFor(e)),
      });
    this.users
      .list(0, 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // Sem a lista de pessoas a tela ainda funciona, só mostra id em vez de nome.
        next: page => this.people.set(page.content),
        error: () => this.people.set([]),
      });
  }

  select(reportId: string): void {
    const report = this.reports().find(candidate => candidate.id === reportId) ?? null;
    if (this.selected()?.id === reportId) {
      this.selected.set(null);
      this.runs.set([]);
      return;
    }
    this.selected.set(report);
    this.loadRuns(reportId);
  }

  define(request: SavedReportRequest): void {
    this.error.set(null);
    this.api
      .define(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: report => {
          this.toast.success('Relatório salvo.');
          this.reports.update(current => [...current, report].sort(byName));
          this.selected.set(report);
        },
        error: (e: SavedReportError) => this.error.set(this.messageFor(e)),
      });
  }

  activate(reportId: string, active: boolean): void {
    this.error.set(null);
    this.api
      .activate(reportId, active)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: report => this.replace(report),
        error: (e: SavedReportError) => this.error.set(this.messageFor(e)),
      });
  }

  /**
   * Executa e recarrega as execuções.
   *
   * <p>Recusa não é erro: a execução aconteceu e disse por que não produziu. Tratá-la como falha
   * esconderia justamente o caso que a história existe para tornar visível — o dono que perdeu a
   * alçada.
   */
  run(reportId: string): void {
    this.running.set(true);
    this.error.set(null);
    this.api
      .run(reportId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(() => this.api.runs(reportId)),
        finalize(() => this.running.set(false)),
      )
      .subscribe({
        next: runs => {
          this.runs.set(runs);
          const last = runs[0];
          if (last?.status === 'REFUSED') {
            this.toast.success('Execução registrada — e recusada. Veja o motivo abaixo.');
          } else {
            this.toast.success('Relatório executado.');
          }
        },
        error: (e: SavedReportError) => this.error.set(this.messageFor(e)),
      });
  }

  /** Baixa o artefato pelo link temporário; o token é pessoal e a chamada é autenticada. */
  download(run: ReportRun, reportName: string): void {
    this.error.set(null);
    this.api
      .link(run.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(issued => this.api.download(issued.token)),
      )
      .subscribe({
        next: content => this.save(content, reportName),
        error: (e: SavedReportError) => this.error.set(this.messageFor(e)),
      });
  }

  deliver(runId: string, recipientId: string, delivered: boolean): void {
    this.error.set(null);
    this.api
      .deliver(runId, recipientId, delivered, delivered ? null : 'entrega recusada na tela')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => this.runs.update(current =>
          current.map(run => (run.id === updated.id ? { ...updated, downloadToken: run.downloadToken } : run))),
        error: (e: SavedReportError) => this.error.set(this.messageFor(e)),
      });
  }

  private loadRuns(reportId: string): void {
    this.runsLoading.set(true);
    this.runs.set([]);
    this.api
      .runs(reportId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.runsLoading.set(false)))
      .subscribe({
        next: runs => this.runs.set(runs),
        error: (e: SavedReportError) => this.error.set(this.messageFor(e)),
      });
  }

  private replace(report: SavedReport): void {
    this.reports.update(current =>
      current.map(candidate => (candidate.id === report.id ? report : candidate)));
    if (this.selected()?.id === report.id) {
      this.selected.set(report);
    }
  }

  private save(content: string, reportName: string): void {
    const blob = new Blob([content], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${reportName.replace(/[^\w.-]/g, '-')}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private messageFor(e: SavedReportError): string {
    if (e.code === 'unknown_saved_report' || e.status === 404) {
      return 'Este relatório não existe nesta cervejaria.';
    }
    if (e.status === 409) {
      return 'Já existe um relatório com este nome, ou a definição foi alterada por outra pessoa.';
    }
    if (e.status === 410) {
      // O link morre antes do artefato quando a retenção é curta; e morre sempre em duas horas.
      return 'Este link não vale mais. Gere outro.';
    }
    if (e.status === 403) {
      return 'Criar e programar relatórios é alçada própria, separada da de consultá-los.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}

function byName(one: SavedReport, other: SavedReport): number {
  return one.name.localeCompare(other.name);
}
