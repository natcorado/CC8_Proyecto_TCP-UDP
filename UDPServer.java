import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServer {
    private static final int UDP_PORT = 345;
    private static final String LOCAL_DOMAIN = "martinez.com";
    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) {
        listen();
    }

    public static void listen() {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT, InetAddress.getByName("0.0.0.0"))) {
            socket.setBroadcast(true);
            System.out.println("UDP Server listening on port " + UDP_PORT);

            while (true) {
                byte[] buffer = new byte[1500]; // Max UDP packet size
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                executor.submit(new PacketHandler(packet));
            }
        } catch (SocketException e) {
            System.err.println("UDP Socket error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("UDP IO error: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private static class PacketHandler implements Runnable {
        private final DatagramPacket packet;

        public PacketHandler(DatagramPacket packet) {
            this.packet = packet;
        }

        @Override
        public void run() {
            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("UDP Packet Received by thread " + Thread.currentThread().getName() + ":");
            System.out.println(message);
            handlePacket(message);
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

        // --- Start of new, more robust parsing ---
        String normalizedMessage = message.replace("\r\n", "\n");
        String[] lines = normalizedMessage.split("\n");

        boolean inDataSection = false;
        StringBuilder dataContentBuilder = new StringBuilder();

        for (String line : lines) {
            if (inDataSection) {
                // The SMTP data section ends with a line containing only a period.
                if (line.equals(".")) {
                    break; 
                }
                dataContentBuilder.append(line).append("\n");
            } else if (line.toUpperCase().equals("DATA")) {
                inDataSection = true;
            }
        }

        String dataContent = dataContentBuilder.toString();
        String headers = "";
        String body = "";

        int headerEndIndex = dataContent.indexOf("\n\n");

        if (headerEndIndex != -1) {
            headers = dataContent.substring(0, headerEndIndex).trim();
            body = dataContent.substring(headerEndIndex + 2).trim();
        } else {
            // If no double newline, assume the whole thing is headers.
            headers = dataContent.trim();
        }
        // --- End of new parsing ---

        if (sender != null && (!headers.isEmpty() || !body.isEmpty())) {
            String messageId = extractMessageId(headers);
            if (messageId == null) {
                messageId = java.util.UUID.randomUUID().toString() + "@" + LOCAL_DOMAIN;
            }
            
            EmailDatabase.getInstance().saveEmail(messageId, sender, recipient, headers, body);
            System.out.println("Email from " + sender + " to " + recipient + " saved via UDP.");
        } else {
            System.out.println("Could not parse email content correctly from UDP packet. Discarding.");
        }
    }

    private static String extractMessageId(String headers) {
        if (headers == null) return null;
        String[] lines = headers.split("\n");
        for (String line : lines) {
            if (line.toUpperCase().startsWith("MESSAGE-ID:")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return null;
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
}
