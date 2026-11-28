package io.github.morganbyte.zpenz;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * El dominio es Java puro (ADR-0002 y ADR-0006): no conoce Spring, ni la base de datos,
 * ni los adaptadores. Estas reglas fallan el build, a diferencia de las advertencias de
 * {@code ArchitectureTest}, que genera el scaffold.
 */
class DomainIndependenceTest {

    private static final String MODEL = "io.github.morganbyte.zpenz.model..";
    private static final String USECASE = "io.github.morganbyte.zpenz.usecase..";

    private static JavaClasses domainClasses;

    @BeforeAll
    static void importDomain() {
        domainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.github.morganbyte.zpenz.model", "io.github.morganbyte.zpenz.usecase");
    }

    @Test
    void domainExists() {
        // Si cambia el paquete del dominio, las reglas de abajo pasarían sin revisar nada.
        org.assertj.core.api.Assertions.assertThat(domainClasses).isNotEmpty();
    }

    @Test
    void domainOnlyDependsOnTheJdkAndItself() {
        classes().that().resideInAnyPackage(MODEL, USECASE)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", MODEL, USECASE)
                .because("el dominio es Java puro")
                .check(domainClasses);
    }

    @Test
    void modelDoesNotDependOnUseCases() {
        noClasses().that().resideInAPackage(MODEL)
                .should().dependOnClassesThat().resideInAPackage(USECASE)
                .because("los casos de uso orquestan el modelo, no al revés")
                .check(domainClasses);
    }
}
