export interface ProcedureStep {
  sequence: number;
  method: string;
  product: string | null;
  concentrationMinPct: number | null;
  concentrationMaxPct: number | null;
  tempMinC: number | null;
  tempMaxC: number | null;
  timeMinutes: number | null;
  flow: string | null;
  ppe: string | null;
  alternative: string | null;
  prohibition: string | null;
  evidenceRequired: boolean;
}

export interface Procedure {
  id: string;
  code: string;
  name: string;
  version: number;
  status: string;
  steps: ProcedureStep[];
}

export interface CreateProcedureRequest {
  code: string;
  name: string;
  steps: ProcedureStep[];
}
