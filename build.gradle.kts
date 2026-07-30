import org.gradle.kotlin.dsl.invoke

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta11"
    id("re.alwyn974.groupez.repository") version "1.0.0"
}

group = "fr.maxlego08.zauctionhouse"
version = "4.0.1.0"

extra.set("targetFolder", file("target/"))
extra.set("apiFolder", file("target-api/"))
extra.set("classifier", System.getProperty("archive.classifier"))
extra.set("sha", System.getProperty("github.sha"))

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "re.alwyn974.groupez.repository")

    group = "fr.maxlego08.zauctionhouse"
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()

        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "tcoded-releases"
            url = uri("https://repo.tcoded.com/releases")
        }
        maven {
            name = "tcoded-releases"
            url = uri("https://repo.extendedclip.com/releases/")
        }
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.shadowJar {

        archiveBaseName.set("RavenAuction")
        archiveAppendix.set(if (project.path == ":") "" else project.name)
        archiveClassifier.set("")
    }

    tasks.compileJava {
        options.encoding = "UTF-8"
    }

    tasks.javadoc {
        options.encoding = "UTF-8"
        if (JavaVersion.current().isJava9Compatible)
            (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
        compileOnly("me.clip:placeholderapi:2.11.6")
        compileOnly("fr.maxlego08.menu:zmenu-api:1.1.1.6")

        implementation("fr.maxlego08.sarah:sarah:1.23")
        implementation("com.tcoded:FoliaLib:0.5.1")
        implementation("fr.traqueur.currencies:currenciesapi:1.0.13")
    }
}

repositories {

}

dependencies {
    api(projects.api)
    api(projects.hooks)

    // Redis client for the multi-server cluster bridge. Declared here rather than in allprojects
    // because only this module talks to Redis, and only this module's shadowJar relocates it.
    // Only core commands and pub/sub are used, so the JSON extras are dropped; slf4j-api is already
    // bundled by sarah, so shading a second copy would clash.
    implementation("redis.clients:jedis:5.2.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "org.json", module = "json")
    }
}

tasks {
    shadowJar {
        relocate("fr.maxlego08.sarah", "fr.maxlego08.zauctionhouse.libs.sarah")
        relocate("com.tcoded.folialib", "fr.maxlego08.zauctionhouse.libs.folialib")
        relocate("fr.traqueur.currencies", "fr.maxlego08.zauctionhouse.libs.currencies")
        relocate("redis.clients", "fr.maxlego08.zauctionhouse.libs.jedis")
        relocate("org.apache.commons.pool2", "fr.maxlego08.zauctionhouse.libs.pool2")

        rootProject.extra.properties["sha"]?.let { sha ->
            archiveClassifier.set("${rootProject.extra.properties["classifier"]}-${sha}")
        } ?: run {
            archiveClassifier.set(rootProject.extra.properties["classifier"] as String?)
        }
        destinationDirectory.set(rootProject.extra["targetFolder"] as File)
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.release = 21
    }

    processResources {
        from("resources")
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
