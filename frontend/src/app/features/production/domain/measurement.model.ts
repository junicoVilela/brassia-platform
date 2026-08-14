export const MEASUREMENT_KINDS: { value: string; label: string; units: string[] }[] = [
  { value: 'DENSITY', label: 'Densidade', units: ['SG', 'PLATO'] },
  { value: 'TEMPERATURE', label: 'Temperatura', units: ['C', 'F'] },
  { value: 'VOLUME', label: 'Volume', units: ['L', 'ML'] },
  { value: 'PH', label: 'pH', units: ['PH'] },
  { value: 'COLOR', label: 'Cor', units: ['EBC', 'SRM'] },
  { value: 'IBU', label: 'Amargor', units: ['IBU'] },
  // Único que se mede na cerveja pronta, e não na brassa (PKG-004-B): quando registrado, é ele que vai
  // para o rótulo no lugar do ABV calculado pela receita.
  { value: 'ABV', label: 'Álcool medido', units: ['%ABV'] },
];

export const MEASUREMENT_SOURCES: { value: string; label: string }[] = [
  { value: 'MANUAL', label: 'Manual' },
  { value: 'DEVICE', label: 'Instrumento' },
  { value: 'IMPORTED', label: 'Importado' },
];

export interface Measurement {
  id: string;
  stepId: string | null;
  kind: string;
  value: number;
  unit: string;
  temperatureC: number | null;
  method: string | null;
  source: string;
  recordedAt: string;
  recordedBy: string;
}

export interface RecordMeasurementRequest {
  stepId?: string | null;
  kind: string;
  value: number;
  unit: string;
  temperatureC?: number | null;
  method?: string | null;
  source: string;
}
