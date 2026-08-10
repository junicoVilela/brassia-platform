import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { BatchesApi } from '../../../production/data-access/batches.api';
import { Batch } from '../../../production/domain/batch.model';
import { BlendsStore } from '../../data-access/blends.store';
import {
  BlendKind,
  KIND_LABELS,
  STATUS_CLASSES,
  STATUS_LABELS,
  TOLERANCE_LITERS,
} from '../../domain/blend.model';

interface MovementRow {
  batchId: string;
  liters: number | null;
}

/**
 * União e divisão de volume (BLD-001).
 *
 * <p><strong>O balanço é calculado na tela enquanto se digita.</strong> É o mesmo critério do servidor,
 * dito antes de enviar — e aqui a antecipação vale mais do que no formulário de experimento: quem monta
 * uma união está lidando com volumes medidos em tanque, e ver a diferença aparecer em tempo real é o que
 * revela um erro de leitura antes de ele virar uma operação aprovada.
 */
@Component({
  selector: 'app-blends-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  providers: [BlendsStore],
  templateUrl: './blends-page.component.html',
})
export class BlendsPageComponent implements OnInit {
  private readonly batchesApi = inject(BatchesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly store = inject(BlendsStore);

  readonly batches = signal<Batch[]>([]);
  /** Total no servidor quando a lista veio truncada; nulo quando veio inteira. */
  readonly batchesTruncated = signal<number | null>(null);
  readonly kindLabels = KIND_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusClasses = STATUS_CLASSES;

  readonly showForm = signal(false);
  readonly kind = signal<BlendKind>('MERGE');
  readonly reason = signal('');
  readonly declaredLoss = signal<number>(0);
  readonly inputs = signal<MovementRow[]>([
    { batchId: '', liters: null },
    { batchId: '', liters: null },
  ]);
  readonly outputs = signal<MovementRow[]>([{ batchId: '', liters: null }]);

  readonly inputTotal = computed(() => total(this.inputs()));
  readonly outputTotal = computed(() => total(this.outputs()));

  /** Positivo: sumiu cerveja. Negativo: apareceu cerveja. */
  readonly difference = computed(
    () => this.inputTotal() - this.outputTotal() - (this.declaredLoss() || 0),
  );

  readonly balanced = computed(() => Math.abs(this.difference()) <= TOLERANCE_LITERS);

  readonly canSubmit = computed(
    () =>
      this.balanced() &&
      !!this.reason().trim() &&
      this.filled(this.inputs()).length >= (this.kind() === 'MERGE' ? 2 : 1) &&
      this.filled(this.outputs()).length >= (this.kind() === 'SPLIT' ? 2 : 1) &&
      !this.store.simulating(),
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

  onKindChange(kind: BlendKind): void {
    this.kind.set(kind);
    // A forma muda com o tipo: união parte de dois, divisão chega a dois. Deixar o formulário na forma
    // anterior faria a recusa vir do servidor por algo que a tela já sabia.
    if (kind === 'MERGE') {
      this.inputs.set([
        { batchId: '', liters: null },
        { batchId: '', liters: null },
      ]);
      this.outputs.set([{ batchId: '', liters: null }]);
    } else {
      this.inputs.set([{ batchId: '', liters: null }]);
      this.outputs.set([
        { batchId: '', liters: null },
        { batchId: '', liters: null },
      ]);
    }
  }

  addInput(): void {
    this.inputs.update(rows => [...rows, { batchId: '', liters: null }]);
  }

  addOutput(): void {
    this.outputs.update(rows => [...rows, { batchId: '', liters: null }]);
  }

  updateInput(index: number, field: keyof MovementRow, value: string): void {
    this.inputs.update(rows => patch(rows, index, field, value));
  }

  updateOutput(index: number, field: keyof MovementRow, value: string): void {
    this.outputs.update(rows => patch(rows, index, field, value));
  }

  removeInput(index: number): void {
    this.inputs.update(rows => rows.filter((_, i) => i !== index));
  }

  removeOutput(index: number): void {
    this.outputs.update(rows => rows.filter((_, i) => i !== index));
  }

  submit(): void {
    this.store.simulate(
      {
        kind: this.kind(),
        inputs: this.filled(this.inputs()).map(r => ({ batchId: r.batchId, liters: r.liters! })),
        outputs: this.filled(this.outputs()).map(r => ({ batchId: r.batchId, liters: r.liters! })),
        declaredLossLiters: this.declaredLoss() || 0,
        reason: this.reason().trim(),
      },
      () => this.resetForm(),
    );
  }

  batchLabel(batchId: string): string {
    return this.batches().find(b => b.id === batchId)?.code ?? batchId.slice(0, 8);
  }

  private filled(rows: MovementRow[]): MovementRow[] {
    return rows.filter(r => r.batchId && r.liters !== null && r.liters > 0);
  }

  private resetForm(): void {
    this.showForm.set(false);
    this.reason.set('');
    this.declaredLoss.set(0);
    this.onKindChange(this.kind());
  }
}

function total(rows: MovementRow[]): number {
  return rows.reduce((sum, row) => sum + (row.liters ?? 0), 0);
}

function patch(
  rows: MovementRow[],
  index: number,
  field: keyof MovementRow,
  value: string,
): MovementRow[] {
  return rows.map((row, i) => {
    if (i !== index) {
      return row;
    }
    // Campo de litros vazio vira null e não 0: zero litro é um volume declarado, vazio é "não informado",
    // e tratar os dois igual faria o balanço fechar com uma linha em branco.
    return field === 'liters'
      ? { ...row, liters: value === '' ? null : Number(value) }
      : { ...row, batchId: value };
  });
}
