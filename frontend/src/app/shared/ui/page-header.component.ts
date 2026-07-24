import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Cabeçalho de página padrão: título à esquerda e um slot de ações à direita
 * (`<app-page-header title="…"><button>…</button></app-page-header>`).
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <div>
        <h1 class="h4 mb-0 fw-semibold">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="text-muted small mb-0 mt-1">{{ subtitle() }}</p>
        }
      </div>
      <div class="d-flex align-items-center flex-wrap gap-2">
        <ng-content />
      </div>
    </div>
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
}
