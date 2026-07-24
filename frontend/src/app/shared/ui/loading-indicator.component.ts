import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Indicador de carregamento consistente (spinner + texto). */
@Component({
  selector: 'app-loading-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex align-items-center justify-content-center text-muted py-5" role="status">
      <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
      <span>{{ text() }}</span>
    </div>
  `,
})
export class LoadingIndicatorComponent {
  readonly text = input('Carregando…');
}
