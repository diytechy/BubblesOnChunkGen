import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
public class CheckMethods {
    public static void main(String[] args) throws Exception {
        File file = new File("C:/Projects/BubblesOnChunkGen/terra-addon/build/tmp/.cache/expanded/zip_e6f888da48a1b8db50aa1b7bb7536ca2");
        URL url = file.toURI().toURL();
        URLClassLoader cl = new URLClassLoader(new URL[]{url});
        Class<?> clazz = cl.loadClass("net.minecraft.world.entity.player.Player");
        for (Method m : clazz.getMethods()) {
            if (m.getName().toLowerCase().contains("perm") || m.getName().toLowerCase().contains("op")) {
                System.out.println("Player." + m.getName());
            }
        }
        Class<?> entityClazz = cl.loadClass("net.minecraft.world.entity.Entity");
        for (Method m : entityClazz.getMethods()) {
            if (m.getName().toLowerCase().contains("perm") || m.getName().toLowerCase().contains("op")) {
                System.out.println("Entity." + m.getName());
            }
        }
    }
}
