import java.net.*;
import java.io.*;
import java.sql.*;

//1. Lab 1 conectarlo a thunderbird 

public class SMTPServer {
    private static final String DOMAIN = "piplo.com";
    private static final int PORT = 25;
    private ServerSocket serverSocket; 

    private static void startServer(){
        try{
            serverSocket = new ServerSocket(PORT);
            while(true){
                //serverSocket.accept() waits for and accepts an incoming client connection.
                //Once a client connects,it returns a new Socket object for comm. 
                //This allows the server to handle multiple clients concurrently (each in a separate thread).

                Socket clientSocket = serverSocket.accept();
                new Thread(new SessionHandler(client)).start();
            }
            
        }catch (IOException e) {
            System.err.println("Failed to start a new Socket: " + e.getMessage());
        }
    }

    private static void handleClient inplements Runnable{
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String sender;
        private String recipient;
        private StringBuilder emailData;

        public SessionHandler(Socket socket) {
            this.clientSocket = socket;
        }  

        @Override
        public void run() {
            try{
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                sendResponse(220, DOMAIN + " SMTP ready");
                String inputLine; 
                
                while((inputLine = in.readLine()) != null){

                }
            }catch(IOException e){

            }
        }  
    }

}
/*import java.net.*;
import java.io.*;

public class SMTPServer {
    public static void main(String[] args) {

        try (ServerSocket serverSocket = new ServerSocket(25)) {
            System.out.println("SMTP server is running on port 25...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
                );

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Received: " + inputLine);
                    if (inputLine.equalsIgnoreCase("quit")) break;
                }

                System.out.println("give me a bottle of rum!");
                in.close();
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Give me a bottle of rum!");

    }
}*/
