package tr.gov.kurum.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameStartingWith;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.PersistedAggregateRoot;

/**
 * The rules of DDD_HEXAGONAL_ARCHITECTURE_RULES.md §9, run on every build.
 *
 * <p>Some rules of the document cannot be used as written; each adaptation is marked "ADAPTED" with the
 * reason. The intent of every rule is kept. Rules marked "ADDED" enforce points of the document or of the
 * coding rules that §9 does not check.</p>
 */
@AnalyzeClasses(packages = "tr.gov.kurum", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "tr.gov.kurum.";
    private static final String SHARED_KERNEL = "common";
    /** Published language: integration events only (see package-info of tr.gov.kurum.contracts). */
    private static final String CONTRACTS = "contracts";

    // The project's package layout (§1): infrastructure/adapter/in|out/<kind>. The document's own ArchUnit
    // patterns use "..infrastructure.web.." and "..infrastructure.persistence..", which match nothing in
    // that layout; the patterns below are the ones that match it (ADAPTED).
    private static final String WEB = "..infrastructure.adapter.in.web..";
    private static final String PERSISTENCE = "..infrastructure.adapter.out.persistence..";

    // ------------------------------------------------------------------------- 9.1 Layer purity & inversion

    /** Rule 1. */
    @ArchTest
    static void domain_layer_must_be_strictly_pure(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..", "jakarta.validation..", "javax.validation..",
                "com.fasterxml.jackson..", "tools.jackson..", "org.hibernate..", "lombok..")
            .because("the domain is pure business logic without framework dependencies (rule 2.1)")
            .check(classes);
    }

    /** Rule 2. */
    @ArchTest
    static void domain_must_not_depend_on_application_or_infrastructure(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .because("the domain is the centre of the hexagon")
            .check(classes);
    }

    /** Rule 3. */
    @ArchTest
    static void application_must_not_depend_on_infrastructure_or_web(JavaClasses classes) {
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..common.web..",
                "jakarta.servlet..", "org.springframework.web..", "jakarta.persistence..")
            .because("use cases orchestrate; web and database details stay in adapters")
            .check(classes);
    }

    // ----------------------------------------------------------------------- 9.2 Port purity & signatures

    /**
     * Rule 4, ADAPTED: rule 1 of the same document puts Command/Query DTOs into {@code port/in}, so "every
     * class in a port package is an interface" contradicts it. Records (commands, queries, views) and enums
     * are allowed; anything else must be an interface.
     */
    @ArchTest
    static void ports_must_be_interfaces(JavaClasses classes) {
        classes().that().resideInAPackage("..application.port..")
            .and().areNotRecords()
            .and().areNotEnums()
            .should().beInterfaces()
            .because("ports are contracts; only interfaces and immutable DTO records live next to them")
            .check(classes);
    }

    /** Rule 5, extended to every type involved (generics too) and to web/servlet/JPA types. */
    @ArchTest
    static void ports_must_not_expose_infrastructure_types(JavaClasses classes) {
        codeUnits().that().areDeclaredInClassesThat().resideInAPackage("..application.port..")
            .should(notInvolveTypesFrom("..infrastructure..", "jakarta.servlet..", "org.springframework.web..",
                "org.springframework.http..", "jakarta.persistence.."))
            .because("JPA entities, web DTOs or servlet types in a port signature break the port/adapter boundary")
            .check(classes);
    }

    // ---------------------------------------------------------------- 9.3 Encapsulation & rich domain model

    /** Rule 6. */
    @ArchTest
    static void domain_models_must_not_have_public_setters(JavaClasses classes) {
        methods().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
            .and().haveNameStartingWith("set")
            .should().notBePublic()
            .because("state changes through intention-revealing methods, not setters (rule 2.3)")
            .allowEmptyShould(true)   // the domain has no setter at all, which satisfies the rule
            .check(classes);
    }

    /**
     * Rule 7, ADAPTED: enum constants and other constants are {@code public static final} fields; the rule is
     * about instance state, so static fields are excluded.
     */
    @ArchTest
    static void domain_fields_must_be_private(JavaClasses classes) {
        fields().that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
            .and().areNotStatic()
            .should().bePrivate()
            .because("domain state must not be manipulated from outside")
            .check(classes);
    }

    // ----------------------------------------------------------------- 9.4 Bounded context isolation

    /**
     * Rule 8, ADAPTED: the document's pattern {@code tr.gov.kurum.(#)...} is not ArchUnit syntax (there is no
     * "another module" placeholder). The same check as a condition: no dependency on another module's port/in.
     */
    @ArchTest
    static void cross_module_port_in_dependency_is_forbidden(JavaClasses classes) {
        classes().that().resideInAPackage("..application.service..")
            .should(notDependOnAnotherModulesLayer(".application.port.in"))
            .because("modules talk through their own outbound ports, never another module's use case (ADR-023)")
            .check(classes);
    }

    /** Rule 9, ADAPTED like rule 8. The shared kernel ({@code common}) is allowed by definition (rule 5.3). */
    @ArchTest
    static void cross_module_domain_model_import_is_forbidden(JavaClasses classes) {
        classes().that().resideInAPackage("..domain..")
            .should(notDependOnAnotherModulesLayer(".domain"))
            .because("bounded contexts define their own models or use the shared kernel (rule 5.3)")
            .check(classes);
    }

    /**
     * ADDED: the strongest form of rules 8, 9 and 11. A module sees itself and the shared kernel, plus exactly
     * two sanctioned paths, both only from adapters:
     * <ul>
     *   <li>an outbound adapter may call another module's {@code application.port.in} — rule 5.1's "correct"
     *       way: the caller's service sees only its own outbound port;</li>
     *   <li>an adapter may use the published language ({@code tr.gov.kurum.contracts}).</li>
     * </ul>
     */
    @ArchTest
    static void modules_depend_only_on_themselves_the_shared_kernel_and_sanctioned_paths(JavaClasses classes) {
        classes().that().resideInAPackage("tr.gov.kurum.(*)..")
            .and().resideOutsideOfPackage(ROOT + CONTRACTS + "..")
            .should(notDependOnAnotherModuleExceptSanctionedPaths())
            .because("bounded contexts must not know each other's code (section 5)")
            .check(classes);
    }

    /** ADDED: the published language depends on nothing but the JDK and the shared kernel's domain types. */
    @ArchTest
    static void contracts_are_records_of_jdk_and_shared_kernel_types(JavaClasses classes) {
        classes().that().resideInAPackage(ROOT + CONTRACTS + "..")
            .and().doNotHaveSimpleName("package-info")
            .should().beRecords()
            .andShould().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + SHARED_KERNEL + ".domain..")
            .because("an integration event must be readable without the producer's model and survive JSON")
            .check(classes);
    }

    /** ADDED: domain and application code never see the published language; adapters translate (ACL). */
    @ArchTest
    static void only_adapters_use_the_published_language(JavaClasses classes) {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + CONTRACTS + "..")
            .because("a module's model stays its own; translation into the published language is an adapter's job")
            .check(classes);
    }

    /** Rule 10. */
    @ArchTest
    static void modules_must_be_free_of_cycles(JavaClasses classes) {
        slices().matching("tr.gov.kurum.(*)..")
            .should().beFreeOfCycles()
            .because("cyclic module dependencies lock the system into a monolith")
            .check(classes);
    }

    /** Rule 11 (5.2): relation fields must point into the declaring class's own module. */
    @ArchTest
    static void cross_module_jpa_relation_is_forbidden(JavaClasses classes) {
        fields().that().areAnnotatedWith("jakarta.persistence.ManyToOne")
            .or().areAnnotatedWith("jakarta.persistence.OneToMany")
            .or().areAnnotatedWith("jakarta.persistence.ManyToMany")
            .or().areAnnotatedWith("jakarta.persistence.OneToOne")
            .should(referenceOnlyEntitiesOfOwnModule())
            .because("rule 5.2: across modules only scalar ids are stored")
            .check(classes);
    }

    // ------------------------------------------------------------------ 9.5 Driving & driven adapters

    /** Rule 11 (web), ADAPTED like rule 8 (and with the project's web package pattern). */
    @ArchTest
    static void controllers_must_only_depend_on_own_inbound_ports(JavaClasses classes) {
        classes().that().resideInAPackage(WEB)
            .should(notDependOnAnotherModulesLayer(".application.port.in"))
            .because("a controller delegates to its own bounded context only (rule 7.3)")
            .check(classes);
    }

    /** Rule 12. */
    @ArchTest
    static void controllers_must_not_call_domain_services(JavaClasses classes) {
        noClasses().that().resideInAPackage(WEB)
            .should().dependOnClassesThat().resideInAPackage("..domain.service..")
            .because("requests reach the domain through use cases (port/in)")
            .check(classes);
    }

    /** Rule 13, extended to generic type arguments ({@code List<DomainType>} is a leak as well). */
    @ArchTest
    static void controllers_must_not_return_domain_models(JavaClasses classes) {
        methods().that().areDeclaredInClassesThat()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should(notReturnTypesFrom("..domain.model.."))
            .because("REST controllers return DTOs, never domain objects (rule 7.4)")
            .check(classes);
    }

    /** Rule 14. */
    @ArchTest
    static void generic_config_package_is_forbidden(JavaClasses classes) {
        noClasses().should().resideInAPackage("..infrastructure.config..")
            .because("configuration lives in the adapter package it configures (ADR-012)")
            .check(classes);
    }

    /** Rule 15, with the project's persistence package pattern (ADAPTED). */
    @ArchTest
    static void jpa_entities_must_reside_in_persistence_and_extend_base(JavaClasses classes) {
        classes().that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage(PERSISTENCE)
            .andShould().beAssignableTo(BaseJpaEntity.class)
            .because("entities are infrastructure and share the audit and version columns")
            .check(classes);
    }

    /**
     * Rule 16. "Aggregate root" is not visible in bytecode, so root entities carry the marker
     * {@link PersistedAggregateRoot}; a repository's entity type must have it.
     */
    @ArchTest
    static void repositories_must_only_target_aggregate_roots(JavaClasses classes) {
        classes().that().areAssignableTo("org.springframework.data.repository.Repository").and().areInterfaces()
            .should(manageOnlyAggregateRootEntities())
            .because("rule 5.4: child entities change only through their aggregate root")
            .check(classes);
    }

    /** ADDED (rule 4.1): Spring Data repositories must not leak out of the persistence package. */
    @ArchTest
    static void spring_data_repositories_are_package_private(JavaClasses classes) {
        classes().that().areAssignableTo("org.springframework.data.repository.Repository").and().areInterfaces()
            .should().notBePublic()
            .andShould().resideInAPackage(PERSISTENCE)
            .because("only the persistence adapter may use them (rule 4.1)")
            .check(classes);
    }

    /** ADDED: entities stay inside their persistence package as well. */
    @ArchTest
    static void jpa_entities_are_package_private(JavaClasses classes) {
        classes().that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().notBePublic()
            .because("an entity must never reach a controller or a use case")
            .check(classes);
    }

    // ------------------------------------------------------------------------------- 9.6 Coding standards

    /** Rule 17. */
    @ArchTest
    static void field_injection_is_strictly_forbidden(JavaClasses classes) {
        noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .orShould().beAnnotatedWith("org.springframework.beans.factory.annotation.Value")
            .because("dependencies are visible constructor parameters")
            .check(classes);
    }

    /** Rule 18, also for class-level annotations (the document checks methods only). */
    @ArchTest
    static void transactional_must_only_be_in_application_or_persistence(JavaClasses classes) {
        noMethods().that().areDeclaredInClassesThat().resideInAnyPackage(WEB, "..domain..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .orShould().beAnnotatedWith("jakarta.transaction.Transactional")
            .check(classes);
        noClasses().that().resideInAnyPackage(WEB, "..domain..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .orShould().beAnnotatedWith("jakarta.transaction.Transactional")
            .because("transaction boundaries belong to the application layer")
            .check(classes);
    }

    /**
     * Rule 19 as written. Note: {@code lombok.Data} has SOURCE retention, so it never reaches the bytecode and
     * this rule can never fail. The checks that actually guard against what @Data breaks are
     * {@link #jpa_relations_are_lazy_and_never_cascade_all} and {@link #jpa_entities_are_package_private}.
     */
    @ArchTest
    static void jpa_entities_must_never_use_lombok_data(JavaClasses classes) {
        noClasses().that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().beAnnotatedWith("lombok.Data")
            .allowEmptyShould(true)
            .check(classes);
    }

    /** Rule 20. */
    @ArchTest
    static void application_services_must_not_contain_business_logic(JavaClasses classes) {
        noClasses().that().resideInAPackage("..application.service..")
            .should().callMethodWhere(target(nameStartingWith("set"))
                .and(target(owner(resideInAPackage("..domain.model..")))))
            .because("rule 3.1: state transitions happen inside the domain model")
            .check(classes);
    }

    /**
     * Rule 21, ADAPTED: {@code resideInAnyPackage("java.util.Date")} treats a class name as a package and never
     * matches anything. The class names are compared instead. The JWT adapter is the single exception: jjwt's
     * API is built on {@code Date}; it converts to {@code Instant} right at that boundary.
     */
    @ArchTest
    static void legacy_date_and_calendar_are_forbidden(JavaClasses classes) {
        noClasses().that().doNotHaveFullyQualifiedName(
                "tr.gov.kurum.identity.infrastructure.adapter.out.token.JwtAccessTokenService")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.GregorianCalendar")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.sql.Timestamp")
            .because("java.time types are used everywhere")
            .check(classes);
    }

    /** Rule 22. */
    @ArchTest
    static void standard_streams_and_print_stack_trace_are_forbidden(JavaClasses classes) {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("logging goes through SLF4J")
            .check(classes);
    }

    // ---------------------------------------------------------------------- ADDED: coding rules (PDF)

    /** Rule 1.24 of the coding rules: Optional is a return type only. */
    @ArchTest
    static void optional_is_never_a_field_or_parameter(JavaClasses classes) {
        noFields().should().haveRawType(Optional.class)
            .because("Optional is meant for return values (coding rule 1.24)")
            .check(classes);
        codeUnits().that().areDeclaredInClassesThat().resideInAPackage("tr.gov.kurum..")
            .should(notHaveOptionalParameters())
            .because("Optional is meant for return values (coding rule 1.24)")
            .check(classes);
    }

    /** Rule 1.9 of the coding rules: no EAGER loading, no CascadeType.ALL. */
    @ArchTest
    static void jpa_relations_are_lazy_and_never_cascade_all(JavaClasses classes) {
        fields().that().areDeclaredInClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .should(haveLazyRelationsWithoutCascadeAll())
            .because("EAGER loading and CascadeType.ALL are forbidden (coding rule 1.9)")
            .check(classes);
    }

    /** Rule 1.17 of the coding rules: enums are stored as text (ORDINAL is the JPA default). */
    @ArchTest
    static void enums_are_persisted_as_strings(JavaClasses classes) {
        fields().that().areDeclaredInClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .should(storeEnumsAsStrings())
            .because("an ordinal changes meaning when the enum is reordered (coding rule 1.17)")
            .check(classes);
    }

    // =========================================================================================== helpers

    private static String moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT)) {
            return null;
        }
        String rest = packageName.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /**
     * Fails for a dependency on another module (not the shared kernel) whose package contains
     * {@code layerFragment} ({@code ""} = any package of another module).
     */
    private static ArchCondition<JavaClass> notDependOnAnotherModulesLayer(String layerFragment) {
        String description = layerFragment.isEmpty()
            ? "not depend on another module (except the shared kernel)"
            : "not depend on " + layerFragment + " of another module";
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownModule = moduleOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String targetModule = moduleOf(target);
                    if (targetModule == null || targetModule.equals(ownModule) || SHARED_KERNEL.equals(targetModule)
                            || !target.getPackageName().contains(layerFragment)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notDependOnAnotherModuleExceptSanctionedPaths() {
        return new ArchCondition<>("not depend on another module, except through sanctioned adapter paths") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownModule = moduleOf(item);
                boolean adapter = item.getPackageName().contains(".infrastructure.adapter.");
                boolean outboundAdapter = item.getPackageName().contains(".infrastructure.adapter.out.");
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String targetModule = moduleOf(target);
                    if (targetModule == null || targetModule.equals(ownModule) || SHARED_KERNEL.equals(targetModule)) {
                        continue;
                    }
                    boolean publishedLanguage = adapter && CONTRACTS.equals(targetModule);
                    boolean otherModulesInboundPort = outboundAdapter
                        && target.getPackageName().contains(".application.port.in");
                    if (!publishedLanguage && !otherModulesInboundPort) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaCodeUnit> notInvolveTypesFrom(String... packages) {
        return new ArchCondition<>("not involve types from " + Arrays.toString(packages)) {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                List<JavaType> types = new java.util.ArrayList<>(codeUnit.getParameterTypes());
                types.add(codeUnit.getReturnType());
                for (JavaType type : types) {
                    for (JavaClass involved : type.getAllInvolvedRawTypes()) {
                        if (resideInAnyPackage(packages).test(involved)) {
                            events.add(SimpleConditionEvent.violated(codeUnit,
                                codeUnit.getFullName() + " uses " + involved.getName()));
                        }
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notReturnTypesFrom(String packageIdentifier) {
        return new ArchCondition<>("not return types from " + packageIdentifier) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaClass involved : method.getReturnType().getAllInvolvedRawTypes()) {
                    if (resideInAPackage(packageIdentifier).test(involved) && !involved.isEnum()) {
                        events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " returns " + involved.getName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaField> referenceOnlyEntitiesOfOwnModule() {
        return new ArchCondition<>("reference only entities of the own module") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String ownModule = moduleOf(field.getOwner());
                for (JavaClass involved : field.getType().getAllInvolvedRawTypes()) {
                    if (involved.isAnnotatedWith("jakarta.persistence.Entity")
                            && !ownModule.equals(moduleOf(involved))) {
                        events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " references " + involved.getName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> manageOnlyAggregateRootEntities() {
        return new ArchCondition<>("manage only entities marked @PersistedAggregateRoot") {
            @Override
            public void check(JavaClass repository, ConditionEvents events) {
                for (JavaType superInterface : repository.getInterfaces()) {
                    if (!(superInterface instanceof JavaParameterizedType parameterized)
                            || parameterized.getActualTypeArguments().isEmpty()) {
                        continue;
                    }
                    JavaClass entity = parameterized.getActualTypeArguments().get(0).toErasure();
                    if (!entity.isAnnotatedWith(PersistedAggregateRoot.class)) {
                        events.add(SimpleConditionEvent.violated(repository,
                            repository.getName() + " manages " + entity.getName()
                                + ", which is not an aggregate root"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaCodeUnit> notHaveOptionalParameters() {
        return new ArchCondition<>("not have Optional parameters") {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                // Compiler-generated lambda bodies may take an Optional captured from the enclosing method.
                if (codeUnit.getName().startsWith("lambda$")) {
                    return;
                }
                for (JavaClass parameter : codeUnit.getRawParameterTypes()) {
                    if (parameter.isEquivalentTo(Optional.class)) {
                        events.add(SimpleConditionEvent.violated(codeUnit,
                            codeUnit.getFullName() + " has an Optional parameter"));
                    }
                }
            }
        };
    }

    private static final Set<String> TO_ONE = Set.of("jakarta.persistence.ManyToOne", "jakarta.persistence.OneToOne");
    private static final Set<String> RELATIONS = Set.of("jakarta.persistence.ManyToOne",
        "jakarta.persistence.OneToOne", "jakarta.persistence.OneToMany", "jakarta.persistence.ManyToMany",
        "jakarta.persistence.ElementCollection");

    private static ArchCondition<JavaField> haveLazyRelationsWithoutCascadeAll() {
        return new ArchCondition<>("load relations lazily and never cascade ALL") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                for (JavaAnnotation<JavaField> annotation : field.getAnnotations()) {
                    String type = annotation.getRawType().getName();
                    if (!RELATIONS.contains(type)) {
                        continue;
                    }
                    String fetch = enumName(annotation.get("fetch").orElse(null));
                    // To-one relations default to EAGER: LAZY must be written explicitly.
                    if ("EAGER".equals(fetch) || (TO_ONE.contains(type) && !"LAZY".equals(fetch))) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName() + " is not LAZY"));
                    }
                    Object cascade = annotation.get("cascade").orElse(null);
                    if (cascade instanceof Object[] values
                            && Arrays.stream(values).map(ArchitectureTest::enumName).anyMatch("ALL"::equals)) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName() + " cascades ALL"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaField> storeEnumsAsStrings() {
        return new ArchCondition<>("store enum values as STRING") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                if (!field.getRawType().isEnum() || field.getModifiers().contains(JavaModifier.STATIC)) {
                    return;
                }
                String value = field.tryGetAnnotationOfType("jakarta.persistence.Enumerated")
                    .flatMap(annotation -> annotation.get("value"))
                    .map(ArchitectureTest::enumName)
                    .orElse("ORDINAL");
                if (!"STRING".equals(value)) {
                    events.add(SimpleConditionEvent.violated(field,
                        field.getFullName() + " is not @Enumerated(EnumType.STRING)"));
                }
            }
        };
    }

    private static String enumName(Object value) {
        if (value instanceof JavaEnumConstant constant) {
            return constant.name();
        }
        return value == null ? null : String.valueOf(value);
    }
}
