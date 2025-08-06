import java.sql.*;

public class EmailDatabase {
    private static final String DB_PATH = "../../SMTP_SERVER.db";
    private static EmailDatabase instance = null;
    private Connection conn;

    private EmailDatabase(){
        try{
            conn = DriverManager.getConnection(DB_PATH); 
            System.out.println("Conexion exitosa");
        }catch (SQLException e) {
            System.err.println("Error al conectar a la base de datos: " + e.getMessage());
        }    
    }

    public static synchronized EmailDatabase getInstance(){
        if(instance==null){
            instance = new EmailDatabase(); 
        }

        return instance; 
    }

    public boolean saveEmail(String mail_from, String rcpt_to, String data){
        String sql = "INSERT INTO SMTP_DB(MAIL_FROM, RCPT_TO, DATA, DATE) VALUES(?, ?, ?, datetime('now'))";

        try(PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, mail_from);
            pstmt.setString(2, rcpt_to);
            pstmt.setString(3, data);
            pstmt.executeUpdate();
            return false; 

        }catch(SQLException e){
            System.err.println("Error al guardar email: " + e.getMessage());
            return true;     
                
        }
    }

}
