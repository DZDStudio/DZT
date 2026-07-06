import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.jvm.toolchain.JavaToolchainService


plugins {
    java
    id("io.izzel.taboolib") version "2.0.37"
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

project.version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-DDD-HHmmss"))

taboolib {
    env {
        install(AlkaidRedis, Basic, Bukkit, BukkitFakeOp, BukkitHook, BukkitNMS, BukkitNMSDataSerializer, BukkitNMSEntityAI, BukkitNMSItemTag, BukkitNMSUtil, BukkitNavigation, BukkitUI, BukkitUtil, CommandHelper, Database, DatabasePlayer, DatabasePlayerRedis, I18n, IOC, Kether, LettuceRedis, Metrics, MinecraftChat, MinecraftEffect, PtcObject, XSeries)
    }
    description {
        name("DZT")
        desc("DZDGame 服务器核心插件")
        load("STARTUP")
        dependencies {
            name("Geyser-Spigot")
            name("floodgate")
        }
        contributors {
            name("DZDStudio")
        }
        links {
            name("https://github.com/DZDStudio/DZT")
        }
    }
    version {
        taboolib = "6.3.0-c6f096d"
        coroutines = "1.8.1"
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.opencollab.dev/main/")
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))

    compileOnly("io.papermc.paper:paper-api:1.21.9-rc1-R0.1-SNAPSHOT")

    compileOnly("org.geysermc.geyser:api:2.9.5-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")

    taboo("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    taboo("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0") { isTransitive = false }
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0") { isTransitive = false }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

runPaper.folia.registerTask()
tasks {
    runServer {
        minecraftVersion("26.1.2")
    }
}
