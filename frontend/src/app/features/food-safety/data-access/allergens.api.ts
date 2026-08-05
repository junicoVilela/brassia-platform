import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Allergen, AllergenMatrix } from '../domain/allergen.model';

@Injectable({ providedIn: 'root' })
export class AllergensApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/food-safety';

  matrix(): Observable<AllergenMatrix> {
    return this.http.get<AllergenMatrix>(`${this.baseUrl}/matrix`);
  }

  register(code: string, name: string): Observable<Allergen> {
    return this.http.post<Allergen>(`${this.baseUrl}/allergens`, { code, name });
  }

  declareIngredient(ingredientId: string, allergens: string[]): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/ingredients/${ingredientId}/allergens`, {
      allergens,
    });
  }

  dedicate(equipmentId: string, allergens: string[]): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/equipment/${equipmentId}/dedication`, { allergens });
  }

  /** Remover a dedicação devolve o equipamento ao compartilhado, onde a troca volta a ser checada. */
  share(equipmentId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/equipment/${equipmentId}/dedication`);
  }

  declareProcedure(procedureCode: string, allergens: string[]): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/procedures/${procedureCode}/allergens`, {
      allergens,
    });
  }
}
