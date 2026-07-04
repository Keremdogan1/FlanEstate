import io.github.flemmli97.flan.claim.Claim;
public class Test {
    public static void main(String[] args) throws Exception {
        for (java.lang.reflect.Method m : Class.forName("io.github.flemmli97.flan.claim.Claim").getMethods()) {
            System.out.println(m.getName() + " -> " + m.getReturnType().getName());
        }
    }
}
