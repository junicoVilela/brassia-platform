import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { UtilitiesStore, lastDays } from '../../data-access/utilities.store';
import {
  UTILITY_ICONS,
  UTILITY_LABELS,
  UtilityIndicator,
  UtilityType,
} from '../../domain/utility-indicator.model';

/**
 * Água, energia e CO₂ por litro envasado (UTL-001).
 *
 * <p>A tela tem uma responsabilidade que não é dividir: é deixar claro o quanto do número é
 * medição. Um consumo por litro calculado sobre um terço dos ciclos parece um indicador da fábrica
 * e é um indicador de um terço dela — por isso a cobertura fica ao lado do número, e não num
 * rodapé que ninguém lê.
 *
 * <p>Período sem envase mostra o consumo e não mostra o por litro. Escrever "0 L/L" ali seria
 * elogiar a fábrica que limpou tanque sem produzir cerveja.
 */
@Component({
  selector: 'app-utility-indicators-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [UtilitiesStore],
  templateUrl: './utility-indicators-page.component.html',
})
export class UtilityIndicatorsPageComponent implements OnInit {
  protected readonly store = inject(UtilitiesStore);
  private readonly fb = inject(FormBuilder);

  protected readonly labels = UTILITY_LABELS;
  protected readonly icons = UTILITY_ICONS;

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

  protected label(type: UtilityType): string {
    return this.labels[type];
  }

  protected icon(type: UtilityType): string {
    return this.icons[type];
  }

  /** Verdadeiro quando parte do total foi estimada — o que muda como o número deve ser lido. */
  protected hasEstimate(indicator: UtilityIndicator): boolean {
    return indicator.estimated > 0;
  }
}
