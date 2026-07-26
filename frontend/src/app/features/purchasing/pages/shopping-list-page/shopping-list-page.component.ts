import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ShoppingListStore } from '../../data-access/shopping-list.store';
import { ShoppingListGroup } from '../../domain/shopping-list.model';

@Component({
  selector: 'app-shopping-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [ShoppingListStore],
  templateUrl: './shopping-list-page.component.html',
})
export class ShoppingListPageComponent implements OnInit {
  protected readonly store = inject(ShoppingListStore);
  private readonly auth = inject(AuthService);

  protected readonly canSeeCosts = this.auth.hasPermission('purchasing.cost.read');

  ngOnInit(): void {
    this.store.load();
  }

  /** Exporta a lista em CSV; custos só entram quando o usuário tem permissão. */
  protected exportCsv(): void {
    const header = ['Fornecedor', 'Ingrediente', 'Codigo', 'A comprar', 'Unidade compra',
      'Necessidade', 'Em estoque', 'Reservado', 'Unidade tecnica'];
    if (this.canSeeCosts) {
      header.push('Custo unitario', 'Custo estimado');
    }
    const rows: string[][] = [header];
    for (const group of this.store.groups()) {
      for (const item of group.items) {
        const row = [
          group.supplierName,
          item.ingredientName ?? item.ingredientId,
          item.ingredientCode ?? '',
          this.num(item.purchaseQuantity),
          item.purchaseUnit,
          this.num(item.suggested),
          this.num(item.onHand),
          this.num(item.reserved),
          item.unit,
        ];
        if (this.canSeeCosts) {
          row.push(this.num(item.unitCost), this.num(item.estimatedCost));
        }
        rows.push(row);
      }
    }
    this.download(rows);
  }

  protected total(group: ShoppingListGroup): number | null {
    return group.estimatedTotal;
  }

  private num(value: number | null): string {
    return value === null || value === undefined ? '' : String(value);
  }

  private download(rows: string[][]): void {
    const csv = rows.map(row => row.map(cell => `"${(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'lista-de-compras.csv';
    link.click();
    URL.revokeObjectURL(url);
  }
}
