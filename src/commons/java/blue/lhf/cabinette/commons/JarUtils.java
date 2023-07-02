package blue.lhf.cabinette.commons;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.jar.*;
import java.util.zip.ZipEntry;

import static java.nio.file.FileVisitResult.CONTINUE;

public class JarUtils {
    private JarUtils() {

    }

    public static FileSystem self() throws IOException, URISyntaxException {
        return FileSystems.newFileSystem(JarUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI(), Map.of());
    }

    public static FileSystem open(final Path jar) throws IOException {
        return FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of());
    }

    public static void injectTree(final JarOutputStream stream, final Path root,
                           final Path start, final String jarRoot) throws IOException {
        final String prefix = jarRoot.isBlank() ? "" : jarRoot + "/";
        Files.walkFileTree(start, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                stream.putNextEntry(new ZipEntry(prefix + root.relativize(file)));
                try (final InputStream is = Files.newInputStream(file)) {
                    is.transferTo(stream);
                }
                stream.closeEntry();

                return CONTINUE;
            }
        });
    }
}
