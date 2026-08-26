import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../core/auth/auth.service';
import { ToastHostComponent } from '../core/notifications/toast-host.component';
import { ThemeModeService } from '../core/theme/theme-mode.service';
import { UiSearchService } from '../core/search/ui-search.service';
import { MENU, MenuNode } from './menu';

/**
 * Shell de layout (tema Fila): sidebar + header + área de conteúdo + footer.
 * O colapso da sidebar usa o atributo `sidebar-data-theme=sidebar-hide` num
 * ancestral de `.sidebar-area` (convenção do tema), aqui o host do componente.
 *
 * O menu vem de `menu.ts` e é agrupado por categoria. O acordeão é estado do
 * componente (o `.open` do tema é só CSS): o grupo da rota atual abre sozinho e
 * os demais o usuário abre à mão — vários podem ficar abertos ao mesmo tempo.
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

  /** Opções de `routerLinkActive` — referências fixas para não recriar objeto a cada CD. */
  protected readonly exactMatch = { exact: true } as const;
  protected readonly prefixMatch = { exact: false } as const;

  private readonly currentUrl = signal(this.router.url);
  private readonly openGroups = signal<ReadonlySet<string>>(new Set<string>());

  /** Menu já filtrado pelas permissões da sessão; grupo sem filho visível some. */
  protected readonly menu = computed(() => this.visibleNodes(MENU));

  constructor() {
    this.openActiveGroup(this.router.url);
    this.router.events
      .pipe(
        filter(event => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(event => {
        // A busca é contextual à tela; limpa ao trocar de rota.
        this.search.clear();
        this.currentUrl.set(event.urlAfterRedirects);
        this.openActiveGroup(event.urlAfterRedirects);
      });
  }

  protected isOpen(groupId: string): boolean {
    return this.openGroups().has(groupId);
  }

  protected toggleGroup(groupId: string): void {
    this.openGroups.update(open => {
      const next = new Set(open);
      if (!next.delete(groupId)) {
        next.add(groupId);
      }
      return next;
    });
  }

  /** true quando a rota atual está dentro do grupo. */
  protected isGroupActive(group: MenuNode): boolean {
    const url = this.currentUrl();
    return (group.items ?? []).some(child => this.matchesUrl(child, url));
  }

  /**
   * Destaca o próprio grupo quando o filho ativo não está à vista: com o grupo
   * fechado, ou na sidebar recolhida, onde o submenu inteiro fica escondido e
   * sem isto não sobra nenhuma pista de onde a pessoa está.
   */
  protected isGroupHighlighted(group: MenuNode): boolean {
    return this.isGroupActive(group) && (this.collapsed() || !this.isOpen(group.id));
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

  private visibleNodes(nodes: readonly MenuNode[]): MenuNode[] {
    const visible: MenuNode[] = [];
    for (const node of nodes) {
      if (!this.isPermitted(node)) {
        continue;
      }
      if (!node.items) {
        visible.push(node);
        continue;
      }
      const items = this.visibleNodes(node.items);
      if (items.length > 0) {
        visible.push({ ...node, items });
      }
    }
    return visible;
  }

  private isPermitted(node: MenuNode): boolean {
    if (node.permission && !this.auth.hasPermission(node.permission)) {
      return false;
    }
    return !node.anyPermission || this.auth.hasAnyPermission([...node.anyPermission]);
  }

  private openActiveGroup(url: string): void {
    const active = MENU.find(node => node.items?.some(child => this.matchesUrl(child, url)));
    if (!active || this.openGroups().has(active.id)) {
      return;
    }
    this.openGroups.update(open => new Set(open).add(active.id));
  }

  private matchesUrl(node: MenuNode, url: string): boolean {
    if (!node.route) {
      return false;
    }
    const path = url.split(/[?#]/)[0];
    return path === node.route || path.startsWith(`${node.route}/`);
  }
}
