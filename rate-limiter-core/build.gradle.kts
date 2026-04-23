plugins {
    java
}

description = "Core algorithms: sliding window, token bucket, fixed window + Lua scripts"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:1.19.8")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
}
