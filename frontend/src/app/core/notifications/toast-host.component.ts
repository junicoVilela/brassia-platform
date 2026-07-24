import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from './toast.service';
import { ToastKind } from './toast.model';

/** Pilha de toasts sobreposta (canto superior direito), renderizada no shell. */
@Component({
  selector: 'app-toast-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './toast-host.component.html',
})
export class ToastHostComponent {
  protected readonly toast = inject(ToastService);

  protected icon(kind: ToastKind): string {
    switch (kind) {
      case 'success':
        return 'ri-checkbox-circle-line';
      case 'error':
        return 'ri-error-warning-line';
      default:
        return 'ri-information-line';
    }
  }

  protected accent(kind: ToastKind): string {
    switch (kind) {
      case 'success':
        return 'text-success';
      case 'error':
        return 'text-danger';
      default:
        return 'text-primary';
    }
  }
}
