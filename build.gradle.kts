import com.google.devtools.ksp.KspExperimental

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.gradle.shadow)
    alias(libs.plugins.micronaut.aot)
    alias(libs.plugins.micronaut.openapi)
    alias(libs.plugins.jib)
    alias(libs.plugins.cyclonedx)
}

version = "0.1.0"
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
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.micronaut.cache.core)
    implementation(libs.micronaut.aop)
    implementation(libs.micronaut.management)
    implementation(libs.micronaut.openapi.annotations)
    implementation(libs.micronaut.security.jwt)
    implementation(libs.micronaut.http.client)
    implementation(libs.micronaut.gcp.common)
    implementation(libs.micronaut.gcp.secret.manager)
    implementation(libs.reactor)
    implementation(libs.snakeyaml)
    annotationProcessor(libs.micronaut.security.annotations)
    compileOnly(libs.micronaut.http.client)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.jackson.module.kotlin)
    runtimeOnly(libs.micronaut.cache.caffeine)
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
}

tasks.cyclonedxBom {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setProjectType("application")
}

jib {
    from {
        image = "gcr.io/distroless/java21-debian12@sha256:70e8a4991b6e37cb1eb8eac3b717ed0d68407d1150cf30235d50cd33b2c44f7e"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
}

tasks.withType<Test> {
    environment("MICRONAUT_CONFIG_CLIENT_ENABLED", "false")
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
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

val versionFile = file("build.gradle.kts")

fun bumpVersion(type: String) {
    val versionRegex = """version\s*=\s*"(\d+)\.(\d+)\.(\d+)"""".toRegex()
    val content = versionFile.readText()

    val updatedContent =
        versionRegex.replace(content) { matchResult ->
            val (major, minor, patch) = matchResult.destructured
            val newVersion =
                when (type) {
                    "major" -> "${major.toInt() + 1}.0.0"
                    "minor" -> "$major.${minor.toInt() + 1}.0"
                    "patch" -> "$major.$minor.${patch.toInt() + 1}"
                    else -> throw IllegalArgumentException("Invalid version type: $type")
                }
            """version = "$newVersion""""
        }

    versionFile.writeText(updatedContent)
    println("Successfully updated version")
}

tasks.register("versionMajor") {
    group = "versioning"
    description = "Bump the major version"
    doLast {
        bumpVersion("major")
    }
}

tasks.register("versionMinor") {
    group = "versioning"
    description = "Bump the minor version"
    doLast {
        bumpVersion("minor")
    }
}

tasks.register("versionPatch") {
    group = "versioning"
    description = "Bump the patch version"
    doLast {
        bumpVersion("patch")
    }
}
