package com.linuxconsole;

/**
 * Separate launcher that does NOT extend javafx.application.Application.
 *
 * This works around a known JavaFX issue: when running a shaded/fat jar with
 * `java -jar`, if the class containing main() directly extends Application,
 * Java's module system check fails with "JavaFX runtime components are
 * missing" even though JavaFX IS present on the classpath. Routing through
 * a plain class avoids that check.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
