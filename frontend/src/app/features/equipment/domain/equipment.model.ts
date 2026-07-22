export interface Equipment {
  id: string;
  code: string;
  name: string;
  capacityLiters: number;
  deadSpaceLiters: number;
  mashEfficiencyPercent: number;
  boilOffLitersPerHour: number;
  active: boolean;
  version: number;
}

export interface RegisterEquipmentRequest {
  code: string;
  name: string;
  capacityLiters: number;
  deadSpaceLiters: number;
  mashEfficiencyPercent: number;
  boilOffLitersPerHour: number;
}
