import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { QualityApi } from '../../../quality/data-access/quality.api';
import { NonConformity } from '../../../quality/domain/quality.model';
import { DrillCapaAction } from '../../domain/drill.model';
import { DrillsStore } from '../../data-access/drills.store';
import { RecallDrill } from '../../domain/drill.model';
import { NODE_ICONS, NODE_LABELS } from '../../domain/genealogy.model';

/**
 * Simulados de recall (FDS-004).
 *
 * <p>A tela mostra o cronômetro correndo, e é de propósito: o que o exercício mede é o tempo da
 * <em>cervejaria</em> para localizar o produto, não o do servidor para percorrer o grafo. Enquanto
 * corre, o relatório mostra o alvo — o que saiu e para onde; no encerramento, a equipe diz quantas
 * unidades encontrou de fato, porque contar sozinho daria 100% sempre e não mediria nada.
 */
@Component({
  selector: 'app-drills-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [DrillsStore],
  templateUrl: './drills-page.component.html',
})
export class DrillsPageComponent implements OnInit {
  protected readonly store = inject(DrillsStore);
  private readonly quality = inject(QualityApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canManage = this.auth.hasPermission('traceability.drill.manage');
  protected readonly nodeLabels = NODE_LABELS;
  protected readonly nodeIcons = NODE_ICONS;

  protected readonly finishing = signal<string | null>(null);

  protected readonly finishForm = this.fb.nonNullable.group({
    unitsLocated: [0, [Validators.required, Validators.min(0)]],
    summary: ['', [Validators.required, Validators.maxLength(1000)]],
    correctiveActions: ['', [Validators.maxLength(2000)]],
    // Escolhida entre as NCs prontas para receber ação. O simulado não abre NC sozinho: isso exigiria
    // decidir a severidade, e o quanto uma cobertura de 75% é grave depende do produto e de quem audita.
    nonConformityId: [''],
  });

  ngOnInit(): void {
    this.store.load();
    // As NCs vêm da qualidade: é lá que a ação vai ser acompanhada, e duplicar a lista aqui criaria um
    // segundo lugar para manter igual.
    this.quality
      .nonConformities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.nonConformities.set(list), error: () => undefined });
  }

  protected select(drill: RecallDrill): void {
    this.store.select(drill.id);
  }

  protected isOpenReport(drill: RecallDrill): boolean {
    return this.store.report()?.drill.id === drill.id;
  }

  protected startFinish(drill: RecallDrill): void {
    this.finishing.set(drill.id);
    this.finishForm.reset({
      // Sugere o escopo inteiro localizado, que é o resultado que se espera — e que o exercício
      // existe para confirmar ou desmentir.
      unitsLocated: this.store.report()?.unitsInScope ?? 0,
      summary: '',
      correctiveActions: '',
      nonConformityId: '',
    });
  }

  protected cancelFinish(): void {
    this.finishing.set(null);
  }

  protected confirmFinish(drill: RecallDrill): void {
    if (this.finishForm.invalid) {
      this.finishForm.markAllAsTouched();
      return;
    }
    const value = this.finishForm.getRawValue();
    const nc = value.nonConformityId || null;
    this.store.finish(
      drill.id,
      value.unitsLocated,
      value.summary,
      nc ? null : value.correctiveActions || null,
      nc,
      nc ? this.capaActions() : [],
    );
    this.capaActions.set([]);
    this.finishing.set(null);
  }

  /** Ações a abrir no CAPA quando uma NC é escolhida. Com dono e prazo — sem eles é intenção. */
  protected readonly capaActions = signal<DrillCapaAction[]>([]);
  protected readonly nonConformities = signal<NonConformity[]>([]);

  protected addCapaAction(): void {
    this.capaActions.update(rows => [
      ...rows,
      { kind: 'CORRECTIVE', description: '', owner: '', dueOn: '' },
    ]);
  }

  protected updateCapaAction(index: number, field: keyof DrillCapaAction, value: string): void {
    this.capaActions.update(rows =>
      rows.map((row, i) => (i === index ? { ...row, [field]: value } : row)),
    );
  }

  protected removeCapaAction(index: number): void {
    this.capaActions.update(rows => rows.filter((_, i) => i !== index));
  }

  /** Só NCs investigadas recebem ação: o CAPA recusa planejar solução antes de conhecer a causa. */
  protected readonly nonConformitiesReady = computed(() =>
    this.nonConformities().filter(nc => nc.status === 'INVESTIGATED' || nc.status === 'ACTION_PLANNED'),
  );

  /** Tempo em horas e minutos: um simulado se mede em horas, não em segundos. */
  protected elapsed(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return hours > 0 ? `${hours}h${String(minutes).padStart(2, '0')}` : `${minutes} min`;
  }

  protected busy(key: string): boolean {
    return this.store.saving() === key;
  }
}
