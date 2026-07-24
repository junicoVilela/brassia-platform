import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../core/auth/auth.service';
import { ToastHostComponent } from '../core/notifications/toast-host.component';
import { ThemeModeService } from '../core/theme/theme-mode.service';
import { UiSearchService } from '../core/search/ui-search.service';

/**
 * Shell de layout (tema Fila): sidebar + header + área de conteúdo + footer.
 * O colapso da sidebar usa o atributo `sidebar-data-theme=sidebar-hide` num
 * ancestral de `.sidebar-area` (convenção do tema), aqui o host do componente.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastHostComponent],
  host: {
    '[attr.sidebar-data-theme]': "collapsed() ? 'sidebar-hide' : null",
  },
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeModeService);
  protected readonly search = inject(UiSearchService);
  private readonly router = inject(Router);
  protected readonly collapsed = signal(false);

  constructor() {
    // A busca é contextual à tela; limpa ao trocar de rota.
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.search.clear());
  }

  protected toggle(): void {
    this.collapsed.update(value => !value);
  }

  protected onSearch(value: string): void {
    this.search.set(value);
  }

  protected toggleTheme(): void {
    this.theme.toggle();
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
