package dev.px.karatsuba.pathing.test;

import dev.px.core.test.harness.Checks;

/** Runs every pathing suite in a plain JVM: no Minecraft, no window. {@code gradlew :pathing:smoke}. */
public final class PathingSmokeTest {

    private PathingSmokeTest() {
    }

    public static void main(String[] args) {
        DStarLiteTests.run();
        System.exit(Checks.summary());
    }
}
