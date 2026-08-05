import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { QuarantinesStore } from '../../data-access/quarantines.store';
import { NODE_ICONS, NODE_LABELS } from '../../domain/genealogy.model';
import { Quarantine } from '../../domain/quarantine.model';

/**
 * Quarentenas (FDS-002).
 *
 * <p>A tela existe para responder duas perguntas: o que está contido e <strong>o que mais está
 * parado por causa disso</strong>. A segunda é a que justifica o alcance vir aberto no detalhe —
 * quem investiga precisa ver os descendentes bloqueados, inclusive os que estão parados por
 * suspeita e não por fato.
 */
@Component({
  selector: 'app-quarantines-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [QuarantinesStore],
  templateUrl: './quarantines-page.component.html',
})
export class QuarantinesPageComponent implements OnInit {
  protected readonly store = inject(QuarantinesStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canRelease = this.auth.hasPermission('traceability.quarantine.release');
  protected readonly nodeLabels = NODE_LABELS;
  protected readonly nodeIcons = NODE_ICONS;

  /** Quarentena cuja liberação está sendo escrita; só uma por vez. */
  protected readonly releasing = signal<string | null>(null);

  protected readonly releaseForm = this.fb.nonNullable.group({
    justification: ['', [Validators.required, Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.store.load(true);
  }

  protected toggleScope(onlyOpen: boolean): void {
    if (this.store.onlyOpen() !== onlyOpen) {
      this.store.load(onlyOpen);
    }
  }

  protected select(quarantine: Quarantine): void {
    this.store.select(quarantine.id);
  }

  protected startRelease(quarantine: Quarantine): void {
    this.releasing.set(quarantine.id);
    this.releaseForm.reset({ justification: '' });
  }

  protected cancelRelease(): void {
    this.releasing.set(null);
  }

  protected confirmRelease(quarantine: Quarantine): void {
    if (this.releaseForm.invalid) {
      this.releaseForm.markAllAsTouched();
      return;
    }
    this.store.release(quarantine.id, this.releaseForm.getRawValue().justification);
    this.releasing.set(null);
  }

  protected isOpenDetail(quarantine: Quarantine): boolean {
    return this.store.detail()?.quarantine.id === quarantine.id;
  }

  protected busy(key: string): boolean {
    return this.store.saving() === key;
  }
}
