export const MATERIALS = ['INOX', 'ALUMINIO', 'PLASTICO', 'MADEIRA', 'VIDRO', 'BORRACHA'];
export const SOILING_LEVELS = ['LEVE', 'MODERADA', 'PESADA'];
export const RISK_LEVELS = ['BAIXO', 'MEDIO', 'ALTO'];

export interface CompatibilityRule {
  id: string;
  material: string;
  soiling: string;
  risk: string;
  previousProduct: string | null;
  procedureCode: string | null;
  method: string;
  alternative: string | null;
  restriction: string | null;
}

export interface CreateRuleRequest {
  material: string;
  soiling: string;
  risk: string;
  previousProduct?: string | null;
  procedureCode?: string | null;
  method: string;
  alternative?: string | null;
  restriction?: string | null;
}

export interface RecommendRequest {
  material: string;
  soiling: string;
  risk: string;
  previousProduct?: string | null;
}
