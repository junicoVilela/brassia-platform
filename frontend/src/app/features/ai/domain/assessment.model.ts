/**
 * A avaliação de risco de um lote (AIA-002).
 *
 * `facts` não é anexo: é o que permite conferir cada afirmação sem sair da tela. Uma avaliação sem os
 * números ao lado pediria confiança; com eles, pede leitura.
 */
export interface Assessment {
  usable: boolean;
  summary: string;
  risks: Risk[];
  assumptions: string[];
  facts: FactView[];
  /**
   * Afirmações descartadas por número inventado ou fato inexistente.
   *
   * Chega ao cliente de propósito — é o sinal de que o modelo tentou calcular em vez de interpretar.
   */
  discarded: string[];
}

/** `severity` é juízo do modelo, não medição. A tela precisa dizer isso. */
export interface Risk {
  statement: string;
  severity: Severity;
  factRefs: string[];
}

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH';

/**
 * Um número calculado pelo domínio.
 *
 * `source` nomeia o serviço que o calculou e viaja até aqui porque é o que torna verificável, por quem
 * lê, que o número não veio do modelo. `available` falso é ausência — e ausência não é zero.
 */
export interface FactView {
  id: string;
  label: string;
  value: number | null;
  unit: string;
  source: string;
  available: boolean;
}

export const SEVERITY_LABELS: Record<Severity, string> = {
  LOW: 'Baixo',
  MEDIUM: 'Médio',
  HIGH: 'Alto',
};

export const SEVERITY_BADGES: Record<Severity, string> = {
  LOW: 'bg-secondary',
  MEDIUM: 'bg-warning text-dark',
  HIGH: 'bg-danger',
};
