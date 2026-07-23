export type MaintenanceKind = 'MAINTENANCE' | 'CALIBRATION';

export type MaintenanceStatus = 'SCHEDULED' | 'CANCELLED';

export interface Maintenance {
  id: string;
  equipmentId: string;
  kind: MaintenanceKind;
  instrument: string | null;
  startAt: string;
  endAt: string;
  notes: string | null;
  status: MaintenanceStatus;
  version: number;
}

export interface ScheduleMaintenanceRequest {
  kind: MaintenanceKind;
  instrument?: string | null;
  startAt: string;
  endAt: string;
  notes?: string | null;
}

/** Monta o request a partir do form, convertendo datetime-local para ISO-8601 (UTC). */
export function toScheduleMaintenanceRequest(value: {
  kind: MaintenanceKind;
  instrument: string;
  startAt: string;
  endAt: string;
  notes: string;
}): ScheduleMaintenanceRequest {
  return {
    kind: value.kind,
    instrument: value.instrument.trim() || null,
    startAt: new Date(value.startAt).toISOString(),
    endAt: new Date(value.endAt).toISOString(),
    notes: value.notes.trim() || null,
  };
}
