export interface WaterProfile {
  id: string;
  code: string;
  name: string;
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
  active: boolean;
  version: number;
}

export interface RegisterWaterProfileRequest {
  code: string;
  name: string;
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
}

export interface BlendInput {
  sourceId: string;
  volumeLiters: number;
}

export interface SimulateBlendRequest {
  inputs: BlendInput[];
  targetProfileId?: string | null;
}

export interface BlendDeviation {
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
}

export interface BlendResult {
  method: string;
  totalVolumeLiters: number;
  calcium: number;
  magnesium: number;
  sodium: number;
  sulfate: number;
  chloride: number;
  bicarbonate: number;
  inputs: { sourceId: string; code: string; volumeLiters: number }[];
  target: { profileId: string; code: string; deviation: BlendDeviation } | null;
}
