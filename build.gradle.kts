plugins {
    java
    id("org.springframework.boot")  version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
}

// ── Versions catalogue (single source of truth) ────────────────────────────
val javaVersion       = JavaVersion.VERSION_21
val lombokVersion     = "1.18.32"
val micrometerVersion = "1.13.0"
val testcontainersVersion = "1.19.8"

// ── Shared config for every subproject ─────────────────────────────────────
subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group   = "com.portfolio"
    version = "1.0.0"

    java {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        // Lombok available in every module
        compileOnly("org.projectlombok:lombok:$lombokVersion")
        annotationProcessor("org.projectlombok:lombok:$lombokVersion")

        testCompileOnly("org.projectlombok:lombok:$lombokVersion")
        testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.5")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }
}
