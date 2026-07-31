export type ReadingKind = 'DENSITY' | 'TEMPERATURE' | 'PRESSURE' | 'PH';
export type ReadingSource = 'MANUAL' | 'SENSOR';

export const READING_SOURCES: ReadingSource[] = ['MANUAL', 'SENSOR'];

/** Unidades aceitas por grandeza (a primeira é o padrão do formulário). */
export const READING_UNITS: Record<ReadingKind, string[]> = {
  DENSITY: ['SG', 'PLATO'],
  TEMPERATURE: ['C', 'F'],
  PRESSURE: ['PSI', 'BAR'],
  PH: ['PH'],
};

export const READING_KINDS = Object.keys(READING_UNITS) as ReadingKind[];

export const READING_KIND_LABELS: Record<ReadingKind, string> = {
  DENSITY: 'Densidade',
  TEMPERATURE: 'Temperatura',
  PRESSURE: 'Pressão',
  PH: 'pH',
};

export interface FermentationReading {
  id: string;
  batchId: string;
  kind: ReadingKind;
  source: ReadingSource;
  value: number;
  unit: string;
  measuredAt: string;
  valid: boolean;
  invalidReason: string | null;
}

/** Recorte do lote de produção usado apenas no seletor de leituras. */
export interface BatchOption {
  id: string;
  code: string;
  recipeName: string;
}

export interface RecordReadingRequest {
  batchId: string;
  kind: ReadingKind;
  source: ReadingSource;
  value: number;
  unit: string;
  measuredAt: string;
}

export type FgStabilityVerdict =
  | 'STABLE'
  | 'INSUFFICIENT_READINGS'
  | 'WINDOW_NOT_COVERED'
  | 'VARIATION_ABOVE_TOLERANCE';

/** Como explicar cada veredito ao cervejeiro — o parecer nunca encerra a fermentação sozinho. */
export const FG_VERDICT_LABELS: Record<FgStabilityVerdict, string> = {
  STABLE: 'Série cobre a janela e varia dentro da tolerância.',
  INSUFFICIENT_READINGS: 'Leituras de densidade válidas em SG insuficientes para o critério.',
  WINDOW_NOT_COVERED: 'As leituras não cobrem a janela — estabilidade ainda não comprovada.',
  VARIATION_ABOVE_TOLERANCE: 'A densidade ainda varia acima da tolerância.',
};

export interface FgStability {
  stable: boolean;
  verdict: FgStabilityVerdict;
  policy: { windowHours: number; minReadings: number; toleranceSg: number };
  spanHours: number;
  amplitudeSg: number;
  readings: FermentationReading[];
}
