import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { LibraryStore } from '../../data-access/library.store';
import {
  LibraryPublication,
  OwnedPublication,
  VISIBILITY_HELP,
  VISIBILITY_LABELS,
  Visibility,
} from '../../domain/library.model';

/**
 * A biblioteca de receitas (COM-001).
 *
 * <p>A responsabilidade da tela que não é listar: <strong>deixar o efeito da publicação visível antes de
 * ela acontecer</strong>. Este é o único lugar do sistema em que dado de receita sai da cervejaria, e o
 * erro possível aqui não é um número errado — é um vazamento que não se desfaz.
 *
 * <p>Por isso o formulário mostra, ao lado de cada nível de visibilidade, <strong>o que ele significa na
 * prática</strong>; e a confirmação de publicar diz o que sai e o que fica.
 */
@Component({
  selector: 'app-library-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [LibraryStore],
  templateUrl: './library-page.component.html',
})
export class LibraryPageComponent implements OnInit {
  protected readonly store = inject(LibraryStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly visibilityLabels = VISIBILITY_LABELS;
  protected readonly visibilityHelp = VISIBILITY_HELP;
  protected readonly visibilities: Visibility[] = [
    'PRIVATE',
    'BREWERY',
    'LINK',
    'UNLISTED',
    'PUBLIC',
  ];

  protected readonly showForm = signal(false);
  protected readonly canPublish = this.auth.hasPermission('community.recipe.publish');

  protected readonly form = this.fb.nonNullable.group({
    recipeId: ['', Validators.required],
    title: ['', [Validators.required, Validators.maxLength(160)]],
    summary: ['', Validators.maxLength(1000)],
    license: ['ALL_RIGHTS_RESERVED' as const, Validators.required],
    visibility: ['PRIVATE' as const, Validators.required],
  });

  ngOnInit(): void {
    this.store.load();
  }

  /** O texto de ajuda do nível escolhido agora — é ele que evita a publicação por engano. */
  protected get selectedHelp(): string {
    return VISIBILITY_HELP[this.form.getRawValue().visibility as Visibility];
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.store.publish(v.recipeId, v.title, v.summary || null, v.license, v.visibility);
    this.form.reset({
      recipeId: '',
      title: '',
      summary: '',
      license: 'ALL_RIGHTS_RESERVED',
      visibility: 'PRIVATE',
    });
    this.showForm.set(false);
  }

  protected open(publication: LibraryPublication): void {
    this.store.open(publication);
  }

  protected retire(publication: OwnedPublication): void {
    this.store.unpublish(publication);
  }

  protected changeVisibility(publication: OwnedPublication, value: string): void {
    this.store.changeVisibility(publication, value as Visibility);
  }
}
