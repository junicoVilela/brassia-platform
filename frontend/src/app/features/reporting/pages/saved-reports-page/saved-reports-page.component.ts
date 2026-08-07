import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { SavedReportsStore } from '../../data-access/saved-reports.store';
import {
  DELIVERY_LABELS,
  DeliveryStatus,
  KIND_LABELS,
  ReportKind,
  ReportRun,
  ReportSchedule,
  SCHEDULE_LABELS,
} from '../../domain/saved-report.model';

/**
 * Relatórios salvos e entrega programada (RPT-003).
 *
 * <p><strong>O formulário não tem campo de e-mail</strong>, e a ausência é a funcionalidade:
 * destinatário é usuário da plataforma, escolhido de uma lista, porque só de usuário se sabe a
 * alçada. Um campo livre convidaria a mandar dado da fábrica para um endereço que ninguém verificou.
 *
 * <p><strong>Execução recusada aparece como execução, não como erro.</strong> É o caso que a
 * história existe para tornar visível — o dono perdeu a permissão e o relatório parou de sair —, e
 * escondê-lo atrás de um alerta vermelho passageiro faria a fábrica achar que ele continua indo.
 */
@Component({
  selector: 'app-saved-reports-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [SavedReportsStore],
  templateUrl: './saved-reports-page.component.html',
})
export class SavedReportsPageComponent implements OnInit {
  protected readonly store = inject(SavedReportsStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canManage = this.auth.hasPermission('reporting.saved.manage');
  protected readonly kindLabels = KIND_LABELS;
  protected readonly scheduleLabels = SCHEDULE_LABELS;
  protected readonly deliveryLabels = DELIVERY_LABELS;
  protected readonly kinds: ReportKind[] = ['DASHBOARD', 'BATCH_REPORT'];
  protected readonly schedules: ReportSchedule[] = ['MANUAL', 'DAILY', 'WEEKLY', 'MONTHLY'];

  protected readonly defining = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    kind: ['DASHBOARD' as ReportKind, [Validators.required]],
    // O fuso é da definição, não do servidor: "todo dia às 6h" é 6h na fábrica.
    timezone: [Intl.DateTimeFormat().resolvedOptions().timeZone, [Validators.required]],
    schedule: ['MANUAL' as ReportSchedule, [Validators.required]],
    retentionDays: [30, [Validators.required, Validators.min(1), Validators.max(3650)]],
    ownerUserId: ['', [Validators.required]],
    batchId: [''],
  });

  protected readonly recipients = signal<string[]>([]);

  ngOnInit(): void {
    this.store.load();
  }

  protected startDefining(): void {
    this.defining.set(true);
    this.recipients.set([]);
    this.form.reset({
      name: '',
      kind: 'DASHBOARD',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      schedule: 'MANUAL',
      retentionDays: 30,
      ownerUserId: '',
      batchId: '',
    });
  }

  protected cancelDefining(): void {
    this.defining.set(false);
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.store.define({
      name: value.name,
      kind: value.kind,
      filters: value.kind === 'BATCH_REPORT' && value.batchId ? { batchId: value.batchId } : {},
      timezone: value.timezone,
      format: 'JSON',
      schedule: value.schedule,
      retentionDays: value.retentionDays,
      ownerUserId: value.ownerUserId,
      recipients: this.recipients(),
    });
    this.defining.set(false);
  }

  protected toggleRecipient(userId: string): void {
    this.recipients.update(current =>
      current.includes(userId)
        ? current.filter(id => id !== userId)
        : [...current, userId]);
  }

  protected isRecipient(userId: string): boolean {
    return this.recipients().includes(userId);
  }

  protected deliveryClass(status: DeliveryStatus): string {
    return status === 'DELIVERED' ? 'text-bg-success'
      : status === 'REFUSED' ? 'text-bg-danger'
      : 'text-bg-secondary';
  }

  /** O botão de baixar só faz sentido enquanto o artefato existe e não venceu. */
  protected downloadable(run: ReportRun): boolean {
    return run.status === 'SUCCEEDED' && !run.expired;
  }
}
