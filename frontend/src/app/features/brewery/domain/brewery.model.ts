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
