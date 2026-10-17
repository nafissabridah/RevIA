public class RapportDao {
    public int compterLignes(DataSource ds) throws SQLException {
        Connection conn = ds.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT COUNT(id) FROM rapport");
        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getInt(1);
    }
}
