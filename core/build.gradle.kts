plugins {
    `java-library`
}

val lwjglVersion = "3.3.2-SNAPSHOT"
val jomlVersion = "1.10.4"
val slf4jVersion = "2.0.0-beta1"
val resourcesPath = "../resources"

val lwjglNatives = Pair(
    System.getProperty("os.name")!!,
    System.getProperty("os.arch")!!
).let { (name, arch) ->
    when {
        arrayOf("Linux", "FreeBSD", "SunOS", "Unit").any { name.startsWith(it) } ->
            if (arrayOf("arm", "aarch64").any { arch.startsWith(it) })
                "natives-linux${if (arch.contains("64") || arch.startsWith("armv8")) "-arm64" else "-arm32"}"
            else
                "natives-linux"

        arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } ->
            "natives-macos${if (arch.startsWith("aarch64")) "-arm64" else ""}"

        arrayOf("Windows").any { name.startsWith(it) } ->
            if (arch.contains("64"))
                "natives-windows${if (arch.startsWith("aarch64")) "-arm64" else ""}"
            else
                "natives-windows-x86"

        else -> throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
    }
}

logger.quiet("[ENGINE] Natives in use: $lwjglNatives")
logger.quiet("[ENGINE] Resources path: $resourcesPath")

java {
    sourceSets {
        main {
            resources {
                srcDirs(resourcesPath)
            }
        }
    }
}

dependencies {
    api(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl", "lwjgl")
    api("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    implementation("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    implementation("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
    implementation("org.slf4j", "slf4j-api", slf4jVersion)
    implementation("org.slf4j", "slf4j-simple", slf4jVersion)
    api("org.joml", "joml", jomlVersion)
}
