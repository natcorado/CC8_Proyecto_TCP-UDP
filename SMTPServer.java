import java.net.*;
import java.io.*;
import java.util.*;

public class SMTPServer {
    private static final String DOMAIN = "npc.com";
    private static final int PORT = 25;
    private static final int UDP_PORT = 345;

    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("SMTP Server started on port " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                new Thread(new SessionHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }

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
                            String rawEmail = emailData.toString();
                            
                            String recipientDomain = recipient.substring(recipient.indexOf('@') + 1);

                            if (recipientDomain.equalsIgnoreCase(DOMAIN)) {
                                // Local delivery
                                String headers;
                                String body;
                                int headerEndIndex = rawEmail.indexOf("\n\n");
                                if (headerEndIndex != -1) {
                                    headers = rawEmail.substring(0, headerEndIndex);
                                    body = rawEmail.substring(headerEndIndex + 2);
                                } else {
                                    headers = rawEmail;
                                    body = "";
                                }

                                String messageId = extractMessageId(headers);
                                if (messageId == null) {
                                    // Generate a fallback message ID if none is found
                                    messageId = UUID.randomUUID().toString() + "@" + DOMAIN;
                                    System.err.println("Warning: No Message-ID found. Generated a new one: " + messageId);
                                }

                                EmailDatabase.getInstance().saveEmail(messageId, sender, recipient, headers, body);
                                System.out.println("Email from " + sender + " to " + recipient + " saved to local database.");
                            } else {
                                // Forward via UDP
                                System.out.println("Forwarding email to external domain " + recipientDomain + " via UDP.");
                                sendUdpBroadcast(sender, recipient, rawEmail);
                            }
                            
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
                        if (upperInput.startsWith("HELO") || upperInput.startsWith("EHLO")) {
                            sendResponse(250, DOMAIN + " Hello");
                        } else if (upperInput.startsWith("MAIL FROM:")) {
                            sender = extractEmail(inputLine);
                            if (sender != null) {
                                sendResponse(250, "OK");
                            } else {
                                sendResponse(501, "Syntax error in MAIL FROM");
                            }
                        } else if (upperInput.startsWith("RCPT TO:")) {
                            recipient = extractEmail(inputLine); 
                            if (recipient != null) {
                                sendResponse(250, "OK");
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

        private String extractMessageId(String headers) {
            try (BufferedReader reader = new BufferedReader(new StringReader(headers))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toUpperCase().startsWith("MESSAGE-ID:")) {
                        return line.substring(line.indexOf(':') + 1).trim();
                    }
                }
            } catch (IOException e) {
                // Should not happen with StringReader
            }
            return null;
        }

        private void sendUdpBroadcast(String from, String to, String data) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                String message = "MAIL FROM:<" + from + ">\n" + 
                                 "RCPT TO:<" + to + ">\n" + 
                                 "DATA\n" + 
                                 data;
                byte[] buffer = message.getBytes();
                
                InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, UDP_PORT);
                
                socket.send(packet);
                System.out.println("UDP broadcast sent.");
            } catch (IOException e) {
                System.err.println("Error sending UDP broadcast: " + e.getMessage());
            }
        }
    }
}
