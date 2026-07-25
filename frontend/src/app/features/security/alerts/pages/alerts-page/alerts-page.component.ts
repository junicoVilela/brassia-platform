import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { EmptyStateComponent } from '../../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../../shared/ui/page-header.component';
import { AlertsStore } from '../../data-access/alerts.store';

@Component({
  selector: 'app-alerts-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [AlertsStore],
  templateUrl: './alerts-page.component.html',
})
export class AlertsPageComponent implements OnInit {
  protected readonly store = inject(AlertsStore);

  protected readonly statuses = ['OPEN', 'ACKNOWLEDGED', 'RESOLVED'];

  ngOnInit(): void {
    this.store.load();
  }

  protected onFilter(value: string): void {
    this.store.filterByStatus(value || null);
  }

  protected severityClass(severity: string): string {
    switch (severity) {
      case 'HIGH': return 'bg-danger-subtle text-danger-emphasis';
      case 'MEDIUM': return 'bg-warning-subtle text-warning-emphasis';
      default: return 'bg-secondary-subtle text-secondary-emphasis';
    }
  }

  protected statusClass(status: string): string {
    switch (status) {
      case 'OPEN': return 'bg-danger-subtle text-danger-emphasis';
      case 'ACKNOWLEDGED': return 'bg-warning-subtle text-warning-emphasis';
      case 'RESOLVED': return 'bg-success-subtle text-success-emphasis';
      default: return 'bg-secondary-subtle text-secondary-emphasis';
    }
  }
}
