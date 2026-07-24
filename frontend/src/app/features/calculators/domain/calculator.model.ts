export interface CalculatorSpec {
  id: string;
  name: string;
  inputs: string[];
  unit: string;
  description: string;
}

export interface CalculationResult {
  calculator: string;
  inputs: Record<string, number>;
  value: number;
  unit: string;
  method: string;
  version: string;
  assumptions: string[];
  alerts: string[];
}
