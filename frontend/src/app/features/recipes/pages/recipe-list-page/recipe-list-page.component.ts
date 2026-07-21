import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RecipesStore } from '../../data-access/recipes.store';

@Component({
  selector: 'app-recipe-list-page',
  standalone: true,
  providers: [RecipesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main>
      <header><h1>Receitas</h1><button type="button">Criar receita</button></header>
      @if (store.loading()) { <p aria-live="polite">Carregando…</p> }
      @else if (store.error(); as error) { <p role="alert">{{ error }}</p> }
      @else if (store.empty()) { <p>Nenhuma receita cadastrada.</p> }
      @else {
        <ul>
          @for (recipe of store.items(); track recipe.id) {
            <li>{{ recipe.name }} — versão {{ recipe.version }}</li>
          }
        </ul>
      }
    </main>
  `,
})
export class RecipeListPageComponent implements OnInit {
  readonly store = inject(RecipesStore);
  ngOnInit(): void { this.store.load(); }
}
