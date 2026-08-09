/** Grandeza reportada por um dispositivo (INT-001). */
export type Measure = 'DENSITY' | 'TEMPERATURE' | 'PRESSURE' | 'FLOW';

export type DeviceStatus = 'ACTIVE' | 'PAUSED' | 'REVOKED';

/**
 * De que jeito o dispositivo fala (INT-006).
 *
 * É atributo do cadastro e não da mensagem: deixar o payload declarar o próprio formato seria confiar num
 * campo que o firmware preenche, e um firmware atualizado passaria a ser interpretado de outro jeito sem
 * que ninguém decidisse isso.
 */
export type PayloadFormat = 'CANONICAL' | 'ISPINDEL' | 'TILT';

export const PAYLOAD_FORMAT_LABELS: Record<PayloadFormat, string> = {
  CANONICAL: 'Formato BrassIA (canônico)',
  ISPINDEL: 'iSpindel',
  TILT: 'Tilt',
};

/**
 * Qualidade de uma leitura.
 *
 * Nenhum destes valores significa "recusada": a leitura ruim foi gravada e marcada. Recusar deixaria um
 * buraco na curva, e um buraco é indistinguível de "o sensor não mediu".
 */
export type ReadingQuality = 'GOOD' | 'OUT_OF_RANGE' | 'FUTURE_CLOCK';

export interface SensorDevice {
  id: string;
  code: string;
  name: string;
  measure: Measure;
  unit: string;
  equipmentId: string | null;
  expectedIntervalSeconds: number | null;
  payloadFormat: PayloadFormat;
  status: DeviceStatus;
  registeredAt: string;
  version: number;
}

export interface SensorReading {
  id: string;
  deviceId: string;
  messageId: string;
  measure: Measure;
  value: number;
  unit: string;
  /** Relógio do dispositivo: quando aconteceu. */
  measuredAt: string;
  /** Nosso relógio: quando ficamos sabendo. */
  receivedAt: string;
  quality: ReadingQuality;
  qualityReason: string | null;
  delaySeconds: number;
  late: boolean;
}

export interface RegisterDeviceRequest {
  code: string;
  name: string;
  measure: Measure;
  unit: string;
  equipmentId: string | null;
  expectedIntervalSeconds: number | null;
  payloadFormat: PayloadFormat;
}

export const MEASURE_LABELS: Record<Measure, string> = {
  DENSITY: 'Densidade',
  TEMPERATURE: 'Temperatura',
  PRESSURE: 'Pressão',
  FLOW: 'Vazão',
};

export const MEASURE_ICONS: Record<Measure, string> = {
  DENSITY: 'ri-drop-line',
  TEMPERATURE: 'ri-temp-hot-line',
  PRESSURE: 'ri-dashboard-3-line',
  FLOW: 'ri-water-flash-line',
};

/** Unidades aceitas por grandeza — espelham a faixa de plausibilidade do domínio. */
export const MEASURE_UNITS: Record<Measure, string[]> = {
  DENSITY: ['SG', 'PLATO'],
  TEMPERATURE: ['C', 'F'],
  PRESSURE: ['PSI', 'BAR'],
  FLOW: ['L_MIN', 'HL_H'],
};

export const UNIT_LABELS: Record<string, string> = {
  SG: 'SG',
  PLATO: '°P',
  C: '°C',
  F: '°F',
  PSI: 'psi',
  BAR: 'bar',
  L_MIN: 'L/min',
  HL_H: 'hL/h',
};

export const STATUS_LABELS: Record<DeviceStatus, string> = {
  ACTIVE: 'Ativo',
  PAUSED: 'Pausado',
  REVOKED: 'Revogado',
};

export const QUALITY_LABELS: Record<ReadingQuality, string> = {
  GOOD: 'Boa',
  OUT_OF_RANGE: 'Fora da faixa',
  FUTURE_CLOCK: 'Relógio adiantado',
};
