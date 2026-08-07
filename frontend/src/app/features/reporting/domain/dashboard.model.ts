/** Painel operacional (RPT-002): indicadores derivados, coletados de cinco módulos. */

export type IndicatorGroup = 'PRODUCTION' | 'STOCK' | 'QUALITY' | 'FERMENTATION' | 'COST';

export const GROUP_LABELS: Record<IndicatorGroup, string> = {
  PRODUCTION: 'Produção',
  STOCK: 'Estoque',
  QUALITY: 'Qualidade',
  FERMENTATION: 'Fermentação',
  COST: 'Custo',
};

export const GROUP_ICONS: Record<IndicatorGroup, string> = {
  PRODUCTION: 'ri-flask-line',
  STOCK: 'ri-archive-2-line',
  QUALITY: 'ri-shield-check-line',
  FERMENTATION: 'ri-bubble-chart-line',
  COST: 'ri-money-dollar-circle-line',
};

/** Recurso e filtro onde o número se abre. A rota é da interface, não do backend. */
export interface DrillDown {
  resource: string;
  filter: Record<string, string>;
}

export interface OperationalIndicator {
  code: string;
  group: IndicatorGroup;
  label: string;
  /** O que o número quer dizer. Nunca vazio — o backend não constrói indicador sem. */
  definition: string;
  value: number;
  unit: string;
  /** Nulo significa posição, não ausência: é a foto do instante `to`. */
  from: string | null;
  to: string;
  positional: boolean;
  drillDown: DrillDown;
  /** O que este número não cobre; nulo quando não há o que ressalvar. */
  gap: string | null;
}

export interface Dashboard {
  from: string;
  to: string;
  /** Quantos módulos contribuíram — é o que permite notar que o painel encolheu. */
  sources: number;
  indicators: OperationalIndicator[];
}

/**
 * Onde cada recurso se abre.
 *
 * <p>O mapa mora na interface de propósito: o backend diz "isto se abre nos lotes de produção", e
 * traduzir isso em endereço é assunto de quem tem as rotas. Recurso sem rota conhecida não vira
 * link quebrado — vira cartão sem link.
 */
export const DRILL_DOWN_ROUTES: Record<string, string> = {
  'production.batches': '/production/batches',
  'inventory.lots': '/inventory',
  'quality.controlPlans': '/quality/control-plans',
  // `quality.nonConformities` fica de fora: ainda não há tela de NC. O cartão aparece sem link,
  // que é melhor do que um link para lugar nenhum.
  'fermentation.readings': '/fermentation/readings',
  'costing.batchCosts': '/costing/batches',
};
