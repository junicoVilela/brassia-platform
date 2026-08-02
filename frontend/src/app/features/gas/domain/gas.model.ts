export type GasType = 'CO2' | 'N2' | 'MIX';

export type CylinderStatus = 'AVAILABLE' | 'CONNECTED' | 'EMPTY' | 'BLOCKED';

export const CYLINDER_STATUS_LABELS: Record<CylinderStatus, string> = {
  AVAILABLE: 'Disponível',
  CONNECTED: 'Conectado',
  EMPTY: 'Vazio',
  BLOCKED: 'Bloqueado',
};

export interface GasCylinder {
  id: string;
  code: string;
  gasType: GasType;
  capacityKg: number;
  tareKg: number;
  /** Massa restante: o gás não é medido por pressão (fase líquida engana o manômetro). */
  contentKg: number;
  requalificationDueOn: string;
  expired: boolean;
  status: CylinderStatus;
  /** Vem do backend pronto; a tela não reimplementa a regra de aptidão. */
  allocatable: boolean;
  blockReason: string | null;
  location: string;
}

export type ComponentKind = 'REGULATOR' | 'MANIFOLD';

export const COMPONENT_KIND_LABELS: Record<ComponentKind, string> = {
  REGULATOR: 'Regulador',
  MANIFOLD: 'Manifold',
};

export interface GasComponent {
  id: string;
  kind: ComponentKind;
  code: string;
  name: string;
  maxPressureBar: number;
  setPressureBar: number | null;
  active: boolean;
}

export type ConnectionStatus = 'PENDING_TEST' | 'SERVING' | 'BLOCKED' | 'DISCONNECTED';

export const CONNECTION_STATUS_LABELS: Record<ConnectionStatus, string> = {
  PENDING_TEST: 'Aguardando teste',
  SERVING: 'Servindo',
  BLOCKED: 'Bloqueada',
  DISCONNECTED: 'Desconectada',
};

export interface GasLeakTest {
  passed: boolean;
  method: string;
  pressureDropBar: number;
  note: string | null;
  testedAt: string;
}

export interface GasConnection {
  id: string;
  cylinderId: string;
  regulatorId: string;
  manifoldId: string | null;
  pointOfUseEquipmentId: string;
  workingPressureBar: number;
  /** Menor limite entre os componentes, congelado na montagem da linha. */
  networkMaxPressureBar: number;
  status: ConnectionStatus;
  connectedAt: string;
  leakTest: GasLeakTest | null;
  disconnectedAt: string | null;
  disconnectReason: string | null;
}

export interface PressureReading {
  id: string;
  bar: number;
  tempC: number | null;
  overPressure: boolean;
  at: string;
}

export interface GasConsumption {
  id: string;
  kg: number;
  reason: string | null;
  at: string;
}

export interface GasConnectionDetail {
  connection: GasConnection;
  pressureReadings: PressureReading[];
  consumption: GasConsumption[];
  consumedKg: number;
}

export interface PressureResult {
  readingId: string;
  overPressure: boolean;
  status: ConnectionStatus;
}

/** Motivo estável da recusa de conexão, como o backend o publica. */
export type GasBlockerCode =
  | 'cylinder_blocked'
  | 'cylinder_in_use'
  | 'cylinder_empty'
  | 'cylinder_expired'
  | 'regulator_unknown'
  | 'regulator_inactive'
  | 'manifold_unknown'
  | 'manifold_inactive'
  | 'point_of_use_occupied'
  | 'working_pressure_above_network';

export interface GasBlocker {
  code: GasBlockerCode;
  message: string;
}

export interface RegisterCylinderRequest {
  code: string;
  gasType: GasType;
  capacityKg: number;
  tareKg: number;
  contentKg: number;
  requalificationDueOn: string;
  location: string;
}

export interface ConnectGasRequest {
  cylinderId: string;
  regulatorId: string;
  manifoldId: string | null;
  pointOfUseEquipmentId: string;
  workingPressureBar: number;
}

/** Tubo do catálogo (GAS-002); os números vêm da ficha do fabricante. */
export interface GasTubing {
  id: string;
  material: string;
  internalDiameterMm: number;
  resistanceBarPerMeter: number;
  /** Vazão em que a resistência foi medida; base do escalonamento para outra vazão. */
  referenceFlowLpm: number;
}

export interface ServiceLine {
  id: string;
  code: string;
  name: string;
  pointOfUseEquipmentId: string;
  currentRevision: number;
  everApplied: boolean;
}

export interface ServiceLineRevision {
  revision: number;
  material: string;
  internalDiameterMm: number;
  appliedLengthMeters: number;
  recommendedLengthMeters: number;
  /** Montado − recomendado: o desvio é registrado, não corrigido. */
  lengthDeviationMeters: number;
  appliedPressureBar: number;
  elevationMeters: number;
  residualPressureBar: number;
  targetFlowLpm: number;
  servingTempC: number;
  targetCo2Volumes: number;
  calculationMethod: string;
  calculatorVersion: string;
  note: string | null;
  appliedBy: string;
  appliedAt: string;
}

export interface ServiceLineDetail {
  line: ServiceLine;
  revisions: ServiceLineRevision[];
}

export type LineWarningCode =
  | 'manual_adjustment_only'
  | 'no_balance_possible'
  | 'above_network_limit'
  | 'calculation_alert';

export interface LineWarning {
  code: LineWarningCode;
  message: string;
  safety: boolean;
}

/** Recomendação de balanceamento: método, limites e avisos de segurança. */
export interface LineBalance {
  appliedPressureBar: number;
  recommendedLengthMeters: number;
  hydrostaticBar: number;
  effectiveResistanceBarPerMeter: number;
  targetFlowLpm: number;
  servingTempC: number;
  targetCo2Volumes: number;
  material: string;
  internalDiameterMm: number;
  calculationMethod: string;
  calculatorVersion: string;
  feasible: boolean;
  warnings: LineWarning[];
}

export interface BalanceInput {
  targetCo2Volumes: number;
  servingTempC: number;
  elevationMeters: number;
  residualPressureBar: number;
  targetFlowLpm: number;
  resistanceId: string;
}

export interface ApplyRevisionRequest extends BalanceInput {
  appliedLengthMeters: number;
  note: string | null;
}
