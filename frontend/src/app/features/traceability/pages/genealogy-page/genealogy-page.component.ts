import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { GenealogyStore } from '../../data-access/genealogy.store';
import { QuarantinesApi } from '../../data-access/quarantines.api';
import { DrillsApi } from '../../data-access/drills.api';
import { RecallsApi } from '../../data-access/recalls.api';
import {
  Direction,
  LineageEdge,
  NODE_ICONS,
  NODE_LABELS,
  NodeType,
} from '../../domain/genealogy.model';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../../../core/auth/auth.service';
import { ToastService } from '../../../../core/notifications/toast.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';

/**
 * Genealogia de um nó (TRC-001).
 *
 * <p>A tela não desenha um diagrama livre: a cadeia produtiva é quase linear — insumo, ordem, lote,
 * levedura, envase — e uma biblioteca de grafo aqui seria peso sem retorno. Colunas na ordem em que
 * a produção acontece dizem a mesma coisa e se leem melhor no celular.
 *
 * <p>O nó de partida vem pela URL, porque é assim que se chega: pelo botão "genealogia" do lote.
 */
@Component({
  selector: 'app-genealogy-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [GenealogyStore],
  templateUrl: './genealogy-page.component.html',
})
export class GenealogyPageComponent implements OnInit {
  protected readonly store = inject(GenealogyStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly quarantines = inject(QuarantinesApi);
  private readonly recalls = inject(RecallsApi);
  private readonly drills = inject(DrillsApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  /**
   * Quarentenar (FDS-002) mora aqui porque é aqui que se decide: a investigação começa olhando a
   * cadeia do lote suspeito, não numa tela de cadastro à parte.
   */
  protected readonly canQuarantine = this.auth.hasPermission('traceability.quarantine.open');
  protected readonly quarantining = signal(false);
  protected readonly quarantineError = signal<string | null>(null);
  protected readonly quarantineForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  /**
   * Abrir recall (FDS-003) fica ao lado de quarentenar, e não substitui: são decisões diferentes.
   * Conter é parar o que está aqui dentro; recall é ir atrás do que já saiu.
   */
  protected readonly canRecall = this.auth.hasPermission('traceability.recall.manage');

  /** Simular (FDS-004) é o mesmo gesto sem consequência: treina a localização e não recolhe nada. */
  protected readonly canDrill = this.auth.hasPermission('traceability.drill.manage');
  protected readonly recalling = signal(false);
  protected readonly recallError = signal<string | null>(null);
  protected readonly recallForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  protected readonly nodeLabels = NODE_LABELS;
  protected readonly nodeIcons = NODE_ICONS;
  protected readonly directions: readonly { value: Direction; label: string; hint: string }[] = [
    { value: 'BACKWARD', label: 'De onde veio', hint: 'ancestrais — a pergunta da investigação' },
    { value: 'FORWARD', label: 'Para onde foi', hint: 'descendentes — a pergunta do recall' },
    { value: 'BOTH', label: 'Os dois lados', hint: 'a cadeia inteira em volta do nó' },
  ];

  /** Sem nó na URL não há o que consultar: a tela é sempre sobre alguma coisa. */
  protected readonly hasNode = () => this.store.query() !== null;

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const nodeId = params.get('nodeId');
    const nodeType = params.get('nodeType') as NodeType | null;
    if (nodeId && nodeType) {
      this.store.load({
        nodeType,
        nodeId,
        direction: (params.get('direction') as Direction) ?? 'BOTH',
        depth: Number(params.get('depth') ?? 6),
      });
    }
  }

  protected select(direction: Direction): void {
    this.store.changeDirection(direction);
    // A URL acompanha o estado: um grafo consultado é algo que se manda para outra pessoa.
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { direction },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected expand(): void {
    const current = this.store.query();
    if (current) {
      this.store.changeDepth(Math.min(current.depth + 3, 10));
    }
  }

  protected edgeClass(edge: LineageEdge): string {
    return edge.strength === 'INTENDED' ? 'text-bg-warning' : 'text-bg-light';
  }

  protected strengthLabel(edge: LineageEdge): string {
    return edge.strength === 'INTENDED' ? 'intenção' : 'registrado';
  }

  protected goToBatches(): void {
    this.router.navigate(['/production/batches']);
  }

  /** Inicia o simulado do nó em tela e leva para o cronômetro, que é onde o exercício acontece. */
  protected startDrill(): void {
    const query = this.store.query();
    if (!query) {
      return;
    }
    this.drills.start(query.nodeType, query.nodeId, null).subscribe({
      next: () => {
        this.toast.success('Simulado iniciado.');
        this.router.navigate(['/traceability/recall-drills']);
      },
      error: (e: { detail?: string }) =>
        this.recallError.set(e.detail ?? 'Não foi possível iniciar o simulado.'),
    });
  }

  protected startRecall(): void {
    this.recalling.set(true);
    this.recallError.set(null);
    this.recallForm.reset({ reason: '' });
  }

  protected cancelRecall(): void {
    this.recalling.set(false);
  }

  /** Abre o recall do nó raiz e leva para o dossiê, que é onde o trabalho continua. */
  protected confirmRecall(): void {
    const query = this.store.query();
    if (!query || this.recallForm.invalid) {
      this.recallForm.markAllAsTouched();
      return;
    }
    this.recalls.open(query.nodeType, query.nodeId, this.recallForm.getRawValue().reason).subscribe({
      next: () => {
        this.recalling.set(false);
        this.toast.success('Recall aberto.');
        this.router.navigate(['/traceability/recalls']);
      },
      error: (e: { detail?: string }) =>
        this.recallError.set(e.detail ?? 'Não foi possível abrir o recall.'),
    });
  }

  protected startQuarantine(): void {
    this.quarantining.set(true);
    this.quarantineError.set(null);
    this.quarantineForm.reset({ reason: '' });
  }

  protected cancelQuarantine(): void {
    this.quarantining.set(false);
  }

  /** Quarentena o nó raiz — o que está na tela é o que se contém. */
  protected confirmQuarantine(): void {
    const query = this.store.query();
    if (!query || this.quarantineForm.invalid) {
      this.quarantineForm.markAllAsTouched();
      return;
    }
    this.quarantines
      .open(query.nodeType, query.nodeId, this.quarantineForm.getRawValue().reason)
      .subscribe({
        next: () => {
          this.quarantining.set(false);
          this.toast.success('Quarentena aberta.');
          // Levar para a lista é o passo seguinte natural: é lá que se vê o que ficou parado.
          this.router.navigate(['/traceability/quarantines']);
        },
        error: (e: { code?: string; detail?: string }) =>
          this.quarantineError.set(
            e.code === 'already_quarantined'
              ? 'Este item já está em quarentena. Abra a que existe em vez de criar outra.'
              : (e.detail ?? 'Não foi possível abrir a quarentena.'),
          ),
      });
  }
}
