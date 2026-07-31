export type AdvanceCondition = 'TIME' | 'GRAVITY' | 'MANUAL';
export const ADVANCE_CONDITIONS: AdvanceCondition[] = ['TIME', 'GRAVITY', 'MANUAL'];

export interface FermentationStage {
  sequence: number;
  name: string;
  targetTempC: number;
  rampHours: number | null;
  pressurePsi: number | null;
  condition: AdvanceCondition;
  conditionDays: number | null;
  targetGravity: number | null;
  requiresConfirmation: boolean;
}

/** Critério de estabilidade de FG (FER-003), congelado ao publicar a versão. */
export interface FgStabilityPolicy {
  windowHours: number;
  minReadings: number;
  toleranceSg: number;
}

export interface FermentationProfile {
  id: string;
  code: string;
  name: string;
  version: number;
  status: string;
  stages: FermentationStage[];
  stability: FgStabilityPolicy;
}

export interface CreateProfileRequest {
  code: string;
  name: string;
  stages: FermentationStage[];
  stability: FgStabilityPolicy | null;
}
