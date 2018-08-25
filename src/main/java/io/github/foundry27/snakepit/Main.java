package io.github.foundry27.snakepit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Foundry
 */
public final class Main {
    private static File getSourceFile(final String[] args) {
        if (args.length > 0) {
            return new File(args[0]);
        } else {
            throw new IllegalStateException("an input file name must be specified as a first argument");
        }
    }

    private static String getOutputFileName(final String[] args) {
        if (args.length > 1) {
            return args[1];
        } else {
            return "out.jar";
        }
    }

    public static void main(final String[] args) throws IOException  {
        final File sourceFile = getSourceFile(args);
        final String outputFileName = getOutputFileName(args);
        try (final JarFile jarFile = new JarFile(sourceFile)) {
            final Stream<EagerFileData> dataStream = Stream.concat(
                    streamClassFilesInJar(jarFile).map(Main::getTransformedClassBytes),
                    streamNonClassFilesFromJar(jarFile).map(Main::toEagerData)
            );
            saveDataToJar(dataStream, outputFileName);
        }
    }

    private static boolean areHeaderBytesValue(final byte first, final byte second, final byte third, final byte fourth) {
        return first == (byte) 0xCA && second == (byte) 0xFE && third == (byte) 0xBA && fourth == (byte) 0xBE;
    }

    private static ClassReader readClassBytesFromEntry(final JarFile jar, final JarEntry entry) {
        try (final InputStream jis = jar.getInputStream(entry)) {
            final byte[] bytes = toByteArray(jis);

            if (areHeaderBytesValue(bytes[0], bytes[1], bytes[2], bytes[3])) {
                return new ClassReader(bytes);
            } else {
                throw new IllegalStateException("malformed class file '" + jar.getName() + "' present in jar file: invalid magic header");
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<ClassReader> streamClassFilesInJar(final JarFile jarFile) throws IOException {
        return jarFile.stream()
                .filter(e -> e.getName().endsWith(".class"))
                .map(z -> readClassBytesFromEntry(jarFile, z));
    }

    private static EagerFileData getTransformedClassBytes(final ClassReader cr) {
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new IndyInsnSubstitutingClassVisitor(cw, cr.getClassName()), 0);
        return new EagerFileData(cr.getClassName(), cw.toByteArray());
    }

    private static LazyFileData getFileDataFromZipEntry(final ZipFile file, final ZipEntry entry) {
        try {
            return new LazyFileData(entry.getName(), file.getInputStream(entry));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<LazyFileData> streamNonClassFilesFromJar(final JarFile jarFile) throws IOException {
        return jarFile.stream()
                .filter(e -> !e.getName().endsWith(".class") && !e.isDirectory())
                .map(e -> getFileDataFromZipEntry(jarFile, e));
    }

    private static void saveDataToJar(final Stream<EagerFileData> dataStream, final String fileName) throws IOException {
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(fileName))) {
            dataStream.forEach(data -> {
                try {
                    final String fileExtension = data.name.contains(".") ? "" : ".class";
                    out.putNextEntry(new ZipEntry(data.name + fileExtension));
                    out.write(data.bytes);
                    out.closeEntry();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static byte[] toByteArray(final InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private static EagerFileData toEagerData(final LazyFileData lazy) {
        try {
            return new EagerFileData(lazy.name, toByteArray(lazy.byteStream));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class LazyFileData {

        final String name;

        final InputStream byteStream;

        LazyFileData(final String name, final InputStream byteStream) {
            this.name = name;
            this.byteStream = byteStream;
        }
    }

    private static final class EagerFileData {

        final String name;

        final byte[] bytes;

        EagerFileData(final String name, final byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }
}
