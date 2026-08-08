/**
 * A resposta do copiloto (RAG-002).
 *
 * Os três blocos de texto são distintos e a interface não os junta: "o documento diz", "daí se conclui"
 * e "isto não está em fonte nenhuma" têm pesos diferentes para quem vai agir sobre a resposta.
 */
export interface GroundedAnswer {
  answered: boolean;
  answer: string;
  citations: Citation[];
  inferences: string[];
  limitations: string[];
  consultedSources: number;
  /**
   * Citações que o modelo alegou e não conferiram contra as fontes.
   *
   * Chega ao cliente de propósito: é informação sobre a confiabilidade daquela resposta. Esconder
   * deixaria uma resposta enfraquecida com a mesma aparência de uma resposta sólida.
   */
  discarded: string[];
}

/**
 * Uma citação conferida contra a fonte.
 *
 * Os metadados vêm da fonte, não da resposta do modelo. `effectiveOnDate` falso é aviso, não erro:
 * a citação vem de versão substituída, o que muda como ela deve ser lida.
 */
export interface Citation {
  documentCode: string;
  title: string;
  type: string;
  version: number;
  effectiveOnDate: boolean;
  ordinal: number;
  quote: string;
}

export interface AskRequest {
  question: string;
  onDate: string | null;
  equipmentId: string | null;
}
