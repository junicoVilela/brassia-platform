/** Grandezas que a produção mede e que uma carta de controle sabe ler (SPC-001). */
export type MeasurementKind = 'DENSITY' | 'TEMPERATURE' | 'VOLUME' | 'PH' | 'COLOR' | 'IBU';

export const KIND_LABELS: Record<MeasurementKind, string> = {
  DENSITY: 'Densidade',
  TEMPERATURE: 'Temperatura',
  VOLUME: 'Volume',
  PH: 'pH',
  COLOR: 'Cor',
  IBU: 'IBU',
};

export interface ControlPoint {
  batchId: string;
  value: number;
  measuredAt: string;
}

/**
 * Os limites que o processo **tem**.
 *
 * Nenhum campo aqui aceita número escolhido por alguém: todos saem do cálculo sobre a série. A tela repete
 * essa distinção em texto porque, no gráfico, uma linha horizontal parece igual à outra — e confundir
 * limite de controle com especificação é o erro que esta história existe para impedir.
 */
export interface ControlLimits {
  centerLine: number;
  lowerControlLimit: number;
  upperControlLimit: number;
  sigma: number;
  sampleSize: number;
}

export type SignalKind = 'BEYOND_LIMIT' | 'RUN_ON_ONE_SIDE' | 'TREND';

export interface ControlSignal {
  kind: SignalKind;
  description: string;
  firstIndex: number;
  length: number;
}

export interface ControlChart {
  kind: string;
  unit: string;
  points: ControlPoint[];
  controlLimits: ControlLimits;
  signals: ControlSignal[];
  inControl: boolean;
}

export interface AnalyzeChartRequest {
  recipeId: string;
  kind: MeasurementKind;
  batchIds: string[];
}

/**
 * O que cada sinal quer dizer, em português de quem opera.
 *
 * O texto diz **o que observar**, não só o nome da regra: "sequência" sozinho não informa ninguém, e a
 * leitura mais provável de um rótulo obscuro é ignorá-lo.
 */
export const SIGNAL_LABELS: Record<SignalKind, string> = {
  BEYOND_LIMIT: 'Ponto fora dos limites',
  RUN_ON_ONE_SIDE: 'Deslocamento de patamar',
  TREND: 'Tendência',
};

export const SIGNAL_HINTS: Record<SignalKind, string> = {
  BEYOND_LIMIT:
    'Um ponto além de 3σ. Tem ~0,3% de chance de ser acaso — algo mudou nesse lote.',
  RUN_ON_ONE_SIDE:
    'Sete ou mais pontos seguidos do mesmo lado do centro, mesmo todos dentro da faixa. ' +
    'O processo mudou de patamar e está estável no patamar novo.',
  TREND:
    'Sete ou mais pontos seguidos subindo ou descendo. É o aviso mais antecipado: ' +
    'descreve algo mudando agora — desgaste, saturação, sujeira acumulando.',
};
