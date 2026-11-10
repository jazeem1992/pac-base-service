package com.allianz.pac.baseservice.architecture;

import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethod;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.allianz.pac.baseservice.exception.BaseException;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Coding and layering rules aligned with the Sonar "Sonar way" profile and the conventions of
 * {@code CLAUDE.md}. A violation fails {@code ./mvnw verify}.
 *
 * <p>Javadoc and source-level style rules are enforced by Checkstyle
 * ({@code config/checkstyle/checkstyle.xml}) because Javadoc is not visible in bytecode.
 *
 * <p>Holds only static rule fields evaluated by the ArchUnit JUnit engine (utility-class rule suppressed).
 */
@AnalyzeClasses(packages = "com.allianz.pac.baseservice", importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
class CodingRulesTest {

    private static final String CONTROLLER = "..controller..";
    private static final String SERVICE = "..service..";
    private static final String REPOSITORY = "..repository..";

    // ------------------------------------------------------------------ Sonar-aligned coding rules

    /** Sonar S6813: constructor injection only, no {@code @Autowired}/{@code @Inject} fields. */
    @ArchTest
    static final ArchRule NO_FIELD_INJECTION = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /** Sonar S106: no {@code System.out} / {@code System.err}; use the logger. */
    @ArchTest
    static final ArchRule NO_STANDARD_STREAMS = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    /** Sonar S112: never throw {@code Exception}, {@code RuntimeException} or {@code Throwable} directly. */
    @ArchTest
    static final ArchRule NO_GENERIC_EXCEPTIONS = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    /** Logging standard: SLF4J only, no {@code java.util.logging}. */
    @ArchTest
    static final ArchRule NO_JAVA_UTIL_LOGGING = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /** Sonar S1874: deprecated APIs must not be used. */
    @ArchTest
    static final ArchRule NO_DEPRECATED_API = GeneralCodingRules.DEPRECATED_API_SHOULD_NOT_BE_USED;

    /** Sonar S1148: never call {@code printStackTrace()}; log the exception instead. */
    @ArchTest
    static final ArchRule NO_PRINT_STACK_TRACE = noClasses()
            .should(callMethod(Throwable.class, "printStackTrace"))
            .because("exceptions must be logged through SLF4J (Sonar S1148)");

    // ------------------------------------------------------------------ Layering

    /** One-way layering: controller -> service -> repository. */
    @ArchTest
    static final ArchRule LAYERED_ARCHITECTURE = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy(CONTROLLER)
            .layer("Service").definedBy(SERVICE)
            .layer("Repository").definedBy(REPOSITORY)
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    /** Transactions belong to the service layer, never to controllers. */
    @ArchTest
    static final ArchRule NO_TRANSACTIONS_IN_CONTROLLERS = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().beMetaAnnotatedWith(Transactional.class)
            .orShould().beAnnotatedWith(Transactional.class)
            .because("transaction boundaries are defined in services");

    /** Transactions belong to the service layer, never to controller methods. */
    @ArchTest
    static final ArchRule NO_TRANSACTIONAL_CONTROLLER_METHODS = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage(CONTROLLER)
            .should().beMetaAnnotatedWith(Transactional.class)
            .orShould().beAnnotatedWith(Transactional.class)
            .because("transaction boundaries are defined in services");

    /** Outbound HTTP only through the client layer (resilience + error translation). */
    @ArchTest
    static final ArchRule WEB_CLIENT_ONLY_IN_CLIENTS = noClasses()
            .that().resideOutsideOfPackages("..client..", "..config..")
            .should().dependOnClassesThat().areAssignableTo(WebClient.class)
            .because("downstream calls must go through BaseWebClient subclasses");

    // ------------------------------------------------------------------ API conventions

    /** Every endpoint returns {@code ResponseEntity<ApiResponse<..>>} or a future of it. */
    @ArchTest
    static final ArchRule ENDPOINTS_RETURN_RESPONSE_ENTITY = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areAnnotatedWith(GetMapping.class)
            .or().areAnnotatedWith(PostMapping.class)
            .or().areAnnotatedWith(PutMapping.class)
            .or().areAnnotatedWith(PatchMapping.class)
            .or().areAnnotatedWith(DeleteMapping.class)
            .should().haveRawReturnType(ResponseEntity.class)
            .orShould().haveRawReturnType(CompletableFuture.class)
            .because("all responses use the ApiResponse envelope built by ApiResponseFactory");

    /** Application exceptions extend {@link BaseException} (error code + localized message). */
    @ArchTest
    static final ArchRule EXCEPTIONS_EXTEND_BASE_EXCEPTION = classes()
            .that().resideInAPackage("..exception..")
            .and().areAssignableTo(RuntimeException.class)
            .should().beAssignableTo(BaseException.class)
            .because("every error must map to an ErrorCode and the common response envelope");

    // ------------------------------------------------------------------ Naming

    /** REST controllers are named {@code *Controller} and live in the controller package. */
    @ArchTest
    static final ArchRule CONTROLLER_NAMING = classes()
            .that().areAnnotatedWith(RestController.class)
            .and().resideOutsideOfPackage("..exception..")
            .should().haveSimpleNameEndingWith("Controller")
            .andShould().resideInAPackage(CONTROLLER);

    /** Services are named {@code *ServiceImpl} and live in the service.impl package. */
    @ArchTest
    static final ArchRule SERVICE_NAMING = classes()
            .that().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("ServiceImpl")
            .andShould().resideInAPackage("..service.impl..");

    /** Repositories are named {@code *Repository} and live in the repository package. */
    @ArchTest
    static final ArchRule REPOSITORY_NAMING = classes()
            .that().areAnnotatedWith(Repository.class)
            .should().haveSimpleNameEndingWith("Repository")
            .andShould().resideInAPackage(REPOSITORY);
}
