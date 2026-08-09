/** Onde o experimento está (EXP-001). */
export type ExperimentStatus = 'PLANNED' | 'RUNNING' | 'CONCLUDED' | 'ABANDONED';

export interface ExperimentFactor {
  name: string;
  controlValue: string;
  variantValue: string;
  differs: boolean;
}

/**
 * O que a conclusão **não** pode afirmar.
 *
 * Vem do servidor com descrição junto, e a tela mostra a descrição — não só o código. Um rótulo obscuro
 * como "SINGLE_PAIR" é lido como jargão e ignorado; a frase inteira é o que faz alguém pensar duas vezes
 * antes de mudar a receita por causa de um par de lotes.
 */
export interface ExperimentLimitation {
  code: string;
  description: string;
}

export interface ExperimentConclusion {
  supported: boolean;
  observation: string;
  concludedBy: string;
  concludedAt: string;
}

export interface Experiment {
  id: string;
  recipeId: string;
  hypothesis: string;
  controlBatchId: string;
  variantBatchId: string;
  isolatedVariable: ExperimentFactor;
  factors: ExperimentFactor[];
  plannedMeasurements: string[];
  sensoryPlanned: boolean;
  sensoryBlind: boolean;
  status: ExperimentStatus;
  /** Nunca vazio: um lote dividido é sempre n=1. */
  limitations: ExperimentLimitation[];
  conclusion: ExperimentConclusion | null;
  plannedBy: string;
  plannedAt: string;
}

export interface PlanExperimentRequest {
  recipeId: string;
  hypothesis: string;
  controlBatchId: string;
  variantBatchId: string;
  factors: { name: string; controlValue: string; variantValue: string }[];
  plannedMeasurements: string[];
  sensoryPlanned: boolean;
  sensoryBlind: boolean;
}

export interface ConcludeExperimentRequest {
  supported: boolean;
  observation: string;
}

export const STATUS_LABELS: Record<ExperimentStatus, string> = {
  PLANNED: 'Planejado',
  RUNNING: 'Em andamento',
  CONCLUDED: 'Concluído',
  ABANDONED: 'Abandonado',
};

export const STATUS_CLASSES: Record<ExperimentStatus, string> = {
  PLANNED: 'bg-secondary-subtle text-secondary-emphasis',
  RUNNING: 'bg-primary-subtle text-primary-emphasis',
  CONCLUDED: 'bg-success-subtle text-success-emphasis',
  ABANDONED: 'bg-warning-subtle text-warning-emphasis',
};
