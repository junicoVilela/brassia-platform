import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { FinishedLotsStore, LotWithShipments } from '../../data-access/finished-lots.store';

/**
 * Lotes de produto acabado e as suas saídas (TRC-001-B e TRC-001-D).
 *
 * <p>A tela existe por causa do recall: é aqui que a metade de fora da fábrica passa a existir. Por
 * isso ela mostra, lote a lote, quantas unidades ainda <strong>não têm destino registrado</strong> —
 * num recall, é exatamente esse número que ninguém sabe onde está.
 */
@Component({
  selector: 'app-finished-lots-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [FinishedLotsStore],
  templateUrl: './finished-lots-page.component.html',
})
export class FinishedLotsPageComponent implements OnInit {
  protected readonly store = inject(FinishedLotsStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canShip = this.auth.hasPermission('packaging.shipment.manage');

  /** Lote cuja expedição está sendo escrita; uma por vez. */
  protected readonly shipping = signal<string | null>(null);

  protected readonly shipmentForm = this.fb.nonNullable.group({
    destination: ['', [Validators.required, Validators.maxLength(200)]],
    contact: ['', [Validators.maxLength(200)]],
    units: [1, [Validators.required, Validators.min(1)]],
    shippedOn: [new Date().toISOString().slice(0, 10), [Validators.required]],
    note: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected startShipment(row: LotWithShipments): void {
    this.shipping.set(row.lot.id);
    this.shipmentForm.reset({
      destination: '',
      contact: '',
      // Sugere o saldo, que é o caso comum: o lote inteiro saindo para um destino só.
      units: row.remaining,
      shippedOn: new Date().toISOString().slice(0, 10),
      note: '',
    });
  }

  protected cancelShipment(): void {
    this.shipping.set(null);
  }

  protected confirmShipment(row: LotWithShipments): void {
    if (this.shipmentForm.invalid) {
      this.shipmentForm.markAllAsTouched();
      return;
    }
    const value = this.shipmentForm.getRawValue();
    this.store.ship({
      finishedLotId: row.lot.id,
      destination: value.destination,
      contact: value.contact || null,
      units: value.units,
      shippedOn: value.shippedOn,
      note: value.note || null,
    });
    this.shipping.set(null);
  }

  protected isShipping(row: LotWithShipments): boolean {
    return this.shipping() === row.lot.id;
  }

  protected busy(row: LotWithShipments): boolean {
    return this.store.saving() === row.lot.id;
  }
}
