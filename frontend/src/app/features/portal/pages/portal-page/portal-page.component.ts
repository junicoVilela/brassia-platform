import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { PortalStore } from '../../data-access/portal.store';
import { CatalogItem, PortalOrder } from '../../domain/portal.model';

/**
 * O portal do cliente (SAL-003).
 *
 * <p>A tela mostra <strong>o que dá para pedir agora</strong>, e não um catálogo completo com avisos: um
 * item sem preço ou sem disponibilidade nem chega aqui, porque pedir e ser recusado é pior do que não
 * ver o item — e no portal não há um vendedor por perto para explicar.
 *
 * <p><strong>O teto aparece antes da compra, e não na recusa.</strong> Descobrir que não há crédito
 * depois de montar o pedido é o tipo de coisa que faz o cliente ligar para reclamar; mostrar quanto
 * ainda cabe deixa a decisão com ele.
 */
@Component({
  selector: 'app-portal-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [PortalStore],
  templateUrl: './portal-page.component.html',
})
export class PortalPageComponent implements OnInit {
  protected readonly store = inject(PortalStore);
  private readonly fb = inject(FormBuilder);

  protected readonly buying = signal<CatalogItem | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    quantity: [1, [Validators.required, Validators.min(1)]],
    promisedFor: [''],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected open(item: CatalogItem): void {
    this.form.reset({ code: '', quantity: 1, promisedFor: '' });
    this.buying.set(item);
  }

  protected submit(): void {
    const item = this.buying();
    if (!item || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    // Vazio vira nulo: "a combinar" é estado legítimo.
    this.store.place(v.code, item.productId, v.quantity, v.promisedFor || null);
    this.buying.set(null);
  }

  protected reorder(order: PortalOrder): void {
    this.store.reorder(order, `${order.code}-R`);
  }
}
