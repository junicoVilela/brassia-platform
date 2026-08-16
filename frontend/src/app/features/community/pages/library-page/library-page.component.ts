import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { LibraryStore } from '../../data-access/library.store';
import {
  Contribution,
  ContributionKind,
  LibraryPublication,
  OwnedPublication,
  REPORT_REASON_LABELS,
  ReportReason,
  SharePermission,
  ShareLink,
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
  protected readonly canRate = this.auth.hasPermission('community.rating.write');

  protected readonly reportLabels = REPORT_REASON_LABELS;
  protected readonly reportReasons: ReportReason[] = ['ABUSE', 'PLAGIARISM', 'SPAM', 'OTHER'];

  /** As notas possíveis. Zero não está aqui: ele seria "não avaliou", que é a ausência de nota. */
  protected readonly scale = [1, 2, 3, 4, 5];

  /** O formulário de denúncia só aparece quando alguém decide denunciar — não é botão de primeira. */
  protected readonly showReport = signal(false);

  protected readonly reportForm = this.fb.nonNullable.group({
    reason: ['ABUSE' as ReportReason, Validators.required],
    note: ['', Validators.maxLength(1000)],
  });

  protected readonly contributionForm = this.fb.nonNullable.group({
    kind: ['COMMENT' as ContributionKind, Validators.required],
    body: ['', [Validators.required, Validators.maxLength(2000)]],
    context: ['', Validators.maxLength(120)],
  });

  protected readonly forkForm = this.fb.nonNullable.group({
    equipmentId: ['', Validators.required],
    name: ['', Validators.maxLength(160)],
  });

  protected readonly linkForm = this.fb.nonNullable.group({
    permission: ['READ' as SharePermission, Validators.required],
    label: ['', Validators.maxLength(120)],
    expiresAt: [''],
  });

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
    this.showReport.set(false);
    this.reportForm.reset({ reason: 'ABUSE', note: '' });
    this.forkForm.reset({ equipmentId: '', name: '' });
    this.contributionForm.reset({ kind: 'COMMENT', body: '', context: '' });
    this.store.open(publication);
  }

  protected submitContribution(publication: LibraryPublication): void {
    if (this.contributionForm.invalid) {
      this.contributionForm.markAllAsTouched();
      return;
    }
    const v = this.contributionForm.getRawValue();
    this.store.write(publication, v.kind, v.body, v.context || null);
    this.contributionForm.reset({ kind: 'COMMENT', body: '', context: '' });
  }

  protected decide(publication: LibraryPublication, c: Contribution, accept: boolean): void {
    this.store.decide(publication, c, accept, null);
  }

  protected rate(publication: LibraryPublication, value: number): void {
    this.store.rate(publication, value);
  }

  protected submitReport(publication: LibraryPublication): void {
    const v = this.reportForm.getRawValue();
    // "Outro" sem texto não é denúncia, é ruído: ninguém revisa o que não foi dito. A validação vive
    // aqui porque ela depende do motivo escolhido, e não do campo isolado.
    if (v.reason === 'OTHER' && !v.note.trim()) {
      this.reportForm.controls.note.setErrors({ required: true });
      this.reportForm.markAllAsTouched();
      return;
    }
    if (this.reportForm.invalid) {
      this.reportForm.markAllAsTouched();
      return;
    }
    this.store.report(publication, v.reason, v.note || null);
    this.reportForm.reset({ reason: 'ABUSE', note: '' });
    this.showReport.set(false);
  }

  protected openReports(publication: OwnedPublication): void {
    this.store.openReports(publication);
  }

  protected submitFork(publication: LibraryPublication): void {
    if (this.forkForm.invalid) {
      this.forkForm.markAllAsTouched();
      return;
    }
    const v = this.forkForm.getRawValue();
    // Nome vazio vira nulo: o servidor acrescenta "(cópia)", que é melhor que reusar o nome do
    // original — duas receitas iguais no catálogo fazem o cervejeiro pegar a errada.
    this.store.fork(publication, v.equipmentId, v.name || null);
  }

  protected retire(publication: OwnedPublication): void {
    this.store.unpublish(publication);
  }

  protected changeVisibility(publication: OwnedPublication, value: string): void {
    this.store.changeVisibility(publication, value as Visibility);
  }

  protected openLinks(publication: OwnedPublication): void {
    this.linkForm.reset({ permission: 'READ', label: '', expiresAt: '' });
    this.store.openLinks(publication);
  }

  protected submitLink(): void {
    if (this.linkForm.invalid) {
      this.linkForm.markAllAsTouched();
      return;
    }
    const v = this.linkForm.getRawValue();
    // Data vazia vira nulo: "sem prazo" é estado legítimo. O campo é `date`, e o servidor espera um
    // instante — meia-noite local convertida para UTC, e não a string crua.
    const expira = v.expiresAt ? new Date(`${v.expiresAt}T23:59:59`).toISOString() : null;
    this.store.createLink(v.permission, v.label || null, expira);
  }

  protected revoke(link: ShareLink): void {
    this.store.revokeLink(link);
  }
}
