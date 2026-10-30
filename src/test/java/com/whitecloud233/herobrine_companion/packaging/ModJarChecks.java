package com.whitecloud233.herobrine_companion.packaging;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Checks the shipped archive without falling back to development classes. */
public final class ModJarChecks {
    private static final String LOCOMOTION =
            "com.whitecloud233.herobrine_companion.entity.visual.HeroLocomotion";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected mod JAR and compiled class directories");
        }
        Path archive = Path.of(args[0]);
        Set<String> expected = new TreeSet<>();
        for (int i = 1; i < args.length; i++) {
            Path root = Path.of(args[i]);
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".class"))
                        .map(file -> root.relativize(file).toString().replace('\\', '/'))
                        .forEach(expected::add);
            }
        }
        if (expected.isEmpty()) throw new AssertionError("No compiled mod classes to verify");
        expected.add(LOCOMOTION.replace('.', '/') + ".class");
        Set<String> missing = new TreeSet<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (String name : expected) {
                var entry = zip.getEntry(name);
                if (entry == null) {
                    missing.add(name);
                } else {
                    try (var bytes = zip.getInputStream(entry)) {
                        if (bytes.read() != 0xCA || bytes.read() != 0xFE
                                || bytes.read() != 0xBA || bytes.read() != 0xBE) {
                            throw new AssertionError("Invalid class in mod JAR: " + name);
                        }
                    }
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("Mod JAR is missing compiled classes:\n" + String.join("\n", missing));
        }

        // The platform parent cannot resolve classes from build/classes or test dependencies.
        try (var loader = new URLClassLoader(new URL[]{archive.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> locomotion = Class.forName(LOCOMOTION, true, loader);
            verifyPose(locomotion, "computeIdleMicro", 0.0F);
            verifyPose(locomotion, "computeIdleMicro", 1.25F);
            for (float phase : new float[]{0.0F, 0.25F, 0.5F, 0.75F, 1.0F}) {
                verifyPose(locomotion, "computeWalkPose", phase);
            }
        }
        System.out.println("ModJarChecks: " + expected.size()
                + " packaged classes present; idle and walking execute from " + archive.getFileName());
    }

    private static void verifyPose(Class<?> locomotion, String method, float time) throws Exception {
        float[] pose = new float[32];
        java.util.Arrays.fill(pose, Float.NaN);
        locomotion.getMethod(method, float.class, float[].class).invoke(null, time, pose);
        for (int i = 0; i < pose.length; i++) {
            if (!Float.isFinite(pose[i])) {
                throw new AssertionError(method + " left an invalid pose at index " + i);
            }
        }
    }
}
