package br.com.brew.brassia.foodsafety.adapter.outbound.persistence;

import br.com.brew.brassia.foodsafety.application.port.outbound.AllergenRepository;
import br.com.brew.brassia.foodsafety.domain.Allergen;
import br.com.brew.brassia.foodsafety.domain.AllergenCode;
import br.com.brew.brassia.foodsafety.domain.AllergenDeclaration;
import br.com.brew.brassia.foodsafety.domain.EquipmentDedication;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAllergenRepository implements AllergenRepository {

    private final JdbcClient jdbc;

    JdbcAllergenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Allergen> findAllergens(UUID breweryId) {
        return jdbc.sql("SELECT id, brewery_id, code, name FROM food_safety_allergen "
                        + "WHERE brewery_id = :brewery ORDER BY code")
                .param("brewery", breweryId).query(JdbcAllergenRepository::mapAllergen).list();
    }

    @Override
    public Optional<Allergen> findAllergen(UUID breweryId, AllergenCode code) {
        return jdbc.sql("SELECT id, brewery_id, code, name FROM food_safety_allergen "
                        + "WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code.value())
                .query(JdbcAllergenRepository::mapAllergen).optional();
    }

    @Override
    public void insertAllergen(Allergen allergen) {
        jdbc.sql("INSERT INTO food_safety_allergen (id, brewery_id, code, name) "
                        + "VALUES (:id, :brewery, :code, :name)")
                .param("id", allergen.id())
                .param("brewery", allergen.breweryId())
                .param("code", allergen.code().value())
                .param("name", allergen.name())
                .update();
    }

    @Override
    public Optional<AllergenDeclaration> findDeclaration(UUID breweryId, UUID ingredientId) {
        return findDeclarations(breweryId, List.of(ingredientId)).values().stream().findFirst();
    }

    @Override
    public Map<UUID, AllergenDeclaration> findDeclarations(UUID breweryId, Collection<UUID> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        var declared = jdbc.sql("""
                SELECT ingredient_id, declared_at, declared_by, version
                FROM food_safety_ingredient_declaration
                WHERE brewery_id = :brewery AND ingredient_id IN (:ids)
                """)
                .param("brewery", breweryId).param("ids", ingredientIds)
                .query((rs, rowNum) -> new Header(uuid(rs, "ingredient_id"),
                        rs.getTimestamp("declared_at").toInstant(), uuid(rs, "declared_by"),
                        rs.getLong("version")))
                .list();
        if (declared.isEmpty()) {
            return Map.of();
        }
        var codes = allergensByIngredient(breweryId, declared.stream().map(Header::ingredientId).toList());
        var result = new LinkedHashMap<UUID, AllergenDeclaration>();
        for (var header : declared) {
            result.put(header.ingredientId(), AllergenDeclaration.reconstitute(header.ingredientId(),
                    codes.getOrDefault(header.ingredientId(), Set.of()), header.declaredAt(),
                    header.declaredBy(), header.version()));
        }
        return result;
    }

    @Override
    public List<AllergenDeclaration> findAllDeclarations(UUID breweryId) {
        var headers = jdbc.sql("""
                SELECT ingredient_id, declared_at, declared_by, version
                FROM food_safety_ingredient_declaration WHERE brewery_id = :brewery
                """)
                .param("brewery", breweryId)
                .query((rs, rowNum) -> new Header(uuid(rs, "ingredient_id"),
                        rs.getTimestamp("declared_at").toInstant(), uuid(rs, "declared_by"),
                        rs.getLong("version")))
                .list();
        if (headers.isEmpty()) {
            return List.of();
        }
        var codes = allergensByIngredient(breweryId, headers.stream().map(Header::ingredientId).toList());
        return headers.stream()
                .map(header -> AllergenDeclaration.reconstitute(header.ingredientId(),
                        codes.getOrDefault(header.ingredientId(), Set.of()), header.declaredAt(),
                        header.declaredBy(), header.version()))
                .toList();
    }

    /**
     * Regrava a declaração inteira. Apagar os alergênicos antes de reinserir é o que faz "declarei
     * sem glúten" remover o glúten declarado ontem — acrescentar deixaria a matriz só crescer.
     */
    @Override
    public void saveDeclaration(UUID breweryId, AllergenDeclaration declaration, UUID actorId, Instant at) {
        jdbc.sql("""
                INSERT INTO food_safety_ingredient_declaration
                    (brewery_id, ingredient_id, declared_at, declared_by, version)
                VALUES (:brewery, :ingredient, :at, :actor, 0)
                ON CONFLICT (brewery_id, ingredient_id) DO UPDATE
                SET declared_at = EXCLUDED.declared_at, declared_by = EXCLUDED.declared_by,
                    version = food_safety_ingredient_declaration.version + 1
                """)
                .param("brewery", breweryId).param("ingredient", declaration.ingredientId())
                .param("at", java.sql.Timestamp.from(at)).param("actor", actorId)
                .update();
        jdbc.sql("DELETE FROM food_safety_ingredient_allergen "
                        + "WHERE brewery_id = :brewery AND ingredient_id = :ingredient")
                .param("brewery", breweryId).param("ingredient", declaration.ingredientId())
                .update();
        for (var code : declaration.allergens()) {
            jdbc.sql("INSERT INTO food_safety_ingredient_allergen (brewery_id, ingredient_id, allergen_code) "
                            + "VALUES (:brewery, :ingredient, :code)")
                    .param("brewery", breweryId).param("ingredient", declaration.ingredientId())
                    .param("code", code.value())
                    .update();
        }
    }

    @Override
    public Optional<EquipmentDedication> findDedication(UUID breweryId, UUID equipmentId) {
        var declared = jdbc.sql("SELECT 1 FROM food_safety_equipment_dedication "
                        + "WHERE brewery_id = :brewery AND equipment_id = :equipment")
                .param("brewery", breweryId).param("equipment", equipmentId)
                .query(Integer.class).optional();
        if (declared.isEmpty()) {
            return Optional.empty();
        }
        var codes = jdbc.sql("SELECT allergen_code FROM food_safety_equipment_allergen "
                        + "WHERE brewery_id = :brewery AND equipment_id = :equipment")
                .param("brewery", breweryId).param("equipment", equipmentId)
                .query(String.class).list();
        return Optional.of(EquipmentDedication.of(equipmentId, toCodes(codes)));
    }

    @Override
    public List<EquipmentDedication> findDedications(UUID breweryId) {
        var equipmentIds = jdbc.sql("SELECT equipment_id FROM food_safety_equipment_dedication "
                        + "WHERE brewery_id = :brewery ORDER BY equipment_id")
                .param("brewery", breweryId).query(UUID.class).list();
        return equipmentIds.stream()
                .map(equipmentId -> findDedication(breweryId, equipmentId).orElseThrow())
                .toList();
    }

    @Override
    public void saveDedication(UUID breweryId, EquipmentDedication dedication, UUID actorId, Instant at) {
        jdbc.sql("""
                INSERT INTO food_safety_equipment_dedication (brewery_id, equipment_id, declared_at, declared_by)
                VALUES (:brewery, :equipment, :at, :actor)
                ON CONFLICT (brewery_id, equipment_id) DO UPDATE
                SET declared_at = EXCLUDED.declared_at, declared_by = EXCLUDED.declared_by
                """)
                .param("brewery", breweryId).param("equipment", dedication.equipmentId())
                .param("at", java.sql.Timestamp.from(at)).param("actor", actorId)
                .update();
        jdbc.sql("DELETE FROM food_safety_equipment_allergen "
                        + "WHERE brewery_id = :brewery AND equipment_id = :equipment")
                .param("brewery", breweryId).param("equipment", dedication.equipmentId())
                .update();
        for (var code : dedication.allergens()) {
            jdbc.sql("INSERT INTO food_safety_equipment_allergen (brewery_id, equipment_id, allergen_code) "
                            + "VALUES (:brewery, :equipment, :code)")
                    .param("brewery", breweryId).param("equipment", dedication.equipmentId())
                    .param("code", code.value())
                    .update();
        }
    }

    @Override
    public void removeDedication(UUID breweryId, UUID equipmentId) {
        jdbc.sql("DELETE FROM food_safety_equipment_dedication "
                        + "WHERE brewery_id = :brewery AND equipment_id = :equipment")
                .param("brewery", breweryId).param("equipment", equipmentId)
                .update();
    }

    @Override
    public Set<AllergenCode> findProcedureEffectiveness(UUID breweryId, String procedureCode) {
        return toCodes(jdbc.sql("SELECT allergen_code FROM food_safety_procedure_allergen "
                        + "WHERE brewery_id = :brewery AND procedure_code = :procedure")
                .param("brewery", breweryId).param("procedure", procedureCode)
                .query(String.class).list());
    }

    @Override
    public Map<String, Set<AllergenCode>> findAllProcedureEffectiveness(UUID breweryId) {
        var result = new LinkedHashMap<String, Set<AllergenCode>>();
        jdbc.sql("SELECT procedure_code, allergen_code FROM food_safety_procedure_allergen "
                        + "WHERE brewery_id = :brewery ORDER BY procedure_code, allergen_code")
                .param("brewery", breweryId)
                .query((rs, rowNum) -> Map.entry(rs.getString("procedure_code"), rs.getString("allergen_code")))
                .list()
                .forEach(entry -> result.computeIfAbsent(entry.getKey(), key -> new TreeSet<>())
                        .add(AllergenCode.of(entry.getValue())));
        return result;
    }

    @Override
    public void saveProcedureEffectiveness(UUID breweryId, String procedureCode, Set<AllergenCode> allergens) {
        jdbc.sql("DELETE FROM food_safety_procedure_allergen "
                        + "WHERE brewery_id = :brewery AND procedure_code = :procedure")
                .param("brewery", breweryId).param("procedure", procedureCode)
                .update();
        for (var code : allergens) {
            jdbc.sql("INSERT INTO food_safety_procedure_allergen (brewery_id, procedure_code, allergen_code) "
                            + "VALUES (:brewery, :procedure, :code)")
                    .param("brewery", breweryId).param("procedure", procedureCode).param("code", code.value())
                    .update();
        }
    }

    private Map<UUID, Set<AllergenCode>> allergensByIngredient(UUID breweryId, Collection<UUID> ingredientIds) {
        var result = new LinkedHashMap<UUID, Set<AllergenCode>>();
        jdbc.sql("""
                SELECT ingredient_id, allergen_code FROM food_safety_ingredient_allergen
                WHERE brewery_id = :brewery AND ingredient_id IN (:ids)
                """)
                .param("brewery", breweryId).param("ids", ingredientIds)
                .query((rs, rowNum) -> Map.entry(uuid(rs, "ingredient_id"), rs.getString("allergen_code")))
                .list()
                .forEach(entry -> result.computeIfAbsent(entry.getKey(), key -> new TreeSet<>())
                        .add(AllergenCode.of(entry.getValue())));
        return result;
    }

    private static Set<AllergenCode> toCodes(List<String> values) {
        var codes = new TreeSet<AllergenCode>();
        values.forEach(value -> codes.add(AllergenCode.of(value)));
        return codes;
    }

    private static Allergen mapAllergen(ResultSet rs, int rowNum) throws SQLException {
        return new Allergen(uuid(rs, "id"), uuid(rs, "brewery_id"), AllergenCode.of(rs.getString("code")),
                rs.getString("name"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private record Header(UUID ingredientId, Instant declaredAt, UUID declaredBy, long version) {}
}
