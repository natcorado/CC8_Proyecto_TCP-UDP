import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UDPServer {
    private static final int UDP_PORT = 345;
    private static final String LOCAL_DOMAIN = "npc.com";

    public static void main(String[] args) {
        listen();
    }

    public static void listen() {
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            System.out.println("UDP Server listening on port " + UDP_PORT);
            byte[] buffer = new byte[1500]; // Max UDP packet size

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("UDP Packet Received:");
                System.out.println(message);
                handlePacket(message);
            }
        } catch (SocketException e) {
            System.err.println("UDP Socket error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("UDP IO error: " + e.getMessage());
        }
    }

    private static void handlePacket(String message) {
        String recipient = extractField(message, "RCPT TO:");
        if (recipient == null || !recipient.endsWith("@" + LOCAL_DOMAIN)) {
            System.out.println("Ignoring packet: Not for local domain " + LOCAL_DOMAIN);
            return;
        }

        System.out.println("Packet is for local domain. Processing...");
        String sender = extractField(message, "MAIL FROM:");
        
        // Simple parsing for headers and body from the UDP message
        String dataContent = extractData(message);
        String headers = "";
        String body = "";
        int headerEndIndex = dataContent.indexOf("\r\n\r\n");
        if (headerEndIndex != -1) {
            headers = dataContent.substring(0, headerEndIndex);
            body = dataContent.substring(headerEndIndex + 4);
        } else {
            headers = dataContent; // Or handle as an error
        }

        if (sender != null) {
            String messageId = java.util.UUID.randomUUID().toString() + "@" + LOCAL_DOMAIN;
            EmailDatabase.getInstance().saveEmail(messageId, sender, recipient, headers, body);
            System.out.println("Email from " + sender + " to " + recipient + " saved via UDP.");
        }
    }

    private static String extractField(String message, String field) {
        int fieldIndex = message.toUpperCase().indexOf(field.toUpperCase());
        if (fieldIndex == -1) {
            return null;
        }
        int lineEndIndex = message.indexOf('\n', fieldIndex);
        if (lineEndIndex == -1) {
            lineEndIndex = message.length();
        }
        String line = message.substring(fieldIndex, lineEndIndex);
        int start = line.indexOf('<');
        int end = line.indexOf('>');
        if (start != -1 && end != -1 && end > start) {
            return line.substring(start + 1, end).trim();
        }
        return null;
    }

    private static String extractData(String message) {
        int dataIndex = message.toUpperCase().indexOf("DATA\n");
        if (dataIndex == -1) {
            return "";
        }
        // The rest of the message after "DATA\n" is the content
        return message.substring(dataIndex + 5);
    }
}
