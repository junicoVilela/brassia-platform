# Estrutura de código backend

## Estrutura inicial

```text
backend/
├── .mvn/wrapper/
├── mvnw
├── pom.xml
└── src/
    ├── main/java/br/com/brew/brassia/
    │   ├── BrassiaApplication.java
    │   ├── shared/
    │   │   ├── security/
    │   │   ├── tenancy/
    │   │   ├── error/
    │   │   ├── observability/
    │   │   └── types/
    │   ├── recipe/
    │   ├── production/
    │   ├── inventory/
    │   └── sanitation/
    └── test/java/br/com/brew/brassia/
```

Módulos de negócio ficam diretamente abaixo do pacote raiz para o Spring Modulith identificá-los. Não criar `controller/`, `service/` e `repository/` globais.

## Módulo complexo — hexagonal

```text
recipe/
├── package-info.java               # declaração/documentação do módulo
├── RecipeLookup.java               # API publicada para outros módulos
├── RecipePublished.java            # evento público estável
├── domain/
│   ├── Recipe.java
│   ├── RecipeId.java
│   ├── RecipeStatus.java
│   └── RecipePolicy.java
├── application/
│   ├── port/inbound/CreateRecipe.java
│   ├── port/outbound/RecipeRepository.java
│   └── service/CreateRecipeHandler.java
├── adapter/
│   ├── inbound/web/RecipeController.java
│   └── outbound/persistence/
│       ├── RecipeJpaEntity.java
│       ├── SpringDataRecipeRepository.java
│       └── JpaRecipeRepositoryAdapter.java
└── config/RecipeConfiguration.java
```

O domínio não recebe anotações Spring/JPA. A fronteira transacional pertence ao caso de uso e pode ser aplicada por decorator/configuração, como no scaffold; nunca pelo controller. A entidade JPA é separada quando o modelo de persistência pressionaria o domínio; não duplicar modelo em CRUD simples.

## Módulo simples — camadas enxutas

```text
catalog/
├── package-info.java
├── CatalogApi.java
├── api/CatalogController.java
├── application/CatalogService.java
└── infrastructure/persistence/
    ├── IngredientEntity.java
    └── IngredientJpaRepository.java
```

Sem interface `Service` + `ServiceImpl`, sem `UseCase` para getter e sem mapper genérico. Se o módulo ganhar regras complexas, extraia domínio e portas por caso de uso, sem reescrever tudo.

## Convenções

- Pacotes e classes em inglês; textos de negócio e documentação podem permanecer em português.
- Construtor obrigatório; evitar field injection e setters públicos.
- Records para comandos/DTOs/valores imutáveis; `BigDecimal` e unidade explícita.
- Uma transação por caso de uso; sem transação no controller.
- Repository de outro módulo é proibido; usar API publicada ou evento.
- `shared` não contém regras cervejeiras nem classes genéricas como `Manager`/`Utils`.
- Lombok é opcional somente em adapters; domínio prefere Java explícito/records.
- MapStruct entra apenas quando mapeamentos repetitivos realmente reduzirem erro.
