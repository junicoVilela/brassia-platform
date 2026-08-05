import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { RecallsStore } from '../../data-access/recalls.store';
import { NODE_ICONS, NODE_LABELS } from '../../domain/genealogy.model';
import { Recall, RecallNotification } from '../../domain/recall.model';

/**
 * Recalls (FDS-003).
 *
 * <p>O dossiê tem duas metades que a tela não pode misturar: a lista de destinos comunicados, que é
 * registro do que a cervejaria fez, e o escopo, que é derivado do grafo agora. Entre elas fica a
 * terceira coisa, a que mais importa numa auditoria — o que <em>não</em> se sabe: destinos
 * descobertos depois da abertura e lotes que saíram sem ninguém registrar para onde.
 */
@Component({
  selector: 'app-recalls-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [RecallsStore],
  templateUrl: './recalls-page.component.html',
})
export class RecallsPageComponent implements OnInit {
  protected readonly store = inject(RecallsStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canManage = this.auth.hasPermission('traceability.recall.manage');
  protected readonly nodeLabels = NODE_LABELS;
  protected readonly nodeIcons = NODE_ICONS;

  /** Destino cuja comunicação está sendo registrada. */
  protected readonly notifying = signal<string | null>(null);
  protected readonly closing = signal<string | null>(null);

  protected readonly notifyForm = this.fb.nonNullable.group({
    channel: ['telefone', [Validators.required, Validators.maxLength(40)]],
    note: ['', [Validators.maxLength(500)]],
  });

  protected readonly closeForm = this.fb.nonNullable.group({
    summary: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected select(recall: Recall): void {
    this.store.select(recall.id);
  }

  protected isOpenDossier(recall: Recall): boolean {
    return this.store.dossier()?.recall.id === recall.id;
  }

  protected startNotify(notification: RecallNotification): void {
    this.notifying.set(notification.id);
    this.notifyForm.reset({ channel: 'telefone', note: '' });
  }

  protected cancelNotify(): void {
    this.notifying.set(null);
  }

  protected confirmNotify(recall: Recall, notification: RecallNotification): void {
    if (this.notifyForm.invalid) {
      this.notifyForm.markAllAsTouched();
      return;
    }
    const value = this.notifyForm.getRawValue();
    this.store.notify(recall.id, notification.id, value.channel, value.note || null);
    this.notifying.set(null);
  }

  protected startClose(recall: Recall): void {
    this.closing.set(recall.id);
    this.closeForm.reset({ summary: '' });
  }

  protected cancelClose(): void {
    this.closing.set(null);
  }

  protected confirmClose(recall: Recall): void {
    if (this.closeForm.invalid) {
      this.closeForm.markAllAsTouched();
      return;
    }
    this.store.close(recall.id, this.closeForm.getRawValue().summary);
    this.closing.set(null);
  }

  protected busy(key: string): boolean {
    return this.store.saving() === key;
  }
}
