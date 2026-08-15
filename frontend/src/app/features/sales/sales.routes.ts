import { Routes } from '@angular/router';
import { CatalogPageComponent } from './pages/catalog-page/catalog-page.component';
import { OrdersPageComponent } from './pages/orders-page/orders-page.component';

export const SALES_ROUTES: Routes = [{ path: '', component: CatalogPageComponent }];

export const ORDER_ROUTES: Routes = [{ path: '', component: OrdersPageComponent }];
