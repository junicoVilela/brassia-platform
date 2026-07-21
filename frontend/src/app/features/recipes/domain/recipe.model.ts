export interface RecipeSummary {
  id: string;
  name: string;
  version: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  updatedAt: string;
}
