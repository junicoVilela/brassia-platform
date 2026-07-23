import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BlendStore } from '../../data-access/blend.store';
import { SimulateBlendRequest } from '../../domain/blend.model';

@Component({
  selector: 'app-blend-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [BlendStore],
  templateUrl: './blend-page.component.html',
})
export class BlendPageComponent implements OnInit {
  protected readonly store = inject(BlendStore);
  private readonly fb = inject(FormBuilder);

  protected readonly ionKeys = ['calcium', 'magnesium', 'sodium', 'sulfate', 'chloride', 'bicarbonate'] as const;
  protected readonly ionLabel: Record<string, string> = {
    calcium: 'Ca', magnesium: 'Mg', sodium: 'Na', sulfate: 'SO₄', chloride: 'Cl', bicarbonate: 'HCO₃',
  };

  protected readonly profileForm = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    calcium: [0, [Validators.required, Validators.min(0)]],
    magnesium: [0, [Validators.required, Validators.min(0)]],
    sodium: [0, [Validators.required, Validators.min(0)]],
    sulfate: [0, [Validators.required, Validators.min(0)]],
    chloride: [0, [Validators.required, Validators.min(0)]],
    bicarbonate: [0, [Validators.required, Validators.min(0)]],
  });

  protected readonly blendForm = this.fb.nonNullable.group({
    inputs: this.fb.array([this.newRow(), this.newRow()]),
    targetProfileId: '',
  });

  get inputs(): FormArray {
    return this.blendForm.get('inputs') as FormArray;
  }

  ngOnInit(): void {
    this.store.load();
  }

  private newRow() {
    return this.fb.nonNullable.group({
      sourceId: ['', Validators.required],
      volumeLiters: [0, [Validators.required, Validators.min(0.001)]],
    });
  }

  protected addRow(): void {
    this.inputs.push(this.newRow());
  }

  protected removeRow(index: number): void {
    if (this.inputs.length > 1) {
      this.inputs.removeAt(index);
    }
  }

  protected createProfile(): void {
    if (this.profileForm.invalid) {
      return;
    }
    this.store.createProfile(this.profileForm.getRawValue(), () =>
      this.profileForm.reset({
        code: '', name: '', calcium: 0, magnesium: 0, sodium: 0, sulfate: 0, chloride: 0, bicarbonate: 0,
      }),
    );
  }

  protected simulate(): void {
    if (this.blendForm.invalid) {
      return;
    }
    const raw = this.blendForm.getRawValue();
    const request: SimulateBlendRequest = {
      inputs: raw.inputs.map(i => ({ sourceId: i.sourceId, volumeLiters: i.volumeLiters })),
      targetProfileId: raw.targetProfileId || null,
    };
    this.store.simulate(request);
  }
}
