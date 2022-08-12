plugins {
	java
	id("org.javamodularity.moduleplugin") version("1.8.12") apply(false)
}

subprojects {
	apply(plugin = "org.javamodularity.moduleplugin")
	version = "1.0-SNAPSHOT"

	java {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	repositories {
		mavenCentral()
		maven("https://oss.sonatype.org/content/repositories/snapshots/")
	}
}
