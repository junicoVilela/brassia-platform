import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiSearchService } from '../../../../core/search/ui-search.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { EquipmentStore } from '../../data-access/equipment.store';

@Component({
  selector: 'app-equipment-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [EquipmentStore],
  templateUrl: './equipment-page.component.html',
})
export class EquipmentPageComponent implements OnInit {
  protected readonly store = inject(EquipmentStore);
  protected readonly search = inject(UiSearchService);
  private readonly fb = inject(FormBuilder);

  protected readonly filtered = computed(() => {
    const term = this.search.term().trim().toLowerCase();
    const items = this.store.items();
    if (!term) {
      return items;
    }
    return items.filter(e => `${e.code} ${e.name}`.toLowerCase().includes(term));
  });

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
