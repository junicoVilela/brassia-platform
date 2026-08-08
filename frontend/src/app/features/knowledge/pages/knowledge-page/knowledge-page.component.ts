import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { KnowledgeStore } from '../../data-access/knowledge.store';
import { DocumentType, TYPE_ICONS, TYPE_LABELS } from '../../domain/knowledge.model';

/**
 * A base de conhecimento vista por quem opera (RAG-001).
 *
 * <p>Duas coisas que a tela precisa deixar claras, e que não são detalhe de layout:
 *
 * <p><strong>Trecho recuperado é fala de terceiro.</strong> Ele aparece como citação, com a fonte, a
 * versão e a vigência ao lado — nunca como texto do sistema. O documento foi escrito por fabricante,
 * laboratório ou fornecedor, e apresentá-lo como se fosse resposta da plataforma seria dar a ele uma
 * autoridade que ele não tem.
 *
 * <p><strong>"Não achei" não é "deu erro".</strong> Busca sem resultado é resposta legítima e aparece
 * como tal: significa que não há fonte na base para aquilo, o que é informação útil — e é diferente de
 * a busca ter falhado.
 */
@Component({
  selector: 'app-knowledge-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [KnowledgeStore],
  templateUrl: './knowledge-page.component.html',
})
export class KnowledgePageComponent implements OnInit {
  protected readonly store = inject(KnowledgeStore);
  protected readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly typeLabels = TYPE_LABELS;
  protected readonly typeIcons = TYPE_ICONS;
  protected readonly types = Object.keys(TYPE_LABELS) as DocumentType[];

  protected readonly searchForm = this.fb.nonNullable.group({
    question: ['', [Validators.required, Validators.minLength(3)]],
    onDate: [''],
  });

  protected readonly indexForm = this.fb.nonNullable.group({
    type: ['EQUIPMENT_MANUAL' as DocumentType, [Validators.required]],
    code: ['', [Validators.required, Validators.maxLength(60)]],
    title: ['', [Validators.required, Validators.maxLength(200)]],
    effectiveFrom: ['', [Validators.required]],
    requiredPermission: ['knowledge.document.read', [Validators.required]],
    sourceUri: [''],
    text: ['', [Validators.required]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected search(): void {
    if (this.searchForm.invalid) {
      return;
    }
    const { question, onDate } = this.searchForm.getRawValue();
    this.store.search(question, onDate || null, null);
  }

  protected index(): void {
    if (this.indexForm.invalid) {
      return;
    }
    const value = this.indexForm.getRawValue();
    this.store.index({
      type: value.type,
      code: value.code,
      title: value.title,
      effectiveFrom: value.effectiveFrom,
      requiredPermission: value.requiredPermission,
      equipmentId: null,
      sourceUri: value.sourceUri || null,
      text: value.text,
    });
    // O texto sai do formulário depois de indexado: um manual inteiro pendurado num campo convida a
    // reindexar o mesmo conteúdo por engano. O resto fica, porque versão nova reusa código e permissão.
    this.indexForm.patchValue({ text: '' });
  }
}
