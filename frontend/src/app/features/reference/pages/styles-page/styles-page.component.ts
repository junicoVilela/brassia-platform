import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiSearchService } from '../../../../core/search/ui-search.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { StylesStore } from '../../data-access/styles.store';
import { STYLE_AUTHORITIES, StyleAuthority, StyleRange, StyleSet } from '../../domain/reference.model';

@Component({
  selector: 'app-styles-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [StylesStore],
  templateUrl: './styles-page.component.html',
})
export class StylesPageComponent implements OnInit {
  protected readonly store = inject(StylesStore);
  protected readonly search = inject(UiSearchService);
  private readonly fb = inject(FormBuilder);

  protected readonly authorities = STYLE_AUTHORITIES;

  protected readonly filtered = computed(() => {
    const term = this.search.term().trim().toLowerCase();
    const sets = this.store.sets();
    if (!term) {
      return sets;
    }
    return sets.filter(s => `${s.authority} ${s.edition} ${s.language} ${s.status}`.toLowerCase().includes(term));
  });

  protected readonly form = this.fb.nonNullable.group({
    sourceId: ['', Validators.required],
    authority: this.fb.nonNullable.control<StyleAuthority>('BJCP_BEER', Validators.required),
    edition: ['', [Validators.required, Validators.maxLength(40)]],
    language: ['en', [Validators.required, Validators.maxLength(16)]],
    effectiveFrom: ['', Validators.required],
    attribution: '',
    styleCode: ['', Validators.required],
    styleName: ['', Validators.required],
    styleFamily: '',
    ogMin: this.fb.control<number | null>(null),
    ogMax: this.fb.control<number | null>(null),
    ibuMin: this.fb.control<number | null>(null),
    ibuMax: this.fb.control<number | null>(null),
    generalImpression: '',
    detailedProfile: '',
  });

  protected readonly compareForm = this.fb.nonNullable.group({
    styleCode: ['', Validators.required],
    og: this.fb.control<number | null>(null),
    fg: this.fb.control<number | null>(null),
    abv: this.fb.control<number | null>(null),
    ibu: this.fb.control<number | null>(null),
    colorEbc: this.fb.control<number | null>(null),
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected onSelect(setId: string): void {
    this.store.select(setId || null);
    this.compareForm.reset({ styleCode: '', og: null, fg: null, abv: null, ibu: null, colorEbc: null });
  }

  protected canPublish(set: StyleSet): boolean {
    return set.status === 'DRAFT'
      && (set.permissionStatus === 'GRANTED' || set.permissionStatus === 'LIMITED_PERMISSION');
  }

  protected permissionClass(status: string): string {
    switch (status) {
      case 'GRANTED':
        return 'bg-success-subtle text-success-emphasis';
      case 'LIMITED_PERMISSION':
        return 'bg-info-subtle text-info-emphasis';
      case 'DENIED':
        return 'bg-danger-subtle text-danger-emphasis';
      default:
        return 'bg-warning-subtle text-warning-emphasis';
    }
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.create(
      {
        sourceId: v.sourceId,
        authority: v.authority,
        edition: v.edition,
        language: v.language,
        effectiveFrom: toIso(v.effectiveFrom),
        effectiveTo: null,
        attribution: v.attribution || null,
        styles: [
          {
            code: v.styleCode,
            name: v.styleName,
            family: v.styleFamily || null,
            og: range(v.ogMin, v.ogMax, 'SG'),
            ibu: range(v.ibuMin, v.ibuMax, 'IBU'),
            generalImpression: v.generalImpression || null,
            detailedProfile: v.detailedProfile || null,
          },
        ],
      },
      () =>
        this.form.reset({
          sourceId: '',
          authority: 'BJCP_BEER',
          edition: '',
          language: 'en',
          effectiveFrom: '',
          attribution: '',
          styleCode: '',
          styleName: '',
          styleFamily: '',
          ogMin: null,
          ogMax: null,
          ibuMin: null,
          ibuMax: null,
          generalImpression: '',
          detailedProfile: '',
        }),
    );
  }

  protected publish(setId: string): void {
    this.store.publish(setId);
  }

  protected compare(): void {
    const detail = this.store.detail();
    const v = this.compareForm.getRawValue();
    if (!detail || !v.styleCode) {
      return;
    }
    this.store.compare(detail.id, v.styleCode, {
      og: v.og,
      fg: v.fg,
      abv: v.abv,
      ibu: v.ibu,
      colorEbc: v.colorEbc,
    });
  }
}

function range(min: number | null, max: number | null, unit: string): StyleRange {
  return { min, max, unit: min === null && max === null ? null : unit };
}

function toIso(value: string): string {
  return value ? new Date(value).toISOString() : '';
}
