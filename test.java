public class Test {
    public static void main(String[] args) throws Exception {
        System.out.println("Fields of RenderSystem:");
        for (java.lang.reflect.Field f : com.mojang.blaze3d.systems.RenderSystem.class.getDeclaredFields()) {
            if (f.getName().toLowerCase().contains("shader")) {
                System.out.println(f.getName() + " " + f.getType().getName());
            }
        }
    }
}
