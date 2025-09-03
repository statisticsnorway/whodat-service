import com.google.devtools.ksp.KspExperimental

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.gradle.shadow)
    alias(libs.plugins.micronaut.aot)
    alias(libs.plugins.micronaut.openapi)
}

version = "0.1"
group = "no.ssb.whodat"

val kotlinVersion = project.properties["kotlinVersion"]
repositories {
    mavenCentral()
}

dependencies {
    ksp(libs.micronaut.http.validation)
    ksp(libs.micronaut.serde.processor)
    ksp(libs.micronaut.openapi)
    implementation(libs.micronaut.kotlin.runtime)
    implementation(libs.micronaut.serde.jackson)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.micronaut.openapi.annotations)
    implementation(libs.micronaut.security.jwt)
    implementation(libs.micronaut.http.client)
    implementation(libs.micronaut.gcp.common)
    implementation(libs.micronaut.gcp.secret.manager)
    implementation(libs.snakeyaml)
    annotationProcessor(libs.micronaut.security.annotations)
    compileOnly(libs.micronaut.http.client)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.jackson.module.kotlin)
    testImplementation(libs.micronaut.http.client)
}

application {
    mainClass = "no.ssb.whodat.ApplicationKt"
}
java {
    sourceCompatibility = JavaVersion.toVersion("21")
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("no.ssb.whodat.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
    openapi {
        client(file("src/main/resources/freg-openapi.yaml")) {
            apiPackageName.set("com.mycompany.api")
            modelPackageName.set("com.mycompany.model")
            useOptional.set(true)
            clientId.set("some-client-id")
            // Supports Kotlin codegen too
            lang.set("kotlin")
        }
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "no.ssb.whodat.ApplicationKt"
    }

    // To avoid the duplicate handling strategy error
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // To add all the dependencies otherwise a "NoClassDefFoundError" error
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
}

tasks.register<JavaExec>("runLocal") {
    mainClass.set("no.ssb.whodat.ApplicationKt")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs = listOf("-Dmicronaut.environments=local")
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}
