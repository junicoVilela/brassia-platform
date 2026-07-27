export interface Supplier {
  id: string;
  name: string;
  code: string;
  leadTimeDays: number | null;
}

export interface RegisterSupplierRequest {
  name: string;
  code: string;
  leadTimeDays?: number | null;
}
