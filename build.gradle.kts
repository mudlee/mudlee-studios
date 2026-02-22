plugins {
	java
	id("org.javamodularity.moduleplugin") version("2.0.0") apply(false)
	id("com.diffplug.spotless") version("8.2.1") apply(false)
}

subprojects {
	apply(plugin = "org.javamodularity.moduleplugin")
	apply(plugin = "com.diffplug.spotless")
	version = "1.0-SNAPSHOT"

	java {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	repositories {
		mavenCentral()
		maven("https://oss.sonatype.org/content/repositories/snapshots/")
	}

	configure<com.diffplug.gradle.spotless.SpotlessExtension> {
		java {
			target("src/**/*.java")
			palantirJavaFormat("2.88.0")
		}
	}
}
