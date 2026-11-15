package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * The compile-only stand-ins in {@code src/paperStubs} against the real classes of a Paper server jar: every public
 * method of a stub exists there with the same descriptor and the same static/abstract flags. Runs only when a server
 * jar is given ({@code -Pcasino.paperServerJar=<server>/versions/26.3/paper-26.3.jar}); do that after a Paper update.
 */
class PaperStubsTest {

    private static final String[] CLASSES = {"io/papermc/paper/network/ChannelInitializeListener",
        "io/papermc/paper/network/ChannelInitializeListenerHolder"};

    @Test
    void stubsHaveTheSignaturesOfTheServerJar() throws IOException {
        String given = System.getProperty("kaboofcasino.paperServerJar", "");
        assumeTrue(!given.isEmpty(), "no Paper server jar given (-Pcasino.paperServerJar=...)");
        Path jar = Path.of(given);
        assumeTrue(Files.isRegularFile(jar), "no Paper server jar at " + jar);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String name : CLASSES) {
                ZipEntry entry = zip.getEntry(name + ".class");
                assertNotNull(entry, name + " is not in " + jar);
                ClassModel real;
                try (InputStream in = zip.getInputStream(entry)) {
                    real = ClassFile.of().parse(in.readAllBytes());
                }
                ClassModel stub = stub(name);
                assertEquals(real.flags().has(AccessFlag.INTERFACE), stub.flags().has(AccessFlag.INTERFACE), name);
                Map<String, String> realMethods = publicMethods(real);
                Map<String, String> stubMethods = publicMethods(stub);
                assertFalse(stubMethods.isEmpty(), name);
                for (Map.Entry<String, String> m : stubMethods.entrySet()) {
                    assertEquals(realMethods.get(m.getKey()), m.getValue(), name + "." + m.getKey());
                }
            }
        }
    }

    /** The stubs are on the test classpath (the plugin jar never contains them: compileOnly). */
    @Test
    void stubsAreOnTheTestClasspath() throws IOException {
        for (String name : CLASSES) {
            assertFalse(publicMethods(stub(name)).isEmpty(), name);
        }
    }

    private static ClassModel stub(String name) throws IOException {
        try (InputStream in = PaperStubsTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(in, "stub " + name + " is not on the test classpath");
            return ClassFile.of().parse(in.readAllBytes());
        }
    }

    /** {@code name + descriptor} -> "static"/"abstract" flags of the public methods (no constructors). */
    private static Map<String, String> publicMethods(ClassModel c) {
        Map<String, String> out = new TreeMap<>();
        for (MethodModel m : c.methods()) {
            String name = m.methodName().stringValue();
            if (!m.flags().has(AccessFlag.PUBLIC) || name.startsWith("<")) {
                continue;
            }
            out.put(name + m.methodType().stringValue(), (m.flags().has(AccessFlag.STATIC) ? "static " : "")
                    + (m.flags().has(AccessFlag.ABSTRACT) ? "abstract" : "concrete"));
        }
        return out;
    }
}
