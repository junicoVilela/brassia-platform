import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { STOCK_UNITS, StockInspection, StockUnit } from '../../domain/stock-lot.model';
import { InventoryStore } from '../../data-access/inventory.store';

@Component({
  selector: 'app-inventory-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [InventoryStore],
  templateUrl: './inventory-page.component.html',
})
export class InventoryPageComponent implements OnInit {
  protected readonly store = inject(InventoryStore);
  private readonly fb = inject(FormBuilder);
  protected readonly units = STOCK_UNITS;

  protected readonly form = this.fb.nonNullable.group({
    ingredientId: ['', Validators.required],
    supplierId: ['', Validators.required],
    supplierLotCode: [''],
    quantity: [0, [Validators.required, Validators.min(0.0001)]],
    unit: this.fb.nonNullable.control<StockUnit>('KG', Validators.required),
    unitCost: [0, [Validators.required, Validators.min(0)]],
    expiryDate: [''],
    inspection: this.fb.nonNullable.control<StockInspection>('APPROVED', Validators.required),
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected receive(): void {
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.receive({
      ingredientId: v.ingredientId,
      supplierId: v.supplierId,
      supplierLotCode: v.supplierLotCode || null,
      quantity: v.quantity,
      unit: v.unit,
      unitCost: v.unitCost,
      expiryDate: v.expiryDate || null,
      inspection: v.inspection,
    }, () => this.form.reset({ quantity: 0, unit: 'KG', unitCost: 0, inspection: 'APPROVED' }));
  }
}
