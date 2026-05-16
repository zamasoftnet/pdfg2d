package net.zamasoft.pdfg2d.test;

import java.io.File;

public final class TestOutputFiles {
    private static final String OUTPUT_DIR_PROPERTY = "pdfg2d.testOutputDir";

    private TestOutputFiles() {
    }

    public static File outputFile(final Class<?> owner, final String fileName) {
        final var baseDir = new File(System.getProperty(OUTPUT_DIR_PROPERTY, "build/generated-test-files"));
        final var classDir = new File(baseDir, owner.getName().replace('.', File.separatorChar));
        if (!classDir.exists() && !classDir.mkdirs()) {
            throw new IllegalStateException("Failed to create test output directory: " + classDir);
        }
        return new File(classDir, fileName);
    }
}
