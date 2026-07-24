export type WaterMethod = 'LAB' | 'TEST_STRIP' | 'ION_METER' | 'UTILITY';

export const WATER_METHODS: WaterMethod[] = ['LAB', 'TEST_STRIP', 'ION_METER', 'UTILITY'];

export interface WaterSource {
  id: string;
  code: string;
  name: string;
  active: boolean;
  version: number;
}

export interface WaterReport {
  id: string;
  sourceId: string;
  collectedOn: string;
  method: WaterMethod;
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
  notes: string | null;
}

export interface RegisterWaterSourceRequest {
  code: string;
  name: string;
}

export interface RecordWaterReportRequest {
  collectedOn: string;
  method: WaterMethod;
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
  notes?: string | null;
}

export interface ChargeBalance {
  cationsMeqL: number;
  anionsMeqL: number;
  differenceMeqL: number;
  percentDifference: number;
  withinTolerance: boolean;
}

export interface IonProfile {
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
}

export interface WaterReferenceProfile {
  id: string;
  global: boolean;
  name: string;
  region: string | null;
  edition: string;
  ions: IonProfile;
  alkalinity: number | null;
  hardness: number | null;
  ph: number | null;
  status: string;
  sourceName: string | null;
  chargeBalance: ChargeBalance;
}

export interface CreateWaterReferenceProfileRequest {
  name: string;
  region: string | null;
  edition: string;
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
  alkalinity: number | null;
  hardness: number | null;
  ph: number | null;
  sourceId: string | null;
  sourceName: string | null;
}
