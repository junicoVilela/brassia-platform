import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { BatchesApi } from '../../../production/data-access/batches.api';
import { Batch } from '../../../production/domain/batch.model';
import { ComplaintsStore } from '../../data-access/complaints.store';
import {
  CATEGORY_LABELS,
  Complaint,
  ComplaintCategory,
  RISK_CATEGORIES,
  SAMPLE_LABELS,
  SEVERITY_CLASSES,
  SEVERITY_HINTS,
  SEVERITY_LABELS,
  STATUS_LABELS,
  SampleStatus,
  Severity,
} from '../../domain/complaint.model';

/**
 * Reclamações de campo (FLD-001).
 *
 * <p><strong>O formulário antecipa o que a classificação vai exigir.</strong> Mostrar "isto exigirá
 * quarentena" enquanto se escolhe a categoria é diferente de descobrir depois de salvar: no primeiro caso
 * a pessoa classifica sabendo a consequência, no segundo ela aprende a evitar a classificação que dá
 * trabalho.
 */
@Component({
  selector: 'app-complaints-page',
  standalone: true,
  imports: [DatePipe, FormsModule],
  providers: [ComplaintsStore],
  templateUrl: './complaints-page.component.html',
})
export class ComplaintsPageComponent implements OnInit {
  private readonly batchesApi = inject(BatchesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly store = inject(ComplaintsStore);

  readonly batches = signal<Batch[]>([]);
  /** Total no servidor quando a lista veio truncada; nulo quando veio inteira. */
  readonly batchesTruncated = signal<number | null>(null);
  readonly severityLabels = SEVERITY_LABELS;
  readonly severityHints = SEVERITY_HINTS;
  readonly severityClasses = SEVERITY_CLASSES;
  readonly categoryLabels = CATEGORY_LABELS;
  readonly sampleLabels = SAMPLE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly severities = Object.keys(SEVERITY_LABELS) as Severity[];
  readonly categories = Object.keys(CATEGORY_LABELS) as ComplaintCategory[];
  readonly sampleStatuses = Object.keys(SAMPLE_LABELS) as SampleStatus[];

  readonly showForm = signal(false);
  readonly batchId = signal('');
  readonly reference = signal('');
  readonly category = signal<ComplaintCategory>('OFF_FLAVOR');
  readonly severity = signal<Severity>('QUALITY');
  readonly description = signal('');
  readonly sampleStatus = signal<SampleStatus>('UNKNOWN');
  readonly sampleLocation = signal('');
  readonly temperature = signal<number | null>(null);
  readonly daysSincePurchase = signal<number | null>(null);
  readonly exposedToLight = signal<boolean | null>(null);
  readonly storageNotes = signal('');
  readonly contactName = signal('');
  readonly contactEmail = signal('');
  readonly contactPhone = signal('');

  readonly waiveFor = signal<string | null>(null);
  readonly waiveAction = signal<string>('');
  readonly justification = signal('');
  readonly fulfillReference = signal('');

  /**
   * O que a classificação atual vai exigir — a mesma regra do servidor, dita antes.
   *
   * Categoria de risco prevalece sobre severidade: é o caso que a pessoa mais tende a subestimar.
   */
  readonly anticipatedActions = computed<string[]>(() => {
    const risco = RISK_CATEGORIES.includes(this.category()) || this.severity() === 'SAFETY';
    if (risco) {
      return ['Quarentenar o lote', 'Abrir investigação de causa (CAPA)'];
    }
    return this.severity() === 'SYSTEMIC' ? ['Abrir investigação de causa (CAPA)'] : [];
  });

  readonly canSubmit = computed(
    () =>
      !!this.batchId() &&
      !!this.description().trim() &&
      (this.sampleStatus() !== 'RETAINED' || !!this.sampleLocation().trim()) &&
      !this.store.registering(),
  );

  ngOnInit(): void {
    this.store.load();
    this.batchesApi
      .listForSelection()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          this.batches.set(page.items);
          // Truncamento fica VISÍVEL: um seletor que mostra 100 de 3.000 sem avisar faz quem procura
          // concluir que o lote não existe.
          this.batchesTruncated.set(page.truncated ? page.total : null);
        },
        error: () => undefined,
      });
  }

  submit(): void {
    const contato =
      this.contactName().trim() || this.contactEmail().trim() || this.contactPhone().trim()
        ? {
            name: this.contactName().trim() || undefined,
            email: this.contactEmail().trim() || undefined,
            phone: this.contactPhone().trim() || undefined,
          }
        : undefined;

    this.store.register(
      {
        batchId: this.batchId(),
        reference: this.reference().trim() || undefined,
        category: this.category(),
        severity: this.severity(),
        description: this.description().trim(),
        storage: {
          temperatureCelsius: this.temperature(),
          daysSincePurchase: this.daysSincePurchase(),
          exposedToLight: this.exposedToLight(),
          notes: this.storageNotes().trim() || null,
        },
        sample: {
          status: this.sampleStatus(),
          location: this.sampleLocation().trim() || null,
        },
        contact: contato,
      },
      () => this.resetForm(),
    );
  }

  openWaiver(complaintId: string, action: string): void {
    this.waiveFor.set(complaintId);
    this.waiveAction.set(action);
    this.justification.set('');
  }

  submitWaiver(): void {
    const id = this.waiveFor();
    if (id) {
      this.store.waive(id, this.waiveAction(), this.justification().trim());
      this.waiveFor.set(null);
    }
  }

  submitFulfillment(complaintId: string, action: string): void {
    const reference = this.fulfillReference().trim();
    if (reference) {
      this.store.fulfill(complaintId, action, reference);
      this.fulfillReference.set('');
    }
  }

  batchLabel(batchId: string): string {
    return this.batches().find(b => b.id === batchId)?.code ?? batchId.slice(0, 8);
  }

  isPending(complaint: Complaint, action: string): boolean {
    return complaint.pendingActions.includes(action);
  }

  private resetForm(): void {
    this.showForm.set(false);
    this.description.set('');
    this.reference.set('');
    this.sampleLocation.set('');
    this.storageNotes.set('');
    this.temperature.set(null);
    this.daysSincePurchase.set(null);
    this.exposedToLight.set(null);
    this.contactName.set('');
    this.contactEmail.set('');
    this.contactPhone.set('');
  }
}
