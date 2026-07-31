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
