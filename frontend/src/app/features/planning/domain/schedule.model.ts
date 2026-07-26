export type ScheduleStatus = 'PLANNED';

export interface ScheduleEntry {
  id: string;
  recipeId: string;
  equipmentId: string;
  assignedUserId: string;
  plannedVolumeLiters: number;
  scheduledStart: string;
  scheduledEnd: string;
  status: ScheduleStatus;
}

export interface CreateScheduleEntryRequest {
  recipeId: string;
  equipmentId: string;
  assignedUserId: string;
  plannedVolumeLiters: number;
  scheduledStart: string;
  scheduledEnd: string;
}

export interface SimulateScheduleRequest {
  equipmentId: string;
  scheduledStart: string;
  scheduledEnd: string;
}

export interface ScheduleConflict {
  entryId: string;
  scheduledStart: string;
  scheduledEnd: string;
}

export interface SimulateScheduleResult {
  hasConflict: boolean;
  conflicts: ScheduleConflict[];
}

export interface CreatedScheduleEntry {
  id: string;
  status: string;
}
