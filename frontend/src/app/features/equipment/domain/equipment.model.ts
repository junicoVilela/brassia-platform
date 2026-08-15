/** Estado de limpeza (CLN-004-A). Nulo na resposta de edição: editar perfil não afirma nada sobre ele. */
export type Cleanliness = 'CLEAN' | 'DIRTY' | null;

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
  cleanliness: Cleanliness;
  /** Desde quando está sujo; nulo quando limpo. Separa "esvaziou hoje" de "parado há três semanas". */
  soiledSince: string | null;
}

export interface RegisterEquipmentRequest {
  code: string;
  name: string;
  capacityLiters: number;
  deadSpaceLiters: number;
  mashEfficiencyPercent: number;
  boilOffLitersPerHour: number;
}
