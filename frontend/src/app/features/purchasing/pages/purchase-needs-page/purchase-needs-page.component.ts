import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { PurchaseNeedsStore } from '../../data-access/purchase-needs.store';

@Component({
  selector: 'app-purchase-needs-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [PurchaseNeedsStore],
  templateUrl: './purchase-needs-page.component.html',
})
export class PurchaseNeedsPageComponent implements OnInit {
  protected readonly store = inject(PurchaseNeedsStore);

  ngOnInit(): void {
    this.store.load();
  }
}
