import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { YeastStore } from '../../data-access/yeast.store';
import { YEAST_STATUS_LABELS } from '../../domain/yeast.model';

@Component({
  selector: 'app-yeast-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [YeastStore],
  templateUrl: './yeast-page.component.html',
})
export class YeastPageComponent implements OnInit {
  protected readonly store = inject(YeastStore);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly canManage = this.auth.hasPermission('fermentation.yeast.manage');
  protected readonly canManagePolicy = this.auth.hasPermission('fermentation.yeast.policy.manage');
  protected readonly statusLabels = YEAST_STATUS_LABELS;

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    strainId: ['', Validators.required],
    sourceBatchId: ['', Validators.required],
    // Vazio = levedura comprada (geração 1). A geração nunca é digitada.
    parentHarvestId: [''],
    harvestedAt: ['', Validators.required],
    viabilityPercent: this.fb.control<number | null>(null, [Validators.required, Validators.min(0),
      Validators.max(100)]),
    condition: ['', [Validators.required, Validators.maxLength(200)]],
    storageLocation: ['', [Validators.required, Validators.maxLength(120)]],
    storageTempC: this.fb.control<number | null>(null, Validators.required),
  });

  protected readonly policyForm = this.fb.nonNullable.group({
    maxGeneration: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    maxAgeDays: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    minViabilityPercent: this.fb.control<number | null>(null, [Validators.required, Validators.min(0),
      Validators.max(100)]),
  });

  /** Lote de destino do repitch, escolhido explicitamente antes de confirmar o uso. */
  protected readonly targetBatchId = signal<string>('');

  // Espelha a política vigente no formulário assim que ela chega do servidor.
  private readonly syncPolicyForm = effect(() => {
    const policy = this.store.policy();
    if (policy) {
      this.policyForm.patchValue(policy, { emitEvent: false });
    }
  });

  ngOnInit(): void {
    this.store.load();
    this.store.loadBatches();
    this.store.recommend();
  }

  protected savePolicy(): void {
    if (this.policyForm.invalid) {
      return;
    }
    const v = this.policyForm.getRawValue();
    this.store.savePolicy({
      maxGeneration: v.maxGeneration!,
      maxAgeDays: v.maxAgeDays!,
      minViabilityPercent: v.minViabilityPercent!,
    });
  }

  /** Confirmação explícita antes de consumir a coleta, no lote escolhido. */
  protected confirmUse(harvestId: string, code: string): void {
    const batchId = this.targetBatchId();
    if (!batchId) {
      return;
    }
    const batch = this.store.batches().find(b => b.id === batchId);
    if (window.confirm(`Confirmar o uso da coleta ${code} no lote ${batch?.code ?? batchId}?`
        + ' Ela será consumida e não poderá ser pitchada de novo.')) {
      this.store.use(harvestId, batchId);
    }
  }

  protected collect(): void {
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.collect(
      {
        code: v.code,
        strainId: v.strainId,
        sourceBatchId: v.sourceBatchId,
        parentHarvestId: v.parentHarvestId || null,
        harvestedAt: new Date(v.harvestedAt).toISOString(),
        viabilityPercent: v.viabilityPercent!,
        condition: v.condition,
        storageLocation: v.storageLocation,
        storageTempC: v.storageTempC!,
      },
      () => this.form.reset({ code: '', strainId: '', sourceBatchId: '', parentHarvestId: '', harvestedAt: '',
        viabilityPercent: null, condition: '', storageLocation: '', storageTempC: null }),
    );
  }

  /** Reprovar exige motivo — o domínio recusa sem ele, então pedimos aqui. */
  protected reject(harvestId: string): void {
    const reason = window.prompt('Motivo da reprovação (contaminação, odor, viabilidade baixa):');
    if (reason && reason.trim()) {
      this.store.review(harvestId, false, reason.trim());
    }
  }
}
