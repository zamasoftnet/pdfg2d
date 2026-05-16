import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.net.URL;
import java.net.URLClassLoader;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildEmojiIndexToolTest {
    private static final Path EMOJI_ZIP = Path.of(
            "src/main/resources/net/zamasoft/pdfg2d/font/emoji/emoji.zip");
    private static final Path TOOL_SOURCE = Path.of("BuildEmojiIndexTool.java");

    @TempDir
    Path tempDir;

    @Test
    void testEmojiZipContainsIndexLicenseAndRepresentativeEntries() throws Exception {
        final var entries = new HashSet<String>();
        String index = null;

        try (final var zis = new ZipInputStream(new ByteArrayInputStream(java.nio.file.Files.readAllBytes(EMOJI_ZIP)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().equals("INDEX")) {
                    index = new String(zis.readAllBytes(), StandardCharsets.ISO_8859_1);
                }
            }
        }

        assertTrue(entries.contains("LICENSE"));
        assertTrue(entries.contains("INDEX"));
        assertTrue(entries.contains("emoji_u1f44d.svg"));
        assertTrue(entries.contains("emoji_u1f44d_1f3fb.svg"));
        assertTrue(entries.contains("emoji_u1f469_200d_2764_200d_1f468.svg"));

        assertTrue(index != null && !index.isBlank(), "INDEX should exist and be non-empty");
        final var lines = Set.of(index.split("\\R"));
        assertTrue(lines.contains("1f44d"), "INDEX should contain simple emoji codes");
        assertTrue(lines.contains("1f44d_1f3fb"), "INDEX should contain multi-code emoji sequences");
        assertTrue(lines.contains("1f469_200d_2764_200d_1f468"), "INDEX should contain joined emoji sequences");
        assertTrue(lines.contains("1f469_200d_2764"), "INDEX should contain joined emoji prefixes");
        assertTrue(lines.contains("1f469"), "INDEX should contain prefix base codes");
        assertTrue(lines.contains("200d"), "INDEX should contain ZWJ marker");
    }

    @Test
    void testWriteIndexAddsFullCodesPrefixesAndZwj() throws Exception {
        final var toolClass = compileAndLoadToolClass();
        final var tool = newToolInstance(toolClass);
        final var outBytes = new ByteArrayOutputStream();

        try (final var zos = new ZipOutputStream(outBytes)) {
            invokeWriteIndex(toolClass, tool, zos, List.of(
                    "emoji_u1f468_200d_1f469.svg",
                    "emoji_u1f44d_1f3fb.svg"));
        }

        final var index = readZipEntry(outBytes.toByteArray(), "INDEX");
        final var lines = Set.of(index.split("\\R"));

        assertTrue(lines.contains("1f468_200d_1f469"));
        assertTrue(lines.contains("1f468_200d"));
        assertTrue(lines.contains("1f468"));
        assertTrue(lines.contains("1f44d_1f3fb"));
        assertTrue(lines.contains("1f44d"));
        assertTrue(lines.contains("200d"));
    }

    @Test
    void testProcessSourceArchiveCopiesLicenseAndEmojiFilesWithoutDuplicates() throws Exception {
        final var sourceBytes = new ByteArrayOutputStream();
        try (final var zos = new ZipOutputStream(sourceBytes)) {
            writeZipEntry(zos, "noto-emoji-main/LICENSE", "license");
            writeZipEntry(zos, "noto-emoji-main/svg/emoji_u1f44d.svg", "<svg/>");
            writeZipEntry(zos, "noto-emoji-main/third_party/svg/emoji_u1f44d.svg", "<svg/>");
            writeZipEntry(zos, "noto-emoji-main/svg/emoji_u1f469_200d_2764_200d_1f468.svg", "<svg/>");
            writeZipEntry(zos, "noto-emoji-main/png/emoji_u1f44d.png", "png");
            writeZipEntry(zos, "other/path/ignored.svg", "<svg/>");
        }

        final var toolClass = compileAndLoadToolClass();
        final var tool = newToolInstance(toolClass);
        final var copiedBytes = new ByteArrayOutputStream();
        final var emojiFiles = new ArrayList<String>();
        final var addedEntries = new HashSet<String>();

        try (final var zis = new ZipInputStream(new ByteArrayInputStream(sourceBytes.toByteArray()));
                final var zos = new ZipOutputStream(copiedBytes)) {
            invokeProcessSourceArchive(toolClass, tool, zis, zos, emojiFiles, addedEntries);
        }

        final var copiedEntries = listZipEntries(copiedBytes.toByteArray());
        assertEquals(Set.of("LICENSE", "emoji_u1f44d.svg", "emoji_u1f469_200d_2764_200d_1f468.svg"), copiedEntries);
        assertEquals(List.of("emoji_u1f44d.svg", "emoji_u1f469_200d_2764_200d_1f468.svg"), emojiFiles);
        assertFalse(copiedEntries.contains("emoji_u1f44d.png"));
    }

    private Class<?> compileAndLoadToolClass() throws Exception {
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var outputDir = tempDir.resolve("tool-classes");
        java.nio.file.Files.createDirectories(outputDir);
        final int result = compiler.run(null, null, null,
                "-encoding", "UTF-8",
                "-d", outputDir.toString(),
                TOOL_SOURCE.toString());
        assertEquals(0, result, "BuildEmojiIndexTool.java should compile for reflective testing");

        final URLClassLoader loader = new URLClassLoader(new URL[] { outputDir.toUri().toURL() });
        return loader.loadClass("BuildEmojiIndexTool");
    }

    private static Object newToolInstance(final Class<?> toolClass) throws Exception {
        final Constructor<?> ctor = toolClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void invokeWriteIndex(final Class<?> toolClass, final Object tool, final ZipOutputStream zos,
            final List<String> emojiFiles) throws Exception {
        final Method method = toolClass.getDeclaredMethod("writeIndex", ZipOutputStream.class, List.class);
        method.setAccessible(true);
        method.invoke(tool, zos, emojiFiles);
    }

    private static void invokeProcessSourceArchive(final Class<?> toolClass, final Object tool, final ZipInputStream zis,
            final ZipOutputStream zos, final List<String> emojiFiles, final Set<String> addedEntries) throws Exception {
        final Method method = toolClass.getDeclaredMethod(
                "processSourceArchive", ZipInputStream.class, ZipOutputStream.class, List.class, Set.class);
        method.setAccessible(true);
        method.invoke(tool, zis, zos, emojiFiles, addedEntries);
    }

    private static String readZipEntry(final byte[] zipBytes, final String entryName) throws IOException {
        try (final var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zis.readAllBytes(), StandardCharsets.ISO_8859_1);
                }
            }
        }
        throw new IOException("Missing entry: " + entryName);
    }

    private static Set<String> listZipEntries(final byte[] zipBytes) throws IOException {
        final var names = new HashSet<String>();
        try (final var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static void writeZipEntry(final ZipOutputStream zos, final String name, final String body) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(body.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
