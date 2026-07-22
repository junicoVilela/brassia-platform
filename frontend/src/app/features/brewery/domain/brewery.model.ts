export interface BrewerySummary {
  id: string;
  code: string;
  name: string;
  timezone: string;
}

export interface RegisterBreweryRequest {
  code: string;
  name: string;
  timezone: string;
}

export interface OperationalPreferences {
  breweryId: string;
  volumeUnit: string;
  massUnit: string;
  temperatureUnit: string;
  currencyCode: string;
  maxBatchVolume: number;
  allowNegativeStock: boolean;
  stockPolicy: string;
  version: number;
}

export type UpdatePreferencesRequest = Omit<OperationalPreferences, 'breweryId'>;
