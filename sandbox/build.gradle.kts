import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem

plugins {
	application
	id("org.beryx.jlink") version("3.1.5")
}

dependencies {
	implementation(project(":core"))
}

val currentOs: DefaultOperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
var engineJvmArgs = listOf("-Dorg.lwjgl.system.allocator=system", "-Dorg.lwjgl.util.DebugLoader=true", "-Dorg.lwjgl.util.Debug=true", "-Dorg.lwjgl.opengl.Display.enableHighDPI=true", "-Dorg.lwjgl.opengl.Display.enableOSXFullscreenModeAPI=true")
if(currentOs.isMacOsX) {
	engineJvmArgs = engineJvmArgs.plus(listOf("-XstartOnFirstThread"))
}

application {
	mainModule.set(moduleName)
	mainClass.set("hu.mudlee.sandbox.SandboxApplication")
	applicationDefaultJvmArgs = engineJvmArgs

	logger.quiet("[ENGINE] [application] Main Module: ${mainModule.get()}")
	logger.quiet("[ENGINE] [application] Main Class: ${mainClass.get()}")
	logger.quiet("[ENGINE] [application] JVM args: ${engineJvmArgs.joinToString(", ")}")

	// See https://github.com/java9-modularity/gradle-modules-plugin/issues/165
	modularity.disableEffectiveArgumentsAdjustment()
}

jlink {
	addOptions("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
	// https://github.com/beryx-gist/badass-jlink-example-richtextfx/blob/master/build.gradle
	// jvmArgs = ['-splash:$APPDIR/splash.png']

	logger.quiet("[ENGINE] [jlink] Options: ${jlink.options.get().joinToString(" ")}")
	logger.quiet("[ENGINE] [jlink] JVM args: ${engineJvmArgs.joinToString(", ")}")

	launcher {
		name = "sandbox-app"
		jvmArgs = engineJvmArgs
	}

	jpackage {
		skipInstaller = true
	}
}
