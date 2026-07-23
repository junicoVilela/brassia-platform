import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EquipmentStore } from '../../data-access/equipment.store';

@Component({
  selector: 'app-equipment-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [EquipmentStore],
  templateUrl: './equipment-page.component.html',
})
export class EquipmentPageComponent implements OnInit {
  protected readonly store = inject(EquipmentStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    capacityLiters: [0, [Validators.required, Validators.min(0.001)]],
    deadSpaceLiters: [0, [Validators.required, Validators.min(0)]],
    mashEfficiencyPercent: [70, [Validators.required, Validators.min(0.01), Validators.max(100)]],
    boilOffLitersPerHour: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create(this.form.getRawValue(), () =>
      this.form.reset({
        code: '',
        name: '',
        capacityLiters: 0,
        deadSpaceLiters: 0,
        mashEfficiencyPercent: 70,
        boilOffLitersPerHour: 0,
      }),
    );
  }
}
