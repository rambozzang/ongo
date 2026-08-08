import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

apply(plugin = "jacoco")

val javaVersion = 25

allprojects {
    group = "com.ongo"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    extensions.configure<JacocoPluginExtension> {
        // Java 25 support is required by the build toolchain used in CI.
        toolVersion = "0.8.13"
    }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.7")
            mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            // Kotlin 2.2 (the Spring Boot 4.0 baseline) runs on JDK 25 but
            // emits Java 21 bytecode for broad runtime compatibility.
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

        testImplementation("org.springframework.boot:spring-boot-starter-test-classic")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("io.mockk:mockk:1.13.13")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Testcontainers 기반 통합 테스트는 Docker가 있는 로컬/전용 CI에서 실행한다.
        // 배포 Jenkins처럼 Docker를 제공하지 않는 환경에서는 단위 테스트만 돌릴 수 있게
        // 명시적인 스위치를 둔다. 기본값은 false라 로컬 전체 테스트 동작은 유지된다.
        if (project.findProperty("skipIntegrationTests") == "true") {
            exclude("**/*IT.class")
        }
    }

    // Every test task produces a machine-readable report. The report is
    // intentionally separate from the pass/fail gate: first make coverage
    // observable for every module, then raise thresholds from measured data.
    tasks.withType<Test>().configureEach {
        finalizedBy("jacocoTestReport")
    }
    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
        classDirectories.setFrom(
            classDirectories.files.map { directory ->
                fileTree(directory) {
                    exclude(
                        "**/*Application*",
                        "**/config/**",
                        "**/generated/**",
                        "**/jooq/**",
                    )
                }
            }
        )
    }
}

// A single report is required for the roadmap's backend coverage target. Module
// reports are useful for local ownership, but without an aggregate report a
// lightly-tested API module can disappear behind a well-tested utility module.
tasks.register<JacocoReport>("jacocoRootReport") {
    val testTasks = subprojects.map { it.tasks.named("test") }
    dependsOn(testTasks)

    sourceDirectories.from(subprojects.map { it.file("src/main/kotlin") })
    additionalSourceDirs.from(subprojects.map { it.file("src/main/kotlin") })
    classDirectories.from(
        subprojects.map { subproject ->
            fileTree(subproject.file("build/classes/kotlin/main")) {
                exclude(
                    "**/*Application*",
                    "**/config/**",
                    "**/generated/**",
                    "**/jooq/**",
                )
            }
        }
    )
    executionData.from(subprojects.map { it.file("build/jacoco/test.exec") })
    jacocoClasspath = configurations.getByName("jacocoAnt")
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoRootReport/jacocoRootReport.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoRootReport/html"))
    }
}

tasks.register<JacocoCoverageVerification>("jacocoRootCoverageVerification") {
    dependsOn("jacocoRootReport")
    sourceDirectories.from(subprojects.map { it.file("src/main/kotlin") })
    classDirectories.from(
        subprojects.map { subproject ->
            fileTree(subproject.file("build/classes/kotlin/main")) {
                exclude(
                    "**/*Application*",
                    "**/config/**",
                    "**/generated/**",
                    "**/jooq/**",
                )
            }
        }
    )
    executionData.from(subprojects.map { it.file("build/jacoco/test.exec") })
    jacocoClasspath = configurations.getByName("jacocoAnt")
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.25".toBigDecimal()
            }
        }
    }
}
