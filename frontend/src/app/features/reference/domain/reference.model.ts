export type SourceType =
  | 'OFFICIAL_STANDARD'
  | 'INTERCHANGE_STANDARD'
  | 'MANUFACTURER'
  | 'ACCOUNT_INTEGRATION'
  | 'MANUAL_CONTRIBUTION'
  | 'BRASSIA_CURATION';

export type PermissionStatus = 'UNKNOWN' | 'PENDING' | 'LIMITED_PERMISSION' | 'GRANTED' | 'DENIED';

export const SOURCE_TYPES: SourceType[] = [
  'OFFICIAL_STANDARD',
  'INTERCHANGE_STANDARD',
  'MANUFACTURER',
  'ACCOUNT_INTEGRATION',
  'MANUAL_CONTRIBUTION',
  'BRASSIA_CURATION',
];

export const PERMISSION_STATUSES: PermissionStatus[] = [
  'UNKNOWN',
  'PENDING',
  'LIMITED_PERMISSION',
  'GRANTED',
  'DENIED',
];

/** Permissões que autorizam publicação (espelha o gate do domínio). */
export function allowsPublish(status: PermissionStatus): boolean {
  return status === 'LIMITED_PERMISSION' || status === 'GRANTED';
}

export interface ReferenceSource {
  id: string;
  global: boolean;
  type: SourceType;
  name: string;
  owner: string;
  url: string | null;
  licenseName: string;
  permissionStatus: PermissionStatus;
  attribution: string | null;
}

export interface RegisterReferenceSourceRequest {
  type: SourceType;
  name: string;
  owner: string;
  url: string | null;
  licenseName: string;
  permissionStatus: PermissionStatus;
  attribution: string | null;
  reviewFrequency: string | null;
  responsible: string | null;
}

export interface ReferenceDataset {
  id: string;
  sourceId: string | null;
  version: string | null;
  checksum: string | null;
  status: string | null;
  reviewStatus: string | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  publishedAt: string | null;
  created: boolean | null;
}

export interface RecordReferenceDatasetRequest {
  datasetVersion: string;
  rawPayload: string;
  sourceSystem: string;
  sourceRecordId: string | null;
  sourceUrl: string | null;
  retrievedAt: string;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export type ImportJobStatus = 'RECEIVED' | 'VALIDATING' | 'REVIEW_REQUIRED' | 'PUBLISHED' | 'FAILED';

export interface ValidationIssue {
  line: number | null;
  field: string | null;
  code: string;
  message: string;
  severity: string;
}

export interface ImportJob {
  id: string;
  datasetVersion: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  status: string;
  publishedDatasetId: string | null;
  issues: ValidationIssue[];
}

export interface SubmitImportJobRequest {
  datasetVersion: string;
  contentType: string;
  rawPayload: string;
}

export type StyleAuthority =
  | 'BJCP_BEER'
  | 'BJCP_MEAD'
  | 'BJCP_CIDER'
  | 'BREWERS_ASSOCIATION'
  | 'INTERNAL';

export const STYLE_AUTHORITIES: StyleAuthority[] = [
  'BJCP_BEER',
  'BJCP_MEAD',
  'BJCP_CIDER',
  'BREWERS_ASSOCIATION',
  'INTERNAL',
];

export interface StyleRange {
  min: number | null;
  max: number | null;
  unit: string | null;
}

export interface StyleSet {
  id: string;
  global: boolean;
  authority: string;
  edition: string;
  language: string;
  permissionStatus: string;
  status: string;
  publishedAt: string | null;
}

export interface Style {
  code: string;
  name: string;
  family: string | null;
  category: string | null;
  og: StyleRange;
  fg: StyleRange;
  abv: StyleRange;
  ibu: StyleRange;
  color: StyleRange;
  generalImpression: string | null;
  hasDetailedProfile: boolean;
}

export interface StyleSetDetail {
  id: string;
  global: boolean;
  authority: string;
  edition: string;
  language: string;
  permissionStatus: string;
  status: string;
  styles: Style[];
}

export interface RangeCheck {
  metric: string;
  value: number;
  min: number | null;
  max: number | null;
  unit: string | null;
  withinRange: boolean;
}

export interface CompareStyleResult {
  styleCode: string;
  styleName: string;
  checks: RangeCheck[];
}

export interface CompareStyleRequest {
  og: number | null;
  fg: number | null;
  abv: number | null;
  ibu: number | null;
  colorEbc: number | null;
}

export interface CreateStyleSetRequest {
  sourceId: string;
  authority: StyleAuthority;
  edition: string;
  language: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  attribution: string | null;
  styles: {
    code: string;
    name: string;
    family: string | null;
    og: StyleRange;
    ibu: StyleRange;
    generalImpression: string | null;
    detailedProfile: string | null;
  }[];
}
