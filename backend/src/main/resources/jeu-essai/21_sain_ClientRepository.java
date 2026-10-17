public class ClientRepository {
    private final DataSource dataSource;

    public ClientRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<Client> parId(long id) throws SQLException {
        String sql = "SELECT id, nom, email FROM client WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Client(rs.getLong("id"), rs.getString("nom"), rs.getString("email")));
                }
                return Optional.empty();
            }
        }
    }
}
