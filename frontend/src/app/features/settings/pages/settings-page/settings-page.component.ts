import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';

/** Um cartão de acesso a uma área de configuração/segurança. */
interface SettingsCard {
  readonly title: string;
  readonly description: string;
  readonly icon: string;
  readonly route: string;
  /** Sufixo de cor Bootstrap para o distintivo do ícone (`primary`, `info`, …). */
  readonly accent: string;
  /** Permissões que liberam o acesso (basta uma). */
  readonly permissions: readonly string[];
}

/** Seção de cartões agrupados sob um rótulo. */
interface SettingsSection {
  readonly title: string;
  readonly icon: string;
  readonly cards: readonly SettingsCard[];
}

@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './settings-page.component.html',
  styleUrl: './settings-page.component.css',
})
export class SettingsPageComponent {
  private readonly auth = inject(AuthService);

  private readonly securityCards: readonly SettingsCard[] = [
    {
      title: 'Usuários',
      description: 'Contas com acesso à cervejaria, convites e bloqueios.',
      icon: 'ri-group-line',
      route: '/security/users',
      accent: 'primary',
      permissions: ['security.user.read'],
    },
    {
      title: 'Grupos',
      description: 'Papéis, permissões e associações de acesso.',
      icon: 'ri-shield-keyhole-line',
      route: '/security/groups',
      accent: 'indigo',
      permissions: ['security.group.read', 'security.permission.read'],
    },
    {
      title: 'Acesso temporário',
      description: 'Concessões com prazo de expiração automático.',
      icon: 'ri-time-line',
      route: '/security/temporary-access',
      accent: 'info',
      permissions: ['security.temporary-access.read'],
    },
    {
      title: 'Revisão de acessos',
      description: 'Campanhas de revisão e segregação de funções.',
      icon: 'ri-list-check-2',
      route: '/security/access-review',
      accent: 'teal',
      permissions: ['security.access-review.read', 'security.segregation.manage'],
    },
    {
      title: 'Alertas',
      description: 'Eventos de segurança monitorados e notificações.',
      icon: 'ri-alarm-warning-line',
      route: '/security/alerts',
      accent: 'warning',
      permissions: ['security.alert.read'],
    },
    {
      title: 'Auditoria',
      description: 'Trilha de auditoria com filtros e paginação.',
      icon: 'ri-file-list-3-line',
      route: '/security/audit',
      accent: 'secondary',
      permissions: ['security.audit.read'],
    },
    {
      title: 'Contas de serviço',
      description: 'Credenciais de integração e automações.',
      icon: 'ri-robot-line',
      route: '/security/service-accounts',
      accent: 'success',
      permissions: ['security.service-account.read'],
    },
    {
      title: 'Federação',
      description: 'Provedores de identidade externa e SSO.',
      icon: 'ri-links-line',
      route: '/security/federation',
      accent: 'danger',
      permissions: ['security.federation.read'],
    },
  ];

  private readonly operationCards: readonly SettingsCard[] = [
    {
      title: 'Parametrização',
      description: 'Validade de CIP, prazos do CAPA, calibração, gás e escala sensorial.',
      icon: 'ri-sliders-line',
      route: '/settings/parameters',
      accent: 'primary',
      permissions: [
        'sanitation.cycle.read',
        'gas.read',
        'metrology.instrument.read',
        'quality.nc.read',
        'sensory.session.read',
      ],
    },
  ];

  /** Seções com apenas os cartões que o usuário tem permissão de acessar. */
  protected readonly sections = computed<SettingsSection[]>(() => {
    const operation = this.operationCards.filter(card => this.auth.hasAnyPermission([...card.permissions]));
    const security = this.securityCards.filter(card => this.auth.hasAnyPermission([...card.permissions]));
    const sections: SettingsSection[] = [];
    if (operation.length > 0) {
      sections.push({ title: 'Operação', icon: 'ri-equalizer-line', cards: operation });
    }
    if (security.length > 0) {
      sections.push({ title: 'Segurança e acesso', icon: 'ri-shield-check-line', cards: security });
    }
    return sections;
  });

  protected readonly hasAny = computed(() => this.sections().length > 0);
}
