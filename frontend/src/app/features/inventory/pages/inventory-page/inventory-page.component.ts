import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import {
  LOT_PROPERTY_CONFIDENCES,
  LOT_PROPERTY_SOURCES,
  LotPropertyConfidence,
  LotPropertySource,
  MOVEMENT_TYPES,
  MovementType,
  STOCK_UNITS,
  StockInspection,
  StockUnit,
} from '../../domain/stock-lot.model';
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
  private readonly auth = inject(AuthService);
  protected readonly canOverrideStock = this.auth.hasPermission('inventory.stock.override');
  protected readonly units = STOCK_UNITS;
  protected readonly movementTypes = MOVEMENT_TYPES;
  protected readonly propertySources = LOT_PROPERTY_SOURCES;
  protected readonly propertyConfidences = LOT_PROPERTY_CONFIDENCES;

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

  protected readonly movementForm = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<MovementType>('CONSUMPTION', Validators.required),
    quantity: [0, [Validators.required, Validators.min(0.0001)]],
    reason: [''],
    allowNegative: [false],
  });

  protected readonly reserveForm = this.fb.nonNullable.group({
    ingredientId: ['', Validators.required],
    quantity: [0, [Validators.required, Validators.min(0.0001)]],
    unit: this.fb.nonNullable.control<StockUnit>('KG', Validators.required),
    orderId: [''],
  });

  protected readonly propertyForm = this.fb.nonNullable.group({
    property: ['', [Validators.required, Validators.maxLength(60)]],
    value: [0, [Validators.required]],
    unit: [''],
    source: this.fb.nonNullable.control<LotPropertySource>('MANUAL', Validators.required),
    confidence: this.fb.nonNullable.control<LotPropertyConfidence>('HIGH', Validators.required),
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected startMovements(lotId: string): void {
    this.movementForm.reset({ type: 'CONSUMPTION', quantity: 0, reason: '', allowNegative: false });
    this.store.showMovements(lotId);
  }

  protected startProperties(lotId: string): void {
    this.propertyForm.reset({ property: '', value: 0, unit: '', source: 'MANUAL', confidence: 'HIGH' });
    this.store.showProperties(lotId);
  }

  protected recordProperty(lotId: string): void {
    if (this.propertyForm.invalid) {
      return;
    }
    const v = this.propertyForm.getRawValue();
    this.store.recordProperty(lotId,
      { property: v.property, value: v.value, unit: v.unit || null, source: v.source, confidence: v.confidence },
      () => this.propertyForm.reset({ property: '', value: 0, unit: '', source: 'MANUAL', confidence: 'HIGH' }));
  }

  protected record(lotId: string): void {
    if (this.movementForm.invalid) {
      return;
    }
    const v = this.movementForm.getRawValue();
    this.store.recordMovement(lotId,
      { type: v.type, quantity: v.quantity, reason: v.reason || null, allowNegative: v.allowNegative },
      () => this.movementForm.reset({ type: 'CONSUMPTION', quantity: 0, reason: '', allowNegative: false }));
  }

  protected reserve(): void {
    if (this.reserveForm.invalid) {
      return;
    }
    const v = this.reserveForm.getRawValue();
    this.store.reserve({ ingredientId: v.ingredientId, quantity: v.quantity, unit: v.unit, orderId: v.orderId || null },
      () => this.reserveForm.reset({ ingredientId: '', quantity: 0, unit: 'KG', orderId: '' }));
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
