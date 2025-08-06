import java.net.*;
import java.io.*;
import java.util.*;

public class SMTPServer {
    private static final String DOMAIN = "piplo.com";
    private static final int PORT = 25;
    private ServerSocket serverSocket;

    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("SMTP Server started on port " + PORT);
            
            while (true) {
                /*
                * SessionHandler implements Runnable to enable multi-client support
                * - The run() method contains all client interaction logic
                * SO Each instance handles exactly one client connection
                */
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                new Thread(new SessionHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }


    /* 
    * serverSocket.accept() waits for and accepts an incoming client connection.
    * Once a client connects, it returns a new Socket object for communication.
    * This allows the server to handle multiple clients concurrently (each in a separate thread).
    */

    static class SessionHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String sender;
        private String recipient;
        private StringBuilder emailData;
        private boolean dataMode;

        public SessionHandler(Socket socket) {
            this.clientSocket = socket;
            this.emailData = new StringBuilder();
            this.dataMode = false;
        }

        private void sendResponse(int code, String message) {
            String response = code + " " + message;
            out.println(response);
            System.out.println("S: " + response);
        }

        private String extractEmail(String line) {
            int start = line.indexOf('<');
            int end = line.indexOf('>');
            if (start != -1 && end != -1 && end > start) {
                return line.substring(start + 1, end).trim();
            }
            return null;
        }

        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                
                sendResponse(220, DOMAIN + " SMTP ready");
                String inputLine;
                
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("C: " + inputLine);
                    
                    if (dataMode) {
                        if (inputLine.equals(".")) {
                            dataMode = false;
                            System.out.println("Email received from " + sender + " to " + recipient);
                            System.out.println("Content:\n" + emailData.toString());
                            sendResponse(250, "Message accepted for delivery");
                            emailData.setLength(0); 
                        } else {
                            if (inputLine.startsWith(".")) {
                                inputLine = inputLine.substring(1);
                            }
                            emailData.append(inputLine).append("\n");
                        }
                    } else {
                        String upperInput = inputLine.toUpperCase();
                        if (upperInput.startsWith("HELO")) {
                            sendResponse(250, DOMAIN + " Hello");
                        } else if (upperInput.startsWith("MAIL FROM:")) {
                            sender = extractEmail(inputLine);
                            if (sender != null) {
                                sendResponse(250, "Sender OK");
                            } else {
                                sendResponse(501, "Syntax error in MAIL FROM");
                            }
                        } else if (upperInput.startsWith("RCPT TO:")) {
                            recipient = extractEmail(inputLine); 
                            if (recipient != null) {
                                sendResponse(250, "Recipient OK");
                            } else {
                                sendResponse(501, "Syntax error in RCPT TO");
                            }
                        } else if (upperInput.equals("DATA")) {
                            if (sender == null || recipient == null) {
                                sendResponse(503, "Need MAIL FROM and RCPT TO before DATA");
                            } else {
                                dataMode = true;
                                sendResponse(354, "End data with <CR><LF>.<CR><LF>");
                            }
                        } else if (upperInput.equals("QUIT")) {
                            sendResponse(221, DOMAIN + " closing connection");
                            break;
                        } else {
                            sendResponse(502, "Command not implemented");
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error with client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
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
