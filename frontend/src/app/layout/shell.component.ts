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
  template: `
    <div class="sidebar-area" id="sidebar-area">
      <div class="logo position-relative d-flex align-items-center justify-content-between">
        <a routerLink="/" class="d-flex align-items-center gap-2 text-decoration-none">
          <img src="assets/fila/images/logo-icon.png" alt="BrassIA" width="32" height="32">
          <span class="logo-text text-secondary fw-semibold">BrassIA</span>
        </a>
        <button type="button" class="sidebar-burger-menu bg-transparent p-0 border-0 d-lg-none"
                (click)="toggle()" aria-label="Alternar menu">
          <i class="ri-menu-line fs-4"></i>
        </button>
      </div>

      <aside id="layout-menu" class="layout-menu menu-vertical menu active">
        <ul class="menu-inner">
          <li class="menu-title small text-uppercase"><span class="menu-title-text">Operação</span></li>
          <li class="menu-item">
            <a routerLink="/recipes" routerLinkActive="active" class="menu-link">
              <i class="ri-book-2-line menu-icon"></i>
              <span class="title">Receitas</span>
            </a>
          </li>
          <li class="menu-item">
            <a routerLink="/breweries" routerLinkActive="active" class="menu-link">
              <i class="ri-store-2-line menu-icon"></i>
              <span class="title">Cervejarias</span>
            </a>
          </li>
          <li class="menu-item">
            <a routerLink="/catalog" routerLinkActive="active" class="menu-link">
              <i class="ri-flask-line menu-icon"></i>
              <span class="title">Ingredientes</span>
            </a>
          </li>
          <li class="menu-item">
            <a routerLink="/equipment" routerLinkActive="active" class="menu-link" [routerLinkActiveOptions]="{ exact: true }">
              <i class="ri-timer-flash-line menu-icon"></i>
              <span class="title">Equipamentos</span>
            </a>
          </li>
          <li class="menu-item">
            <a routerLink="/equipment/maintenance" routerLinkActive="active" class="menu-link">
              <i class="ri-tools-line menu-icon"></i>
              <span class="title">Manutenção</span>
            </a>
          </li>
          <li class="menu-title small text-uppercase"><span class="menu-title-text">Segurança</span></li>
          <li class="menu-item">
            <a routerLink="/security/users" routerLinkActive="active" class="menu-link">
              <i class="ri-group-line menu-icon"></i>
              <span class="title">Usuários</span>
            </a>
          </li>
          <li class="menu-item">
            <a routerLink="/security/groups" routerLinkActive="active" class="menu-link">
              <i class="ri-shield-keyhole-line menu-icon"></i>
              <span class="title">Grupos</span>
            </a>
          </li>
        </ul>
      </aside>
    </div>

    <div class="main-content d-flex flex-column">
      <header class="header-area bg-white mb-4 rounded-10 border border-white" id="header-area">
        <div class="d-md-flex align-items-center justify-content-between">
          <div class="left-header-content">
            <button type="button" class="header-burger-menu bg-transparent p-0 border-0"
                    (click)="toggle()" aria-label="Alternar menu">
              <i class="ri-menu-line fs-4"></i>
            </button>
          </div>
          <div class="right-header-content mt-3 mt-md-0">
            <ul class="d-flex align-items-center gap-2 mb-0 ps-0 list-unstyled">
              @if (auth.user()?.activeBrewery; as active) {
                <li class="dropdown">
                  <a href="javascript:void(0);" class="dropdown-toggle d-flex align-items-center gap-2 text-decoration-none text-body"
                     data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="ri-store-2-line"></i>
                    <span class="fw-medium">{{ active.name }}</span>
                  </a>
                  <ul class="dropdown-menu dropdown-menu-end">
                    <li><h6 class="dropdown-header">Cervejaria ativa</h6></li>
                    @for (brewery of auth.user()?.accessibleBreweries ?? []; track brewery.id) {
                      <li>
                        <button type="button" class="dropdown-item d-flex align-items-center justify-content-between"
                                (click)="selectBrewery(brewery.id)">
                          {{ brewery.name }}
                          @if (brewery.id === active.id) { <i class="ri-check-line text-primary"></i> }
                        </button>
                      </li>
                    }
                  </ul>
                </li>
              }
              <li class="dropdown">
                <a href="javascript:void(0);" class="dropdown-toggle d-flex align-items-center gap-2 text-decoration-none text-body"
                   data-bs-toggle="dropdown" aria-expanded="false">
                  <span class="rounded-circle bg-primary text-white d-flex align-items-center justify-content-center"
                        style="width:36px;height:36px;">{{ initials() }}</span>
                  <span class="d-none d-sm-inline fw-medium">{{ auth.user()?.displayName ?? 'Conta' }}</span>
                </a>
                <ul class="dropdown-menu dropdown-menu-end">
                  <li><button type="button" class="dropdown-item" (click)="logout()">Sair</button></li>
                </ul>
              </li>
            </ul>
          </div>
        </div>
      </header>

      <div class="main-content-container overflow-hidden">
        <router-outlet />
      </div>

      <footer class="footer-area bg-white text-center rounded-10 rounded-bottom-0 mt-4">
        <p class="mb-0 py-3 text-muted small">BrassIA — Plataforma inteligente de gestão cervejeira</p>
      </footer>
    </div>
  `,
  styles: `
    :host { display: block; }
  `,
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
