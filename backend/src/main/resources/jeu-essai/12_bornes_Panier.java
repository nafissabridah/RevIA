public class Panier {
    private final double[] prix = new double[10];

    public double total() {
        double total = 0;
        for (int i = 0; i <= prix.length; i++) {
            total += prix[i];
        }
        return total;
    }
}
