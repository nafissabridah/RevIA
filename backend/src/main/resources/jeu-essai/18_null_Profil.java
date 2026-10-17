public class ProfilService {
    private final Map<String, Profil> cache = new HashMap<>();

    public String ville(String identifiant) {
        Profil profil = cache.get(identifiant);
        return profil.getAdresse().getVille();
    }
}
