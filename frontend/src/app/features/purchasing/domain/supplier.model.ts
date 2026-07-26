export interface Supplier {
  id: string;
  name: string;
  code: string;
}

export interface RegisterSupplierRequest {
  name: string;
  code: string;
}
