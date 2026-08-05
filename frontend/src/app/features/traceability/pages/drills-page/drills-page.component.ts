import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
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
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [DrillsStore],
  templateUrl: './drills-page.component.html',
})
export class DrillsPageComponent implements OnInit {
  protected readonly store = inject(DrillsStore);
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
  });

  ngOnInit(): void {
    this.store.load();
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
    this.store.finish(drill.id, value.unitsLocated, value.summary, value.correctiveActions || null);
    this.finishing.set(null);
  }

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
