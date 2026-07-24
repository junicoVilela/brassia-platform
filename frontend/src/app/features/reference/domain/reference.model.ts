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
