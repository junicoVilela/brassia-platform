/** Dossiê do lote (RPT-001): consolidação derivada, montada a cada pedido. */

export interface PlannedMaterial {
  ingredientId: string;
  quantity: number;
  unit: string;
}

export interface ReportPlan {
  volumeLiters: number;
  /** Vazio quando não há plano confiável; a lacuna correspondente diz por quê. */
  materials: PlannedMaterial[];
}

export interface PackagingRun {
  planCode: string;
  plannedVolumeLiters: number;
  packagedVolumeLiters: number;
  rejectedVolumeLiters: number;
  lossesLiters: number;
}

export interface ReportExecution {
  transferredVolumeLiters: number | null;
  transferLossesLiters: number | null;
  transferred: boolean;
  packaged: boolean;
  packaging: PackagingRun[];
}

export interface QualityMeasurement {
  parameter: string;
  value: number;
  unit: string;
  measuredAt: string;
}

export interface QualityDeviation {
  parameter: string;
  severity: 'MINOR' | 'MAJOR' | 'CRITICAL';
  status: 'OPEN' | 'CLOSED';
  limitValue: number;
  measuredValue: number;
  unit: string;
  openedAt: string;
}

export interface QualityNonConformity {
  code: string;
  title: string;
  severity: string;
  status: string;
}

export interface ReportQuality {
  measurements: number;
  withinSpec: number;
  /** Ninguém mediu nada — que não é o mesmo que o lote ter passado. */
  unmeasured: boolean;
  outOfSpec: QualityMeasurement[];
  deviations: QualityDeviation[];
  nonConformities: QualityNonConformity[];
}

export interface ReportCost {
  total: number;
  costPerLiter: number;
  volumeLiters: number;
  closed: boolean;
  incomplete: boolean;
  gaps: string[];
}

export interface LineageEntry {
  type: string;
  label: string;
}

export interface ReportLineage {
  origins: LineageEntry[];
  destinations: LineageEntry[];
  gaps: string[];
  truncated: boolean;
  /** Falso com elo faltando ou travessia truncada: não prova rastreabilidade. */
  complete: boolean;
}

export interface BatchReport {
  batchId: string;
  batchCode: string;
  recipeName: string;
  recipeVersion: number;
  status: string;
  /** Quando o documento foi montado — relatório derivado sem data é indefensável. */
  generatedAt: string;
  incomplete: boolean;
  plan: ReportPlan;
  execution: ReportExecution;
  quality: ReportQuality;
  /** Nulo quando o custo não pôde ser apurado; a lacuna diz por quê. */
  cost: ReportCost | null;
  lineage: ReportLineage;
  gaps: string[];
}

export const NODE_TYPE_LABELS: Record<string, string> = {
  STOCK_LOT: 'Lote de insumo',
  BREW_ORDER: 'Ordem de produção',
  BATCH: 'Lote de produção',
  YEAST_HARVEST: 'Coleta de levedura',
  PACKAGING_PLAN: 'Plano de envase',
  PACKAGING_RUN: 'Execução de envase',
  FINISHED_LOT: 'Produto acabado',
  SHIPMENT: 'Expedição',
};
