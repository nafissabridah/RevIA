public class ImportCsv {
    public List<String> lire(String chemin) throws IOException {
        BufferedReader lecteur = new BufferedReader(new FileReader(chemin));
        List<String> lignes = new ArrayList<>();
        String ligne;
        while ((ligne = lecteur.readLine()) != null) {
            lignes.add(ligne);
        }
        return lignes;
    }
}
