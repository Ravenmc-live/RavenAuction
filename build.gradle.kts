import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import org.gradle.kotlin.dsl.invoke
import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Maintained fork of JSch used to upload jars over SFTP to remote Pterodactyl servers.
        classpath("com.github.mwiede:jsch:0.2.17")
    }
}

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

// ---------------------------------------------------------------------------
// SFTP deployment to remote Pterodactyl-hosted servers.
// Configure servers in gitignored 'deploy.properties' (see deploy.properties.example).
// ---------------------------------------------------------------------------

val deployPropsFile = rootProject.file("deploy.properties")
val deployProps = Properties().apply {
    if (deployPropsFile.exists()) deployPropsFile.inputStream().use { load(it) }
}

val deployServerNames = deployProps.getProperty("servers", "")
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }

fun ensureRemoteDir(channel: ChannelSftp, remoteDir: String) {
    var path = ""
    remoteDir.split("/").filter { it.isNotEmpty() }.forEach { part ->
        path += "/$part"
        try {
            channel.mkdir(path)
        } catch (ignored: Exception) {
            // directory already exists
        }
    }
}

fun sftpUpload(host: String, port: Int, user: String, password: String, localFile: File, remoteDir: String) {
    val session = JSch().getSession(user, host, port)
    session.setPassword(password)
    session.setConfig("StrictHostKeyChecking", "no")
    session.connect(15000)
    try {
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(15000)
        try {
            ensureRemoteDir(channel, remoteDir)
            channel.put(localFile.absolutePath, "$remoteDir/${localFile.name}")
        } finally {
            channel.disconnect()
        }
    } finally {
        session.disconnect()
    }
}

val sftpDeployAll = tasks.register("sftpDeployAll") {
    group = "Pterodactyl deployment"
    description = "Builds and uploads the plugin jar to every server configured in deploy.properties."
}

if (deployServerNames.isEmpty() && !deployPropsFile.exists()) {
    val sftpDeployHelp = tasks.register("sftpDeployHelp") {
        group = "Pterodactyl deployment"
        description = "Shown because deploy.properties is missing."
        doLast {
            throw GradleException(
                "No deploy.properties found at $deployPropsFile. " +
                    "Copy deploy.properties.example to deploy.properties and fill in your Pterodactyl SFTP credentials."
            )
        }
    }
    sftpDeployAll.configure { dependsOn(sftpDeployHelp) }
}

// The plugin jar is produced by the root project's shadowJar; resolved lazily so the task stays
// configuration-cache friendly.
val pluginJar = tasks.shadowJar.flatMap { it.archiveFile }

deployServerNames.forEach { name ->
    val serverTask = tasks.register("sftpDeployTo${name.replaceFirstChar { it.uppercase() }}") {
        group = "Pterodactyl deployment"
        description = "Builds and uploads the plugin jar to the '$name' Pterodactyl server via SFTP."
        dependsOn(tasks.shadowJar)
        doLast {
            val host = deployProps.getProperty("$name.host")
            val port = deployProps.getProperty("$name.port", "2022").toInt()
            val user = deployProps.getProperty("$name.user")
            val password = deployProps.getProperty("$name.password")
            val remoteDir = deployProps.getProperty("$name.remoteDir", "/plugins")

            if (host.isNullOrBlank() || user.isNullOrBlank() || password.isNullOrBlank()) {
                throw GradleException(
                    "Missing SFTP credentials for server '$name' in deploy.properties " +
                        "(need $name.host, $name.user, $name.password)"
                )
            }

            val jarFile = pluginJar.get().asFile
            logger.lifecycle("Uploading ${jarFile.name} to '$name' ($host:$port$remoteDir)")
            sftpUpload(host, port, user, password, jarFile, remoteDir)
            logger.lifecycle("Uploaded ${jarFile.name} to '$name'")
        }
    }
    sftpDeployAll.configure { dependsOn(serverTask) }
}
