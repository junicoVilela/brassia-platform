export interface BrewCorrection {
  id: string;
  name: string;
  inputs: string[];
  unit: string;
  description: string;
}

export interface CorrectionResult {
  calculator: string;
  value: number;
  unit: string;
  method: string;
  version: string;
  assumptions: string[];
  alerts: string[];
}

export interface PreviewCorrectionRequest {
  calculator: string;
  inputs: Record<string, number>;
}

export interface ApplyCorrectionRequest {
  calculator: string;
  inputs: Record<string, number>;
  sourceMeasurementId?: string | null;
  note?: string | null;
  realizedValue?: number | null;
}

export interface AppliedCorrection {
  id: string;
  calculator: string;
  sourceMeasurementId: string | null;
  note: string | null;
  inputs: Record<string, number>;
  plannedValue: number;
  plannedUnit: string;
  realizedValue: number | null;
  appliedAt: string;
}
