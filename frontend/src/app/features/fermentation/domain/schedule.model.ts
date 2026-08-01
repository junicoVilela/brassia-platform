export type ScheduleAction = 'RAMP' | 'REST' | 'DRY_HOP' | 'COLD_CRASH' | 'TRANSFER' | 'CONDITIONING';
export type ScheduleStepStatus = 'PLANNED' | 'DONE';

export const SCHEDULE_ACTIONS: ScheduleAction[] = [
  'RAMP', 'REST', 'DRY_HOP', 'COLD_CRASH', 'TRANSFER', 'CONDITIONING',
];

export const SCHEDULE_ACTION_LABELS: Record<ScheduleAction, string> = {
  RAMP: 'Rampa',
  REST: 'Descanso',
  DRY_HOP: 'Dry hop',
  COLD_CRASH: 'Cold crash',
  TRANSFER: 'Transferência',
  CONDITIONING: 'Acondicionamento',
};

export interface ScheduleStep {
  id: string;
  sequence: number;
  name: string;
  action: ScheduleAction;
  condition: 'TIME' | 'GRAVITY' | 'MANUAL';
  conditionDays: number | null;
  targetGravity: number | null;
  plannedStart: string;
  plannedEnd: string;
  toleranceHours: number;
  responsibleUserId: string;
  /** false = âncora com data própria; o recálculo para nela. */
  dependsOnPrevious: boolean;
  status: ScheduleStepStatus;
  executedAt: string | null;
  deviationHours: number;
  justification: string | null;
}

export interface FermentationSchedule {
  id: string;
  batchId: string;
  profileId: string;
  profileVersion: number;
  steps: ScheduleStep[];
}

export interface PlanScheduleRequest {
  profileId: string;
  start: string;
  responsibleUserId: string;
  defaultDurationDays: number | null;
  toleranceHours: number | null;
}

export interface AddStepRequest {
  name: string;
  action: ScheduleAction;
  condition: 'TIME' | 'GRAVITY' | 'MANUAL';
  conditionDays: number | null;
  targetGravity: number | null;
  plannedStart: string;
  plannedEnd: string;
  toleranceHours: number;
  responsibleUserId: string;
  dependsOnPrevious: boolean;
}

export interface RescheduleChange {
  stepId: string;
  sequence: number;
  name: string;
  fromStart: string;
  toStart: string;
  fromEnd: string;
  toEnd: string;
}

export interface RescheduleBlocked {
  stepId: string;
  sequence: number;
  name: string;
  reason: string;
}

/** Efeito calculado de mover uma data, antes de qualquer gravação. */
export interface ReschedulePreview {
  deltaHours: number;
  changes: RescheduleChange[];
  blocked: RescheduleBlocked[];
}
