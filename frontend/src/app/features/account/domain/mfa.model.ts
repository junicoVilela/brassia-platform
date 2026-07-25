/** Resultado do enroll TOTP: segredo base32 e URI otpauth para o app autenticador. */
export interface TotpEnrollment {
  secret: string;
  otpauthUri: string;
}

export interface RecoveryCodes {
  codes: string[];
}
