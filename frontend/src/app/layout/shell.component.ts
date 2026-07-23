import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

/**
 * Shell de layout (tema Fila): sidebar + header + área de conteúdo + footer.
 * O colapso da sidebar usa o atributo `sidebar-data-theme=sidebar-hide` num
 * ancestral de `.sidebar-area` (convenção do tema), aqui o host do componente.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  host: {
    '[attr.sidebar-data-theme]': "collapsed() ? 'sidebar-hide' : null",
  },
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly collapsed = signal(false);

  protected toggle(): void {
    this.collapsed.update(value => !value);
  }

  protected initials(): string {
    const name = this.auth.user()?.displayName?.trim();
    return name ? name.charAt(0).toUpperCase() : 'B';
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => void this.router.navigateByUrl('/login'),
    });
  }

  protected selectBrewery(breweryId: string): void {
    if (this.auth.user()?.activeBrewery?.id === breweryId) {
      return;
    }
    this.auth.switchBrewery(breweryId).subscribe();
  }
}
