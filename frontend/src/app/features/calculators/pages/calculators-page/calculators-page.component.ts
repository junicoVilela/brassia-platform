import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { CalculatorsStore } from '../../data-access/calculators.store';

@Component({
  selector: 'app-calculators-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [CalculatorsStore],
  templateUrl: './calculators-page.component.html',
})
export class CalculatorsPageComponent implements OnInit {
  protected readonly store = inject(CalculatorsStore);

  /** Form dinâmico, reconstruído a cada calculadora escolhida. */
  protected readonly form = signal(new FormGroup({}));

  ngOnInit(): void {
    this.store.load();
  }

  protected selectCalculator(id: string): void {
    this.store.select(id);
    const spec = this.store.selected();
    const controls: Record<string, FormControl<number | null>> = {};
    for (const key of spec?.inputs ?? []) {
      controls[key] = new FormControl<number | null>(null, Validators.required);
    }
    this.form.set(new FormGroup(controls));
  }

  protected compute(): void {
    const id = this.store.selectedId();
    const group = this.form();
    if (!id || group.invalid) {
      return;
    }
    this.store.compute(id, group.getRawValue() as Record<string, number>);
  }
}
