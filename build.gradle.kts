import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


plugins {
    java
    id("io.izzel.taboolib") version "2.0.30"
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

project.version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-DDD-HHmmss"))

taboolib {
    env {
        install(Basic, Bukkit, BukkitHook, BukkitNMS, BukkitNMSDataSerializer, BukkitNMSItemTag, BukkitNMSUtil, BukkitUI, BukkitUtil, CommandHelper, Database, MinecraftChat, MinecraftEffect, XSeries)
    }
    description {
        name = "DZT"
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
        taboolib = "6.2.4-86dd2bf"
        coroutines = "1.8.1"
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.opencollab.dev/main/")
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))

    compileOnly("ink.ptms.core:v12111:12111:universal")
    compileOnly("ink.ptms.core:v12111:12111:mapped")
    compileOnly("ink.ptms:nms-all:1.0.0")

    compileOnly("org.geysermc.geyser:api:2.9.3-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    clean {
        doFirst {
            delete(layout.buildDirectory)
        }
    }
    
    runServer {
        dependsOn(clean)
        serverJar(file("run/leaf-1.21.11-41.jar"))
        minecraftVersion("1.21.11")
        downloadPlugins {
            github("dmulloy2", "ProtocolLib", "5.4.0", "ProtocolLib.jar")
            github("PlaceholderAPI", "PlaceholderAPI", "2.12.2", "PlaceholderAPI-2.12.2.jar")
            url("https://download.luckperms.net/1620/bukkit/loader/LuckPerms-Bukkit-5.5.32.jar")

            url("https://download.geysermc.org/v2/projects/geyser/versions/2.9.3/builds/1071/downloads/spigot")
            url("https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/126/downloads/spigot")
            url("https://download.geysermc.org/v2/projects/hurricane/versions/2.1/builds/3/downloads/spigot")
        }
    }
}