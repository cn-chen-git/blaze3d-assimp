package top.fifthlight.fabazel.devlaunchwrapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
public class DevLaunchWrapper {
    private static final String version = System.getProperty("dev.launch.version", null);
    private static final String type = System.getProperty("dev.launch.type", null);
    private static final String assetsPath = System.getProperty("dev.launch.assetsPath", null);
    private static final String mainClass = System.getProperty("dev.launch.mainClass", null);
    private static final String glfwLibName = System.getenv("GLFW_LIBNAME");
    private static final String copyFiles = System.getProperty("dev.launch.copyFiles", null);
    public static void main(String[] args) throws ReflectiveOperationException, IOException {
        if (copyFiles != null) {
            for (var entry : copyFiles.split(",")) {
                var i = entry.indexOf(':');
                if (i == -1) throw new IllegalArgumentException("Invalid copy file entry: " + entry);
                var from = Path.of(entry.substring(0, i));
                var to = Path.of(entry.substring(i + 1));
                Files.createDirectories(to.getParent());
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        var argsList = new ArrayList<>(Arrays.asList(args));
        if (glfwLibName != null) System.setProperty("org.lwjgl.glfw.libname", glfwLibName);
        if (assetsPath != null) {
            var path = Path.of(assetsPath).toRealPath();
            if (Files.isRegularFile(path)) path = path.getParent().getParent();
            argsList.add("--assetsDir");
            argsList.add(path.toString());
            var assetIndex = System.getProperty("dev.launch.assetIndex", null);
            if (assetIndex != null) {
                argsList.add("--assetIndex");
                argsList.add(assetIndex);
            } else if (version != null) {
                argsList.add("--assetIndex");
                argsList.add(Files.readString(path.resolve(Path.of("versions", version))));
            }
        }
        switch (type) {
            case "client" -> Files.writeString(Path.of("allowed_symlinks.txt"), "[regex].*\n");
            case "server" -> {
                if (!Files.exists(Path.of("server.properties")))
                    Files.writeString(Path.of("server.properties"), "online-mode=false\n");
                argsList.add("--nogui");
                Files.writeString(Path.of("eula.txt"), "eula=true\n");
            }
        }
        if (mainClass == null)
            throw new IllegalArgumentException("No main class specified. Specify your real main class with dev.launch.mainClass JVM property.");
        var clazz = ClassLoader.getSystemClassLoader().loadClass(mainClass);
        clazz.getMethod("main", String[].class).invoke(null, (Object) argsList.toArray(new String[0]));
    }
}