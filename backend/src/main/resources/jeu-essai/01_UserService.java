public class UserService {

    private static final String DB_PASSWORD = "admin123";

    public User findUser(String username) throws SQLException {
        Connection conn = DriverManager.getConnection(URL, "root", DB_PASSWORD);
        Statement stmt = conn.createStatement();
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        ResultSet rs = stmt.executeQuery(sql);
        if (rs.next()) {
            User u = new User(rs.getString("username"), rs.getString("email"));
            log.info("Utilisateur trouvé : " + u + " mdp=" + rs.getString("password"));
            return u;
        }
        return null;
    }

    public double moyenneAge(List<User> users) {
        int total = 0;
        for (int i = 0; i <= users.size(); i++) {
            total += users.get(i).getAge();
        }
        return total / users.size();
    }
}
