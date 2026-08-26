/**
 * Estrutura do menu lateral.
 *
 * A lista era plana e escrita à mão no template (63 itens, ~500 linhas). Aqui ela
 * vira dado: cada nó é um link ou um grupo com filhos, e o template só renderiza.
 * Assim a ordem, o agrupamento e a permissão de cada tela ficam num lugar só —
 * conferíveis por teste (`menu.spec.ts`) em vez de por leitura de marcação.
 *
 * `permission` ausente = item visível para qualquer sessão autenticada. Um grupo
 * não declara permissão: ele aparece quando ao menos um filho aparece.
 */
export interface MenuNode {
  /** Identificador estável do nó — usado no `track` e no estado de abertura. */
  readonly id: string;
  readonly label: string;
  /** Ícone do nó. Só grupos e atalhos de topo levam ícone; o filho usa o fio-guia. */
  readonly icon?: string;
  /** Rota do link. Ausente em grupos. */
  readonly route?: string;
  /** Casa a rota exatamente (para pais que também são rota, ex. `/water`). */
  readonly exact?: boolean;
  /** Permissão exigida; o item some sem ela. */
  readonly permission?: string;
  /** Permissões alternativas: basta uma. */
  readonly anyPermission?: readonly string[];
  /** Filhos do grupo. Ausente em links. */
  readonly items?: readonly MenuNode[];
}

export const MENU: readonly MenuNode[] = [
  {
    id: 'scan',
    label: 'Abrir código',
    icon: 'ri-qr-scan-2-line',
    route: '/scan',
  },
  {
    id: 'cadastros',
    label: 'Cadastros',
    icon: 'ri-archive-drawer-line',
    items: [
      { id: 'breweries', label: 'Cervejarias', route: '/breweries' },
      { id: 'equipment', label: 'Equipamentos', route: '/equipment', exact: true },
      { id: 'maintenance', label: 'Manutenção', route: '/equipment/maintenance' },
      { id: 'catalog', label: 'Ingredientes', route: '/catalog' },
      {
        id: 'containers',
        label: 'Contêineres',
        route: '/containers',
        permission: 'container.read',
      },
      { id: 'reference', label: 'Dados de referência', route: '/reference', exact: true },
      { id: 'styles', label: 'Estilos', route: '/reference/styles' },
    ],
  },
  {
    id: 'formulacao',
    label: 'Receitas e água',
    icon: 'ri-book-2-line',
    items: [
      { id: 'recipes', label: 'Receitas', route: '/recipes' },
      { id: 'calculators', label: 'Calculadoras', route: '/calculators' },
      { id: 'water', label: 'Água', route: '/water', exact: true },
      { id: 'water-blend', label: 'Mistura', route: '/water/blend' },
      {
        id: 'community-library',
        label: 'Biblioteca',
        route: '/community/library',
        permission: 'community.library.read',
      },
    ],
  },
  {
    id: 'planejamento',
    label: 'Planejamento',
    icon: 'ri-calendar-schedule-line',
    items: [
      {
        id: 'forecast',
        label: 'Previsão',
        route: '/forecast',
        permission: 'forecast.demand.read',
      },
      {
        id: 'planning',
        label: 'Agenda de produção',
        route: '/planning',
        permission: 'planning.schedule.read',
      },
      {
        id: 'brew-orders',
        label: 'Ordens de produção',
        route: '/brew-orders',
        permission: 'planning.order.read',
      },
    ],
  },
  {
    id: 'suprimentos',
    label: 'Suprimentos',
    icon: 'ri-shopping-cart-2-line',
    items: [
      {
        id: 'inventory',
        label: 'Estoque',
        route: '/inventory',
        exact: true,
        permission: 'inventory.lot.read',
      },
      {
        id: 'inventory-counts',
        label: 'Inventário físico',
        route: '/inventory/counts',
        permission: 'inventory.count.read',
      },
      {
        id: 'suppliers',
        label: 'Fornecedores',
        route: '/suppliers',
        permission: 'purchasing.supplier.read',
      },
      {
        id: 'purchase-needs',
        label: 'Necessidade de compra',
        route: '/purchasing/needs',
        permission: 'purchasing.purchase.read',
      },
      {
        id: 'shopping-list',
        label: 'Lista de compras',
        route: '/purchasing/shopping-list',
        permission: 'purchasing.purchase.read',
      },
    ],
  },
  {
    id: 'fabricacao',
    label: 'Fabricação',
    icon: 'ri-temp-hot-line',
    items: [
      {
        id: 'batches',
        label: 'Lotes de produção',
        route: '/production/batches',
        permission: 'production.batch.read',
      },
      {
        id: 'fermentation-profiles',
        label: 'Perfis de fermentação',
        route: '/fermentation/profiles',
        permission: 'fermentation.profile.read',
      },
      {
        id: 'fermentation-schedule',
        label: 'Linha do tempo',
        route: '/fermentation/schedule',
        permission: 'fermentation.schedule.read',
      },
      {
        id: 'fermentation-readings',
        label: 'Leituras e curvas',
        route: '/fermentation/readings',
        permission: 'fermentation.reading.read',
      },
      {
        id: 'fermentation-yeast',
        label: 'Coletas de levedura',
        route: '/fermentation/yeast',
        permission: 'fermentation.yeast.read',
      },
      {
        id: 'blends',
        label: 'Blend e reprocesso',
        route: '/blends',
        permission: 'blend.operation.read',
      },
      { id: 'gas', label: 'Gases e CO₂', route: '/gas', permission: 'gas.read' },
    ],
  },
  {
    id: 'limpeza',
    label: 'Limpeza',
    icon: 'ri-drop-line',
    items: [
      {
        id: 'sanitation-procedures',
        label: 'POPs de limpeza',
        route: '/sanitation/procedures',
        permission: 'sanitation.procedure.read',
      },
      {
        id: 'sanitation-matrix',
        label: 'Matriz de limpeza',
        route: '/sanitation/matrix',
        permission: 'sanitation.matrix.read',
      },
      {
        id: 'sanitation-cycles',
        label: 'Ciclos de limpeza',
        route: '/sanitation/cycles',
        permission: 'sanitation.cycle.read',
      },
    ],
  },
  {
    id: 'envase',
    label: 'Envase',
    icon: 'ri-inbox-archive-line',
    items: [
      {
        id: 'packaging-plans',
        label: 'Planos de envase',
        route: '/packaging/plans',
        permission: 'packaging.plan.read',
      },
      {
        id: 'finished-lots',
        label: 'Produto acabado',
        route: '/packaging/finished-lots',
        permission: 'packaging.plan.read',
      },
    ],
  },
  {
    id: 'qualidade',
    label: 'Qualidade',
    icon: 'ri-shield-check-line',
    items: [
      {
        id: 'quality-control-plans',
        label: 'Planos de controle',
        route: '/quality/control-plans',
        permission: 'quality.plan.read',
      },
      {
        id: 'sensory-sessions',
        label: 'Sessões sensoriais',
        route: '/sensory/sessions',
        permission: 'sensory.session.read',
      },
      {
        id: 'metrology-instruments',
        label: 'Instrumentos',
        route: '/metrology/instruments',
        permission: 'metrology.instrument.read',
      },
      {
        id: 'allergens',
        label: 'Matriz de alergênicos',
        route: '/food-safety/allergens',
        permission: 'foodsafety.allergen.read',
      },
      {
        id: 'field-feedback',
        label: 'Feedback de campo',
        route: '/field-feedback',
        permission: 'feedback.complaint.read',
      },
    ],
  },
  {
    id: 'rastreabilidade',
    label: 'Rastreabilidade',
    icon: 'ri-route-line',
    items: [
      {
        id: 'genealogy',
        label: 'Genealogia',
        route: '/traceability/genealogy',
        permission: 'traceability.genealogy.read',
      },
      {
        id: 'recalls',
        label: 'Recalls',
        route: '/traceability/recalls',
        permission: 'traceability.recall.read',
      },
      {
        id: 'recall-drills',
        label: 'Simulados de recall',
        route: '/traceability/recall-drills',
        permission: 'traceability.drill.read',
      },
      {
        id: 'quarantines',
        label: 'Quarentenas',
        route: '/traceability/quarantines',
        permission: 'traceability.quarantine.read',
      },
    ],
  },
  {
    id: 'vendas',
    label: 'Vendas',
    icon: 'ri-store-2-line',
    items: [
      {
        id: 'sales-catalog',
        label: 'Produtos e preços',
        route: '/sales/catalog',
        permission: 'sales.catalog.read',
      },
      {
        id: 'crm-customers',
        label: 'Clientes',
        route: '/crm/customers',
        permission: 'crm.customer.read',
      },
      {
        id: 'sales-orders',
        label: 'Pedidos',
        route: '/sales/orders',
        permission: 'sales.order.read',
      },
      {
        id: 'distribution-loads',
        label: 'Cargas',
        route: '/distribution/loads',
        permission: 'distribution.load.read',
      },
      {
        id: 'portal',
        label: 'Meus pedidos',
        route: '/portal',
        permission: 'portal.access',
      },
    ],
  },
  {
    id: 'custos',
    label: 'Custos',
    icon: 'ri-money-dollar-circle-line',
    items: [
      {
        id: 'costing-batches',
        label: 'Custo do lote',
        route: '/costing/batches',
        permission: 'costing.cost.read',
      },
      {
        id: 'costing-variance',
        label: 'Planejado × real',
        route: '/costing/variance',
        permission: 'costing.variance.read',
      },
    ],
  },
  {
    id: 'relatorios',
    label: 'Relatórios',
    icon: 'ri-dashboard-line',
    items: [
      {
        id: 'reporting-dashboard',
        label: 'Painel operacional',
        route: '/reporting/dashboard',
        permission: 'reporting.dashboard.read',
      },
      {
        id: 'reporting-batches',
        label: 'Relatório do lote',
        route: '/reporting/batches',
        permission: 'reporting.batch.read',
      },
      {
        id: 'reporting-saved',
        label: 'Relatórios salvos',
        route: '/reporting/saved-reports',
        permission: 'reporting.saved.read',
      },
      {
        id: 'utilities-indicators',
        label: 'Consumo por litro',
        route: '/utilities/indicators',
        permission: 'utilities.indicator.read',
      },
    ],
  },
  {
    id: 'pesquisa',
    label: 'Desenvolvimento',
    icon: 'ri-test-tube-line',
    items: [
      {
        id: 'experiments',
        label: 'Experimentos',
        route: '/experiments',
        permission: 'experiment.plan.read',
      },
      {
        id: 'optimization',
        label: 'Otimização assistida',
        route: '/optimization',
        permission: 'optimization.run.read',
      },
      {
        id: 'digital-twin',
        label: 'Gêmeo digital',
        route: '/digital-twin',
        permission: 'digitaltwin.profile.read',
      },
    ],
  },
  {
    id: 'ia',
    label: 'Copiloto',
    icon: 'ri-robot-2-line',
    items: [
      {
        id: 'ai-copilot',
        label: 'Perguntar ao copiloto',
        route: '/ai/copilot',
        permission: 'ai.answer.ask',
      },
      {
        id: 'ai-assessments',
        label: 'Avaliar lote',
        route: '/ai/assessments',
        permission: 'ai.assessment.batch',
      },
      {
        id: 'ai-proposals',
        label: 'Propostas do copiloto',
        route: '/ai/proposals',
        permission: 'ai.command.read',
      },
      {
        id: 'knowledge',
        label: 'Base de conhecimento',
        route: '/knowledge',
        permission: 'knowledge.document.read',
      },
      {
        id: 'ai-gateway',
        label: 'Uso e custo da IA',
        route: '/ai/gateway',
        permission: 'ai.gateway.read',
      },
    ],
  },
  {
    id: 'integracoes',
    label: 'Integrações',
    icon: 'ri-plug-line',
    items: [
      {
        id: 'sensors',
        label: 'Sensores',
        route: '/sensors',
        permission: 'sensor.reading.read',
      },
      {
        id: 'webhooks',
        label: 'Webhooks',
        route: '/integration/webhooks',
        permission: 'integration.webhook.read',
      },
    ],
  },
  {
    id: 'settings',
    label: 'Configurações',
    icon: 'ri-settings-3-line',
    route: '/settings',
    anyPermission: [
      'security.user.read',
      'security.group.read',
      'security.permission.read',
      'security.temporary-access.read',
      'security.access-review.read',
      'security.segregation.manage',
      'security.alert.read',
      'security.audit.read',
      'security.service-account.read',
      'security.federation.read',
    ],
  },
];
