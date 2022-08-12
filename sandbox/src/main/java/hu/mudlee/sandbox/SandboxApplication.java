package hu.mudlee.sandbox;

import hu.mudlee.core.Application;
import hu.mudlee.core.settings.Antialiasing;
import hu.mudlee.core.settings.WindowPreferences;

public class SandboxApplication {
	public static void main(String[] args) {
		var app = new Application(
				new SandboxGame(),
				WindowPreferences.builder().title("TESTING").antialiasing(Antialiasing.OFF).fullscreen(false).build()
		);
		app.start();
	}
}
