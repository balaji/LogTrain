import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // The custom dbMain dependsOn edges below would otherwise disable the default
    // hierarchy template, leaving iosMain disconnected from the iOS compilations.
    applyDefaultHierarchyTemplate()

    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "dev.balaji.moonrose.trainfree.sharedLogic"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Room-backed persistence for every target.
        // sharedUI's targets are a subset of these, so its commonMain can use dbMain APIs.
        val dbMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                api(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        androidMain.get().apply {
            dependsOn(dbMain)
            dependencies {
                implementation(libs.mlkit.genai.prompt)
            }
        }
        iosMain.get().dependsOn(dbMain)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
