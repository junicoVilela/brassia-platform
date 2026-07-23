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
