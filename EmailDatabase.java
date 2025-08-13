import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmailDatabase {

    private static final String DB_URL = "jdbc:sqlite:SMTP_SERVER.db";
    private static EmailDatabase instance;

    private EmailDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found.");
            e.printStackTrace();
        }
        createTables();
    }

    public static synchronized EmailDatabase getInstance() {
        if (instance == null) {
            instance = new EmailDatabase();
        }
        return instance;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void createTables() {
        String emailTableSql = "CREATE TABLE IF NOT EXISTS SMTP_DB ( " +
                             " IDmail    INTEGER PRIMARY KEY AUTOINCREMENT," +
                             " MessageID TEXT    NOT NULL UNIQUE," +
                             " MAIL_FROM TEXT    NOT NULL, " + 
                             " RCPT_TO   TEXT    NOT NULL, " + 
                             " HEADERS   TEXT, " + 
                             " BODY      TEXT, " + 
                             " DATE      DATETIME  default current_timestamp )";
        
        String userTableSql = "CREATE TABLE IF NOT EXISTS USERS ( " +
                            " IDuser    INTEGER PRIMARY KEY AUTOINCREMENT," +
                            " username  TEXT    NOT NULL UNIQUE," +
                            " password  TEXT    NOT NULL )";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(emailTableSql);
            stmt.execute(userTableSql);
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
        }
    }

    public synchronized void saveEmail(String messageId, String from, String to, String headers, String body) {
        String sql = "INSERT INTO SMTP_DB(MessageID, MAIL_FROM, RCPT_TO, HEADERS, BODY) VALUES(?,?,?,?,?);";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, messageId);
            pstmt.setString(2, from);
            pstmt.setString(3, to);
            pstmt.setString(4, headers);
            pstmt.setString(5, body);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 19) { // UNIQUE constraint failed
                System.err.println("Attempted to save a duplicate email. Message-ID: " + messageId);
            } else {
                System.err.println("Error saving email: " + e.getMessage());
            }
        }
    }

    public synchronized boolean createUser(String username, String password) {
        String sql = "INSERT INTO USERS(username, password) VALUES(?,?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            System.out.println("User " + username + " created.");
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 19) { 
                System.err.println("Error creating user: " + username + " already exists.");
            } else {
                System.err.println("Error creating user: " + e.getMessage());
            }
            return false;
        }
    }

    public synchronized boolean authenticateUser(String username, String password) {
        String sql = "SELECT password FROM USERS WHERE username = ?;";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                return storedPassword.equals(password);
            }
            return false; // User not found
        } catch (SQLException e) {
            System.err.println("Error authenticating user: " + e.getMessage());
            return false;
        }
    }

    public synchronized int getEmailCountForUser(String username) {
        String sql = "SELECT COUNT(*) AS count FROM SMTP_DB WHERE RCPT_TO = ?;";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("count");
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error getting email count: " + e.getMessage());
            return 0;
        }
    }

    public synchronized List<Long> getEmailIdsForUser(String username) {
        List<Long> emailIds = new ArrayList<>();
        String sql = "SELECT IDmail FROM SMTP_DB WHERE RCPT_TO = ? ORDER BY DATE ASC;";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                emailIds.add(rs.getLong("IDmail"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting email IDs: " + e.getMessage());
        }
        return emailIds;
    }

    public synchronized Map<String, String> getEmail(long mailId) {
        Map<String, String> email = new HashMap<>();
        String sql = "SELECT HEADERS, BODY FROM SMTP_DB WHERE IDmail = ?;";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, mailId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                email.put("headers", rs.getString("HEADERS"));
                email.put("body", rs.getString("BODY"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting email: " + e.getMessage());
        }
        return email;
    }
}
