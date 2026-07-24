import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Estado vazio consistente: ícone suave + mensagem + dica opcional. */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="text-center text-muted py-5">
      <i class="{{ icon() }} d-block mx-auto mb-2" style="font-size: 2.5rem; line-height: 1; opacity: .55;"></i>
      <p class="mb-0 fw-medium">{{ message() }}</p>
      @if (hint()) {
        <p class="small mb-0 mt-1">{{ hint() }}</p>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  readonly icon = input('ri-inbox-2-line');
  readonly message = input.required<string>();
  readonly hint = input<string>('');
}
