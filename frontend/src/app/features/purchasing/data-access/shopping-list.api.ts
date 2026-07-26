import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ShoppingListGroup } from '../domain/shopping-list.model';

@Injectable({ providedIn: 'root' })
export class ShoppingListApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/purchasing/shopping-list';

  list() {
    return this.http.get<ShoppingListGroup[]>(this.baseUrl);
  }
}
