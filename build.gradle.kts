plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.25"
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
    id("io.micronaut.application") version "4.5.3"
    id("com.gradleup.shadow") version "8.3.6"
    id("io.micronaut.aot") version "4.5.3"
    id("io.micronaut.openapi") version "4.5.3"
}

version = "0.1"
group = "no.ssb.whodat"

val kotlinVersion = project.properties["kotlinVersion"]
repositories {
    mavenCentral()
}

dependencies {
    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    ksp("io.micronaut.openapi:micronaut-openapi")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("io.micronaut.openapi:micronaut-openapi-annotations")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.gcp:micronaut-gcp-common")
    implementation("io.micronaut.gcp:micronaut-gcp-secret-manager")
    implementation("org.yaml:snakeyaml")
    compileOnly("io.micronaut:micronaut-http-client")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("io.micronaut:micronaut-http-client")
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
