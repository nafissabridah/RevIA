public class Autorisation {
    public boolean estAdmin(Utilisateur u) {
        String role = u.getRole();
        if (role == "ADMIN") {
            return true;
        }
        return false;
    }
}
