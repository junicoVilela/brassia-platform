import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiSearchService } from '../../../../core/search/ui-search.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { BreweryStore } from '../../data-access/brewery.store';

@Component({
  selector: 'app-breweries-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [BreweryStore],
  templateUrl: './breweries-page.component.html',
})
export class BreweriesPageComponent implements OnInit {
  protected readonly store = inject(BreweryStore);
  protected readonly search = inject(UiSearchService);
  private readonly fb = inject(FormBuilder);

  protected readonly filtered = computed(() => {
    const term = this.search.term().trim().toLowerCase();
    const items = this.store.items();
    if (!term) {
      return items;
    }
    return items.filter(b => `${b.code} ${b.name} ${b.timezone}`.toLowerCase().includes(term));
  });

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    timezone: ['America/Sao_Paulo', [Validators.required, Validators.maxLength(80)]],
  });

  protected readonly prefsForm = this.fb.nonNullable.group({
    volumeUnit: ['L', Validators.required],
    massUnit: ['KG', Validators.required],
    temperatureUnit: ['C', Validators.required],
    currencyCode: ['BRL', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    maxBatchVolume: [1000, [Validators.required, Validators.min(0.000001)]],
    allowNegativeStock: [false],
    stockPolicy: ['FEFO', Validators.required],
    version: [0, Validators.required],
  });

  constructor() {
    effect(() => {
      const prefs = this.store.preferences();
      if (!prefs) {
        return;
      }
      this.prefsForm.setValue({
        volumeUnit: prefs.volumeUnit,
        massUnit: prefs.massUnit,
        temperatureUnit: prefs.temperatureUnit,
        currencyCode: prefs.currencyCode,
        maxBatchVolume: prefs.maxBatchVolume,
        allowNegativeStock: prefs.allowNegativeStock,
        stockPolicy: prefs.stockPolicy,
        version: prefs.version,
      });
    });
  }

  ngOnInit(): void {
    this.store.load();
  }

  protected register(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.register(this.form.getRawValue(), () => this.form.reset({ timezone: 'America/Sao_Paulo' }));
  }

  protected savePreferences(): void {
    if (this.prefsForm.invalid) {
      return;
    }
    this.store.savePreferences(this.prefsForm.getRawValue());
  }
}
