package net.zamasoft.pdfg2d.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.zamasoft.pdfg2d.io.impl.AbstractTempFileOutput;
import net.zamasoft.pdfg2d.io.impl.FileFragmentedOutput;
import net.zamasoft.pdfg2d.io.impl.StreamFragmentedOutput;
import net.zamasoft.pdfg2d.io.util.FragmentOutputAdapter;
import net.zamasoft.pdfg2d.io.util.FragmentedOutputWrapper;
import net.zamasoft.pdfg2d.io.util.OutputMeasurer;
import net.zamasoft.pdfg2d.io.util.PositionTrackingOutput;
import net.zamasoft.pdfg2d.io.util.SequentialOutputAdapter;

class FragmentedOutputTest {
    @TempDir
    File tempDir;

    @Test
    void testStreamFragmentedOutputAssemblesFragmentsInLinkedOrderAndSupportsPatch() throws Exception {
        final var out = new ByteArrayOutputStream();
        final var output = new StreamFragmentedOutput(out, AbstractTempFileOutput.Config.ON_MEMORY);
        output.addFragment();
        output.addFragment();
        output.insertFragmentBefore(1);

        output.write(0, bytes("AA"), 0, 2);
        output.write(1, bytes("BB"), 0, 2);
        output.write(2, bytes("CC"), 0, 2);

        final var positionInfo = output.getPositionInfo();
        assertEquals(0, positionInfo.getPosition(0));
        assertEquals(4, positionInfo.getPosition(1));
        assertEquals(2, positionInfo.getPosition(2));

        output.patch(2, 1, bytes("Z"), 0, 1);
        output.close();

        assertEquals("AACZBB", out.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    void testFileFragmentedOutputSupportsSequentialWrites() throws Exception {
        final var file = new File(tempDir, "fragmented.bin");
        final var output = new FileFragmentedOutput(file, AbstractTempFileOutput.Config.ON_MEMORY);

        output.write(bytes("hello"), 0, 5);
        output.write(bytes("-world"), 0, 6);
        output.close();

        assertTrue(file.isFile());
        assertArrayEquals(bytes("hello-world"), Files.readAllBytes(file.toPath()));
    }

    @Test
    void testAdaptersAndWrappersDelegateAndTrackLengths() throws Exception {
        final var out = new ByteArrayOutputStream();
        final var base = new StreamFragmentedOutput(out, AbstractTempFileOutput.Config.ON_MEMORY);
        final var measurer = new OutputMeasurer(base);
        final var wrapped = new FragmentedOutputWrapper(measurer);

        wrapped.addFragment();
        wrapped.addFragment();

        try (OutputStream fragment0 = new FragmentOutputAdapter(wrapped, 0)) {
            fragment0.write(bytes("ab"));
            fragment0.write('c');
        }
        try (OutputStream fragment1 = new FragmentOutputAdapter(wrapped, 1)) {
            fragment1.write(bytes("de"), 0, 2);
        }

        assertEquals(5L, measurer.getLength());
        wrapped.close();
        assertEquals("abcde", out.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    void testPositionTrackingAndSequentialOutputAdapters() throws Exception {
        final var out = new ByteArrayOutputStream();
        final var sequential = new StreamFragmentedOutput(out, AbstractTempFileOutput.Config.ON_MEMORY);
        try (final var adapter = new SequentialOutputAdapter(sequential)) {
            adapter.write('A');
            adapter.write(bytes("BC"));
            adapter.write(bytes("DEF"), 1, 2);
        }
        assertEquals("ABCEF", out.toString(StandardCharsets.ISO_8859_1));

        final var trackedOut = new ByteArrayOutputStream();
        final FragmentedOutput tracked = new PositionTrackingOutput(
                new FragmentedOutput() {
                    private final java.util.List<byte[]> fragments = new java.util.ArrayList<>();

                    @Override
                    public void addFragment() {
                        this.fragments.add(new byte[0]);
                    }

                    @Override
                    public void insertFragmentBefore(final int anchorId) {
                        this.fragments.add(anchorId, new byte[0]);
                    }

                    @Override
                    public void write(final int id, final byte[] b, final int off, final int len) {
                        final byte[] old = this.fragments.get(id);
                        final byte[] next = java.util.Arrays.copyOf(old, old.length + len);
                        System.arraycopy(b, off, next, old.length, len);
                        this.fragments.set(id, next);
                    }

                    @Override
                    public boolean supportsPositionInfo() {
                        return false;
                    }

                    @Override
                    public PositionInfo getPositionInfo() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void finishFragment(final int id) {
                    }

                    @Override
                    public void close() {
                        for (final byte[] fragment : this.fragments) {
                            trackedOut.write(fragment, 0, fragment.length);
                        }
                    }
                });

        tracked.addFragment();
        tracked.addFragment();
        tracked.insertFragmentBefore(1);
        tracked.write(0, bytes("1"), 0, 1);
        tracked.write(1, bytes("3"), 0, 1);
        tracked.write(2, bytes("2"), 0, 1);
        final var info = tracked.getPositionInfo();
        assertEquals(0, info.getPosition(0));
        assertEquals(2, info.getPosition(1));
        assertEquals(1, info.getPosition(2));
        tracked.close();
        assertEquals("132", trackedOut.toString(StandardCharsets.ISO_8859_1));
    }

    private static byte[] bytes(final String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
