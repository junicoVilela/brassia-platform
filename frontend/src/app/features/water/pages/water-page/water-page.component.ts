import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { WaterStore } from '../../data-access/water.store';
import { WATER_METHODS, WaterMethod } from '../../domain/water.model';

@Component({
  selector: 'app-water-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [WaterStore],
  templateUrl: './water-page.component.html',
})
export class WaterPageComponent implements OnInit {
  protected readonly store = inject(WaterStore);
  private readonly fb = inject(FormBuilder);

  protected readonly methods = WATER_METHODS;

  protected readonly sourceForm = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
  });

  protected readonly reportForm = this.fb.nonNullable.group({
    collectedOn: ['', Validators.required],
    method: this.fb.nonNullable.control<WaterMethod>('LAB', Validators.required),
    calcium: [0, [Validators.required, Validators.min(0)]],
    magnesium: [0, [Validators.required, Validators.min(0)]],
    sodium: [0, [Validators.required, Validators.min(0)]],
    sulfate: [0, [Validators.required, Validators.min(0)]],
    chloride: [0, [Validators.required, Validators.min(0)]],
    bicarbonate: [0, [Validators.required, Validators.min(0)]],
  });

  protected readonly refForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    region: '',
    edition: ['', Validators.required],
    calcium: [0, [Validators.required, Validators.min(0)]],
    magnesium: [0, [Validators.required, Validators.min(0)]],
    sodium: [0, [Validators.required, Validators.min(0)]],
    sulfate: [0, [Validators.required, Validators.min(0)]],
    chloride: [0, [Validators.required, Validators.min(0)]],
    bicarbonate: [0, [Validators.required, Validators.min(0)]],
    alkalinity: this.fb.control<number | null>(null),
    hardness: this.fb.control<number | null>(null),
    ph: this.fb.control<number | null>(null),
    sourceName: '',
  });

  ngOnInit(): void {
    this.store.loadSources();
    this.store.loadReferenceProfiles();
  }

  protected onSelect(sourceId: string): void {
    this.store.select(sourceId || null);
  }

  protected createReferenceProfile(): void {
    if (this.refForm.invalid) {
      return;
    }
    const v = this.refForm.getRawValue();
    this.store.createReferenceProfile(
      {
        name: v.name,
        region: v.region || null,
        edition: v.edition,
        calcium: v.calcium,
        magnesium: v.magnesium,
        sodium: v.sodium,
        sulfate: v.sulfate,
        chloride: v.chloride,
        bicarbonate: v.bicarbonate,
        alkalinity: v.alkalinity,
        hardness: v.hardness,
        ph: v.ph,
        sourceId: null,
        sourceName: v.sourceName || null,
      },
      () => this.refForm.reset({
        name: '', region: '', edition: '', calcium: 0, magnesium: 0, sodium: 0, sulfate: 0, chloride: 0,
        bicarbonate: 0, alkalinity: null, hardness: null, ph: null, sourceName: '',
      }),
    );
  }

  protected publishReferenceProfile(id: string): void {
    this.store.publishReferenceProfile(id);
  }

  protected createSource(): void {
    if (this.sourceForm.invalid) {
      return;
    }
    this.store.createSource(this.sourceForm.getRawValue(), () => this.sourceForm.reset({ code: '', name: '' }));
  }

  protected recordReport(): void {
    if (this.reportForm.invalid) {
      return;
    }
    this.store.recordReport(this.reportForm.getRawValue(), () =>
      this.reportForm.reset({
        collectedOn: '',
        method: 'LAB',
        calcium: 0,
        magnesium: 0,
        sodium: 0,
        sulfate: 0,
        chloride: 0,
        bicarbonate: 0,
      }),
    );
  }
}
