import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.guava)
    compileOnly(libs.android.tools.sdk)
    compileOnly(libs.android.sdklib)

    compileOnly(libs.android.tools.gradle)

    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.coreStubs.model.provider)

    implementation(libs.jadb)
    implementation(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("flixclusiveProvider") {
            id = "flx-provider"
            implementationClass = "FlixclusiveProvider"
        }
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

group = "com.github.flixclusive"
version = "1.3.0"

publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}
