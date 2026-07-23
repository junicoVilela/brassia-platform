import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RecipesStore } from '../../data-access/recipes.store';

@Component({
  selector: 'app-recipe-list-page',
  standalone: true,
  providers: [RecipesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './recipe-list-page.component.html',
})
export class RecipeListPageComponent implements OnInit {
  readonly store = inject(RecipesStore);
  ngOnInit(): void { this.store.load(); }
}
