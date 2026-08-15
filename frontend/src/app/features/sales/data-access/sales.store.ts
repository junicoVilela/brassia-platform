import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { PriceEntry, Product, SalesChannel } from '../domain/product.model';
import { SalesApi } from './sales.api';

interface ApiError {
  status?: number;
  error?: { code?: string; detail?: string; from?: string };
}

/**
 * Estado do catálogo e dos preços (SAL-001).
 *
 * <p>A linha do tempo é sempre relida do servidor depois de um preço novo, e não remendada em memória:
 * cadastrar um preço **fecha o anterior na véspera**, e refazer essa regra no cliente seria manter duas
 * implementações da mesma coisa — que divergem na primeira mudança.
 */
@Injectable()
export class SalesStore {
  private readonly api = inject(SalesApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly products = signal<Product[]>([]);
  readonly channels = signal<SalesChannel[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly onlyActive = signal(true);
  readonly saving = signal(false);

  readonly selectedProduct = signal<Product | null>(null);
  readonly selectedChannel = signal<string | null>(null);
  readonly priceEntries = signal<PriceEntry[]>([]);
  readonly pricesLoading = signal(false);

  /** O preço vigente é o único sem fim — a linha do tempo garante que só existe um. */
  readonly currentPrice = computed(() => this.priceEntries().find(e => e.validTo === null) ?? null);

  readonly hasChannels = computed(() => this.channels().length > 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      products: this.api.products(this.onlyActive()),
      channels: this.api.channels(true),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: ({ products, channels }) => {
          this.products.set(products);
          this.channels.set(channels);
          if (!this.selectedChannel() && channels.length > 0) {
            this.selectedChannel.set(channels[0].id);
          }
        },
        error: (e: ApiError) => this.error.set(this.message(e, 'Não foi possível carregar o catálogo.')),
      });
  }

  toggleOnlyActive(value: boolean): void {
    this.onlyActive.set(value);
    this.load();
  }

  selectProduct(product: Product): void {
    this.selectedProduct.set(product);
    this.loadPrices();
  }

  selectChannel(channelId: string): void {
    this.selectedChannel.set(channelId);
    this.loadPrices();
  }

  loadPrices(): void {
    const product = this.selectedProduct();
    const channel = this.selectedChannel();
    if (!product || !channel) {
      this.priceEntries.set([]);
      return;
    }
    this.pricesLoading.set(true);
    this.api
      .priceSchedule(product.id, channel)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.pricesLoading.set(false)),
      )
      .subscribe({
        next: schedule => this.priceEntries.set(schedule.entries),
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível carregar os preços.')),
      });
  }

  createProduct(sku: string, name: string, recipeId: string, containerId: string): void {
    this.saving.set(true);
    this.api
      .createProduct({ sku, name, recipeId, containerId })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Produto cadastrado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível cadastrar o produto.')),
      });
  }

  setProductActive(product: Product, active: boolean): void {
    this.api
      .setProductActive(product.id, active)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(active ? 'Produto restaurado.' : 'Produto descontinuado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível alterar a situação.')),
      });
  }

  createChannel(code: string, name: string): void {
    this.saving.set(true);
    this.api
      .createChannel({ code, name })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Canal cadastrado.');
          this.load();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível cadastrar o canal.')),
      });
  }

  priceFrom(amount: number, currency: string, taxIncluded: boolean, validFrom: string): void {
    const product = this.selectedProduct();
    const channel = this.selectedChannel();
    if (!product || !channel) {
      return;
    }
    this.saving.set(true);
    this.api
      .priceFrom(product.id, { channelId: channel, amount, currency, taxIncluded, validFrom })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Preço vigente a partir da data.');
          this.loadPrices();
        },
        error: (e: ApiError) => this.toast.error(this.message(e, 'Não foi possível definir o preço.')),
      });
  }

  /**
   * A mensagem do servidor vence a genérica.
   *
   * <p>Na sobreposição de preço ela vem com a data em `from`, que é a informação que resolve o problema:
   * sem ela, o operador fica tentando datas até uma passar.
   */
  private message(e: ApiError, fallback: string): string {
    return e?.error?.detail ?? fallback;
  }
}
