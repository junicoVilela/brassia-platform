import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { SalesStore } from '../../data-access/sales.store';
import { Product } from '../../domain/product.model';

/**
 * Catálogo, canais e preços (SAL-001).
 *
 * <p>A responsabilidade da tela que não é cadastrar: <strong>mostrar que preço tem história</strong>. O
 * vigente aparece em destaque, e as vigências anteriores logo abaixo — porque quando alguém pergunta
 * "por que este pedido de março saiu a 12?", a resposta precisa estar visível, e não numa consulta que
 * ninguém sabe fazer.
 *
 * <p><strong>O formulário de preço fala "a partir de", e não "editar preço".</strong> Preço não se
 * sobrescreve: o novo fecha o anterior na véspera. Um campo de edição prometeria algo que o domínio
 * recusa, e o operador descobriria isso no erro.
 */
@Component({
  selector: 'app-catalog-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [SalesStore],
  templateUrl: './catalog-page.component.html',
})
export class CatalogPageComponent implements OnInit {
  protected readonly store = inject(SalesStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly showProductForm = signal(false);
  protected readonly showChannelForm = signal(false);
  protected readonly showPriceForm = signal(false);

  protected readonly canManage = this.auth.hasPermission('sales.catalog.manage');
  protected readonly canPrice = this.auth.hasPermission('sales.price.manage');

  protected readonly productForm = this.fb.nonNullable.group({
    sku: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    recipeId: ['', Validators.required],
    containerId: ['', Validators.required],
  });

  protected readonly channelForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(30)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
  });

  protected readonly priceForm = this.fb.nonNullable.group({
    amount: [0, [Validators.required, Validators.min(0.0001)]],
    currency: ['BRL', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    taxIncluded: [false],
    validFrom: [this.today(), Validators.required],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected select(product: Product): void {
    this.store.selectProduct(product);
    this.showPriceForm.set(false);
  }

  protected submitProduct(): void {
    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }
    const v = this.productForm.getRawValue();
    this.store.createProduct(v.sku, v.name, v.recipeId, v.containerId);
    this.productForm.reset({ sku: '', name: '', recipeId: '', containerId: '' });
    this.showProductForm.set(false);
  }

  protected submitChannel(): void {
    if (this.channelForm.invalid) {
      this.channelForm.markAllAsTouched();
      return;
    }
    const v = this.channelForm.getRawValue();
    this.store.createChannel(v.code, v.name);
    this.channelForm.reset({ code: '', name: '' });
    this.showChannelForm.set(false);
  }

  protected submitPrice(): void {
    if (this.priceForm.invalid) {
      this.priceForm.markAllAsTouched();
      return;
    }
    const v = this.priceForm.getRawValue();
    // Data pura, sem hora: não há a armadilha de fuso que o datetime-local traz.
    this.store.priceFrom(v.amount, v.currency, v.taxIncluded, v.validFrom);
    this.showPriceForm.set(false);
  }

  private today(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }
}
