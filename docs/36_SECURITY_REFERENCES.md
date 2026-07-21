# Referências normativas e técnicas de segurança

Use fontes primárias como critério de aceite, sem copiar algoritmos. Revisado em 2026-07-16.

- NIST SP 800-63B — política de senha, autenticação, reautenticação e resistência a phishing: https://pages.nist.gov/800-63-4/sp800-63b.html
- W3C WebAuthn Level 3 — passkeys, challenge, RP ID, origem e credenciais públicas: https://www.w3.org/TR/webauthn-3/
- Spring Security Password Storage — encoders adaptativos e upgrade de hash: https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html
- Spring Session JDBC — sessão no servidor: https://docs.spring.io/spring-session/reference/
- Spring Security SAML 2.0 — login, logout e metadata de Relying Party: https://docs.spring.io/spring-security/reference/servlet/saml2/
- Spring Security OAuth2/OIDC Client — login federado por Authorization Code: https://docs.spring.io/spring-security/reference/servlet/oauth2/login/
- SCIM Core Schema RFC 7643: https://www.rfc-editor.org/rfc/rfc7643.html
- SCIM Protocol RFC 7644: https://www.rfc-editor.org/rfc/rfc7644.html
- Spring Security LDAP: https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/ldap.html
- OWASP Authentication Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- OWASP Authorization Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
- OWASP Session Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
- OWASP Multifactor Authentication Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html
- OWASP Forgot Password Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html
- OWASP Logging Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
- OWASP Secrets Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html

Em conflito entre conveniência e controle, documentar threat model, requisito operacional e ADR. Conformidade real depende de implementação, configuração, operação e testes; os documentos do kit não são certificação.
