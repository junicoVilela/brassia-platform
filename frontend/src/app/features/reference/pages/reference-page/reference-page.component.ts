import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiSearchService } from '../../../../core/search/ui-search.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ReferenceStore } from '../../data-access/reference.store';
import {
  ImportJob,
  PERMISSION_STATUSES,
  PermissionStatus,
  ReferenceDataset,
  SOURCE_TYPES,
  SourceType,
  allowsPublish,
} from '../../domain/reference.model';

@Component({
  selector: 'app-reference-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [ReferenceStore],
  templateUrl: './reference-page.component.html',
})
export class ReferencePageComponent implements OnInit {
  protected readonly store = inject(ReferenceStore);
  protected readonly search = inject(UiSearchService);
  private readonly fb = inject(FormBuilder);

  protected readonly types = SOURCE_TYPES;
  protected readonly permissions = PERMISSION_STATUSES;

  protected readonly filtered = computed(() => {
    const term = this.search.term().trim().toLowerCase();
    const sources = this.store.sources();
    if (!term) {
      return sources;
    }
    return sources.filter(s => `${s.name} ${s.owner} ${s.type} ${s.permissionStatus}`.toLowerCase().includes(term));
  });

  protected readonly sourceForm = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<SourceType>('OFFICIAL_STANDARD', Validators.required),
    name: ['', [Validators.required, Validators.maxLength(160)]],
    owner: ['', [Validators.required, Validators.maxLength(160)]],
    url: '',
    licenseName: ['', [Validators.required, Validators.maxLength(160)]],
    permissionStatus: this.fb.nonNullable.control<PermissionStatus>('LIMITED_PERMISSION', Validators.required),
    attribution: '',
    reviewFrequency: '',
    responsible: '',
  });

  protected readonly datasetForm = this.fb.nonNullable.group({
    datasetVersion: ['', [Validators.required, Validators.maxLength(60)]],
    sourceSystem: ['', [Validators.required, Validators.maxLength(160)]],
    rawPayload: ['', Validators.required],
    retrievedAt: ['', Validators.required],
    effectiveFrom: ['', Validators.required],
    effectiveTo: '',
  });

  protected readonly jobForm = this.fb.nonNullable.group({
    datasetVersion: ['', [Validators.required, Validators.maxLength(60)]],
    contentType: ['application/json', Validators.required],
    rawPayload: ['', Validators.required],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected onSelect(sourceId: string): void {
    this.store.select(sourceId || null);
  }

  protected canPublish(dataset: ReferenceDataset): boolean {
    const source = this.store.selected();
    return dataset.status === 'DRAFT' && !!source && allowsPublish(source.permissionStatus);
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

  protected register(): void {
    if (this.sourceForm.invalid) {
      return;
    }
    const v = this.sourceForm.getRawValue();
    this.store.registerSource(
      {
        type: v.type,
        name: v.name,
        owner: v.owner,
        url: v.url || null,
        licenseName: v.licenseName,
        permissionStatus: v.permissionStatus,
        attribution: v.attribution || null,
        reviewFrequency: v.reviewFrequency || null,
        responsible: v.responsible || null,
      },
      () =>
        this.sourceForm.reset({
          type: 'OFFICIAL_STANDARD',
          permissionStatus: 'LIMITED_PERMISSION',
          name: '',
          owner: '',
          url: '',
          licenseName: '',
          attribution: '',
          reviewFrequency: '',
          responsible: '',
        }),
    );
  }

  protected recordDataset(): void {
    if (this.datasetForm.invalid) {
      return;
    }
    const v = this.datasetForm.getRawValue();
    this.store.recordDataset(
      {
        datasetVersion: v.datasetVersion,
        rawPayload: v.rawPayload,
        sourceSystem: v.sourceSystem,
        sourceRecordId: null,
        sourceUrl: null,
        retrievedAt: toIso(v.retrievedAt),
        effectiveFrom: toIso(v.effectiveFrom),
        effectiveTo: v.effectiveTo ? toIso(v.effectiveTo) : null,
      },
      () => this.datasetForm.reset({ datasetVersion: '', sourceSystem: '', rawPayload: '', retrievedAt: '', effectiveFrom: '', effectiveTo: '' }),
    );
  }

  protected publish(datasetId: string): void {
    this.store.publish(datasetId);
  }

  protected canPublishJob(job: ImportJob): boolean {
    const source = this.store.selected();
    return job.status === 'REVIEW_REQUIRED' && !!source && allowsPublish(source.permissionStatus);
  }

  protected submitJob(): void {
    if (this.jobForm.invalid) {
      return;
    }
    const v = this.jobForm.getRawValue();
    this.store.submitJob(
      { datasetVersion: v.datasetVersion, contentType: v.contentType, rawPayload: v.rawPayload },
      () => this.jobForm.reset({ datasetVersion: '', contentType: 'application/json', rawPayload: '' }),
    );
  }

  protected publishJob(jobId: string): void {
    this.store.publishJob(jobId);
  }
}

/** Converte um valor de `datetime-local` para ISO-8601 (Instant esperado pela API). */
function toIso(value: string): string {
  return value ? new Date(value).toISOString() : '';
}
