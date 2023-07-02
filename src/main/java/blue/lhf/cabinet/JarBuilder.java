package blue.lhf.cabinet;

import blue.lhf.cabinet.utils.ThrowingConsumer;
import blue.lhf.cabinette.commons.JarUtils;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;

public class JarBuilder implements AutoCloseable {
    private final JarOutputStream stream;

    public JarBuilder(final OutputStream stream) throws IOException {
        this.stream = new JarOutputStream(stream);
    }

    public <T extends Throwable> void add(final String name, final ThrowingConsumer<OutputStream, T> writer) throws IOException, T {
        stream.putNextEntry(new JarEntry(name));
        writer.accept(stream);
        stream.closeEntry();
    }

    public void add(final Path path) throws IOException {
        JarUtils.injectTree(stream, path, path, "");
    }

    public void add(final Path path, final Path root) throws IOException {
        JarUtils.injectTree(stream, path, root, "");
    }

    public void add(final Path path, final Path root, final String jarRoot) throws IOException {
        JarUtils.injectTree(stream, path, root, jarRoot);
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
