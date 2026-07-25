import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { EmptyStateComponent } from '../../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../../shared/ui/page-header.component';
import { AuditStore } from '../../data-access/audit.store';
import { AuditFilter } from '../../domain/audit-event.model';

@Component({
  selector: 'app-audit-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [AuditStore],
  templateUrl: './audit-page.component.html',
})
export class AuditPageComponent implements OnInit {
  protected readonly store = inject(AuditStore);

  ngOnInit(): void {
    this.store.init();
  }

  protected apply(patch: Partial<AuditFilter>): void {
    this.store.applyFilter(patch);
  }

  protected outcomeClass(outcome: string): string {
    return outcome === 'SUCCESS'
      ? 'bg-success-subtle text-success-emphasis'
      : 'bg-danger-subtle text-danger-emphasis';
  }
}
