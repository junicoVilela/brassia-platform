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
