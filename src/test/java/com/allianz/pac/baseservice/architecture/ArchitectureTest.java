package com.allianz.pac.baseservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture rules enforced on every build. A violation fails {@code ./mvnw verify}.
 *
 * <p>Main rule: API requests and responses are always DTOs - JPA entities never leave the
 * persistence/service layers.
 */
@AnalyzeClasses(packages = "com.allianz.pac.baseservice", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String MODEL = "..model..";
    private static final String CONTROLLER = "..controller..";
    private static final String DTO = "..dto..";

    /** Entities, represented as a predicate on parameter/return types. */
    private static final DescribedPredicate<JavaClass> ENTITY = DescribedPredicate.describe(
            "a JPA entity", javaClass -> javaClass.isAnnotatedWith(Entity.class));

    /** Parameter lists containing at least one entity. */
    private static final DescribedPredicate<List<JavaClass>> ANY_ENTITY_PARAMETER = DescribedPredicate.describe(
            "an entity parameter", parameters -> parameters.stream().anyMatch(ENTITY));

    /** Controllers must not use any class of the persistence model. */
    @ArchTest
    static final ArchRule controllersDoNotDependOnModel = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().dependOnClassesThat().resideInAPackage(MODEL)
            .because("API requests and responses must be DTOs, never entities");

    /** DTOs must not reference entities or other model classes. */
    @ArchTest
    static final ArchRule dtosDoNotDependOnModel = noClasses()
            .that().resideInAPackage(DTO)
            .should().dependOnClassesThat().resideInAPackage(MODEL)
            .because("DTOs are the API contract and must be independent of the persistence model");

    /** No REST endpoint may accept an entity as parameter. */
    @ArchTest
    static final ArchRule endpointsDoNotAcceptEntities = noMethods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().haveRawParameterTypes(ANY_ENTITY_PARAMETER)
            .because("request bodies must be DTOs");

    /** No REST endpoint may return an entity. */
    @ArchTest
    static final ArchRule endpointsDoNotReturnEntities = noMethods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().haveRawReturnType(ENTITY)
            .because("responses must be DTOs wrapped in ApiResponse");

    /** Entities are only used by the persistence, service and mapping layers. */
    @ArchTest
    static final ArchRule entitiesOnlyUsedInPersistenceLayers = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().onlyHaveDependentClassesThat()
            .resideInAnyPackage(MODEL, "..repository..", "..service..", "..mapper..")
            .because("entities must not leak into controllers, DTOs, clients or caches");

    /** Controllers talk to services only, never directly to repositories. */
    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("business logic and transactions belong to the service layer");
}
