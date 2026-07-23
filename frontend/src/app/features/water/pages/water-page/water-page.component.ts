import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { WaterStore } from '../../data-access/water.store';
import { WATER_METHODS, WaterMethod } from '../../domain/water.model';

@Component({
  selector: 'app-water-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
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

  ngOnInit(): void {
    this.store.loadSources();
  }

  protected onSelect(sourceId: string): void {
    this.store.select(sourceId || null);
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
