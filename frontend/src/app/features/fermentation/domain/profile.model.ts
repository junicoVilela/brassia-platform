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

export interface FermentationProfile {
  id: string;
  code: string;
  name: string;
  version: number;
  status: string;
  stages: FermentationStage[];
}

export interface CreateProfileRequest {
  code: string;
  name: string;
  stages: FermentationStage[];
}
