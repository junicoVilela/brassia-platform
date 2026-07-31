import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
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

  ngOnInit(): void {
    this.store.load();
    this.store.loadBatches();
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
