package net.zamasoft.pdfg2d.demo;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Utility methods shared by the pdfg2d demo applications.
 * <p>
 * Provides helpers for locating resource files and obtaining the output
 * directory into which generated PDFs are written.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public final class DemoUtils {
    private DemoUtils() {
        // Utility class.
    }

    /**
     * Returns a {@link File} for a named classpath resource.
     * <p>
     * The method first attempts to resolve the resource through the class
     * loader. If that fails (e.g. the resource is inside a JAR), it falls
     * back to well-known relative paths on the local file system that are
     * typical for IDE and Maven project layouts.
     * </p>
     *
     * @param name the resource name (relative path within the resources root)
     * @return the resolved {@link File}
     * @throws IOException if the resource cannot be found
     */
    public static File getResourceFile(final String name) throws IOException {
        // Try to load from classpath
        final URL url = DemoUtils.class.getClassLoader().getResource(name);
        if (url != null) {
            try {
                return new File(url.toURI());
            } catch (final URISyntaxException e) {
                throw new IOException(e);
            } catch (final IllegalArgumentException e) {
                // URI is not distinct or hierarchical (e.g. inside a JAR)
                // We cannot return a File object for a resource inside a JAR.
                // However, for this demo in an IDE environment, it should be a file.
            }
        }

        // Fallback to local file system relative paths
        final String[] paths = {
                "pdfg2d-demo/src/main/resources/" + name,
                "src/main/resources/" + name,
                "src/example/" + name
        };

        for (final String path : paths) {
            final File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }

        throw new IOException("Resource not found: " + name);
    }

    /**
     * Returns the output directory used by all demo applications.
     * <p>
     * The directory {@code output/} is created relative to the current
     * working directory if it does not already exist.
     * </p>
     *
     * @return the output {@link File} directory (guaranteed to exist)
     */
    public static File getOutputDir() {
        final File dir = new File("output");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + dir.getAbsolutePath());
        }
        return dir;
    }
}
