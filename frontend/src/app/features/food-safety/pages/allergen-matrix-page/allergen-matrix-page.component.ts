import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { AllergensStore, EquipmentRow, ProcedureRow } from '../../data-access/allergens.store';
import { IngredientAllergenRow } from '../../domain/allergen.model';

/** O que está aberto para edição; só uma linha por vez, porque declarar é uma decisão. */
type EditingKind = 'ingredient' | 'equipment' | 'procedure';

@Component({
  selector: 'app-allergen-matrix-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [AllergensStore],
  templateUrl: './allergen-matrix-page.component.html',
})
/**
 * Matriz de alergênicos (FDS-001).
 *
 * <p>Três eixos numa tela só, porque é assim que a matriz é usada: ninguém confere um ingrediente
 * isolado, confere o cruzamento. A leitura que a tela precisa deixar impossível de errar é a
 * diferença entre <strong>declarado isento</strong> e <strong>não declarado</strong> — os dois
 * mostram zero alergênicos, e só o primeiro autoriza escrever no rótulo.
 */
export class AllergenMatrixPageComponent implements OnInit {
  protected readonly store = inject(AllergensStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canDeclare = this.auth.hasPermission('foodsafety.allergen.write');

  /** Chave da linha em edição (`tipo:id`), ou nulo. */
  protected readonly editing = signal<string | null>(null);
  /** Seleção corrente do formulário aberto. */
  protected readonly selection = signal<Set<string>>(new Set());

  protected readonly allergenForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected registerAllergen(): void {
    if (this.allergenForm.invalid) {
      return;
    }
    const { code, name } = this.allergenForm.getRawValue();
    this.store.registerAllergen(code, name);
    this.allergenForm.reset({ code: '', name: '' });
  }

  protected edit(kind: EditingKind, id: string, current: readonly string[]): void {
    this.editing.set(`${kind}:${id}`);
    this.selection.set(new Set(current));
  }

  protected cancel(): void {
    this.editing.set(null);
    this.selection.set(new Set());
  }

  protected isEditing(kind: EditingKind, id: string): boolean {
    return this.editing() === `${kind}:${id}`;
  }

  protected toggle(code: string): void {
    const next = new Set(this.selection());
    if (!next.delete(code)) {
      next.add(code);
    }
    this.selection.set(next);
  }

  protected isSelected(code: string): boolean {
    return this.selection().has(code);
  }

  protected saveIngredient(row: IngredientAllergenRow): void {
    this.store.declareIngredient(row.ingredientId, [...this.selection()]);
    this.cancel();
  }

  protected saveEquipment(row: EquipmentRow): void {
    this.store.dedicate(row.id, [...this.selection()]);
    this.cancel();
  }

  protected saveProcedure(row: ProcedureRow): void {
    this.store.declareProcedure(row.code, [...this.selection()]);
    this.cancel();
  }

  protected share(row: EquipmentRow): void {
    this.store.share(row.id);
    this.cancel();
  }

  protected busy(key: string): boolean {
    return this.store.saving() === key;
  }

  protected nameOf(code: string): string {
    return this.store.allergens().find(allergen => allergen.code === code)?.name ?? code;
  }
}
