import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { DashboardStore, lastDays } from '../../data-access/dashboard.store';
import {
  DRILL_DOWN_ROUTES,
  GROUP_ICONS,
  GROUP_LABELS,
  IndicatorGroup,
  OperationalIndicator,
} from '../../domain/dashboard.model';

/**
 * Painel operacional (RPT-002).
 *
 * <p><strong>Cartões, não gráficos.</strong> Cada número aqui é um valor atual, e um valor atual é
 * um cartão — uma barra sozinha não diz mais do que o número que ela representa. Não há série
 * temporal a comparar nesta história, então não há eixo a desenhar.
 *
 * <p>A definição fica <em>no cartão</em>, não num tooltip que só quem procura encontra. É o que
 * separa um painel de uma parede de números: em três meses, "lotes iniciados" sem definição vira um
 * número que cada pessoa da fábrica interpreta de um jeito.
 *
 * <p>A ressalva usa cor <strong>e</strong> ícone <strong>e</strong> texto. Cor sozinha não é
 * informação para quem não a distingue.
 */
@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [DashboardStore],
  templateUrl: './dashboard-page.component.html',
})
export class DashboardPageComponent implements OnInit {
  protected readonly store = inject(DashboardStore);
  private readonly fb = inject(FormBuilder);

  protected readonly groupLabels = GROUP_LABELS;
  protected readonly groupIcons = GROUP_ICONS;

  protected readonly periodForm = this.fb.nonNullable.group({
    from: [this.store.period().from, [Validators.required]],
    to: [this.store.period().to, [Validators.required]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected apply(): void {
    if (this.periodForm.invalid) {
      return;
    }
    this.store.load(this.periodForm.getRawValue());
  }

  protected preset(days: number): void {
    const period = lastDays(days);
    this.periodForm.setValue(period);
    this.store.load(period);
  }

  protected groupLabel(group: IndicatorGroup): string {
    return this.groupLabels[group];
  }

  protected groupIcon(group: IndicatorGroup): string {
    return this.groupIcons[group];
  }

  /** A rota onde o número se abre, ou vazio quando ainda não há tela para aquele recurso. */
  protected routeOf(indicator: OperationalIndicator): string | null {
    return DRILL_DOWN_ROUTES[indicator.drillDown.resource] ?? null;
  }

  protected queryOf(indicator: OperationalIndicator): Record<string, string> {
    return indicator.drillDown.filter;
  }
}
