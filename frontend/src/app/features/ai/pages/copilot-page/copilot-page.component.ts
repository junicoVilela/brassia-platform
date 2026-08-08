import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { CopilotStore } from '../../data-access/copilot.store';

/**
 * O copiloto respondendo com fonte (RAG-002).
 *
 * <p>A tela tem uma responsabilidade que não é decorativa: <strong>não deixar a inferência parecer
 * citação</strong>. O que o documento diz aparece entre aspas, com o código, a versão e a vigência ao lado;
 * o que o modelo concluiu aparece em bloco separado e rotulado; o que ele não sabe aparece como limitação.
 * Juntar os três num parágrafo bonito apagaria exatamente a informação que decide se alguém pode agir sobre
 * a resposta.
 *
 * <p>Três desfechos, três telas diferentes: respondeu com fonte, não havia fonte (indexe um documento), e
 * não conseguiu sustentar o que ia dizer (a resposta foi descartada). Os dois últimos são resposta
 * legítima, não falha — e o segundo e o terceiro pedem providências opostas.
 */
@Component({
  selector: 'app-copilot-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, PageHeaderComponent, LoadingIndicatorComponent],
  providers: [CopilotStore],
  templateUrl: './copilot-page.component.html',
})
export class CopilotPageComponent {
  protected readonly store = inject(CopilotStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    question: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(1000)]],
    onDate: [''],
  });

  protected ask(): void {
    if (this.form.invalid) {
      return;
    }
    const { question, onDate } = this.form.getRawValue();
    this.store.ask(question, onDate || null);
  }
}
