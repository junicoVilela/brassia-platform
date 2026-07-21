# Checklist de revisão

1. A mudança pertence ao módulo e à sprint corretos?
2. Alguma regra foi duplicada em controller, componente ou SQL?
3. Existe acesso direto a tabela/repositório de outro módulo?
4. `brewery_id`, permissão e ownership são verificados?
5. Concorrência, idempotência e retry foram considerados?
6. Quantidades, unidades, precisão, temperatura e fuso estão corretos?
7. O histórico planejado/realizado e a auditoria foram preservados?
8. Erros são seguros, localizáveis e seguem Problem Details RFC 9457?
9. Testes cobrem limite, falha, autorização e outra cervejaria?
10. A recomendação de IA é explicável e exige confirmação?
