package org.conference.registration;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Executable layering rules (specification §1). */
@AnalyzeClasses(
    packages = "org.conference.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Api")
          .definedBy("..registration.api..")
          .layer("Service")
          .definedBy("..registration.service..")
          .layer("Repository")
          .definedBy("..registration.repository..")
          .layer("Domain")
          .definedBy("..registration.domain..")
          .layer("Config")
          .definedBy("..registration.config..")
          .whereLayer("Api")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Api")
          .whereLayer("Repository")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Config")
          .mayOnlyBeAccessedByLayers("Api", "Service")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Api", "Service", "Repository");

  @ArchTest
  static final ArchRule noCycles =
      slices().matching("org.conference.registration.(*)..").should().beFreeOfCycles();
}
