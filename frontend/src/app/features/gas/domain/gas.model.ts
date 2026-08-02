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
