plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Spring Boot API Gateway — rate-limit filter, metrics endpoints, demo controllers"

dependencies {
    implementation(project(":rate-limiter-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// ── Fat JAR task (bootJar is the Spring Boot executable jar) ──────────────
tasks.bootJar {
    archiveFileName.set("rate-limiter-api.jar")
}

// Disable plain jar to avoid ambiguity
tasks.jar {
    enabled = false
}
