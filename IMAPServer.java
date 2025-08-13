import java.net.*;
import java.io.*;
import java.util.*;

public class IMAPServer {
    private static final int PORT = 143;

    public static void main(String[] args) {
        // Create a default user for testing purposes
        EmailDatabase.getInstance().createUser("christian@martinez.com", "123");
        startServer();
    }

    private static void startServer() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("IMAP Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New IMAP client connected: " + clientSocket.getInetAddress());
                new Thread(new IMAPSessionHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Failed to start IMAP server: " + e.getMessage());
        }
    }

    static class IMAPSessionHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean authenticated = false;
        private String loggedInUser = null;
        private List<Long> messageIds = new ArrayList<>();

        public IMAPSessionHandler(Socket socket) {
            this.clientSocket = socket;
        }

        private void send(String response) {
            out.println(response);
            System.out.println("S: " + response);
        }

        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                send("* OK IMAP server ready");

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("C: " + inputLine);
                    if (inputLine.trim().isEmpty()) {
                        continue;
                    }
                    String[] parts = inputLine.split(" ");
                    String tag = parts[0];
                    String command = parts[1].toUpperCase();

                    if (command.equals("CAPABILITY")) {
                        send("* CAPABILITY IMAP4rev1");
                        send(tag + " OK CAPABILITY completed");
                    } else if (command.equals("LOGIN")) {
                        if (parts.length < 4) {
                            send(tag + " BAD Invalid LOGIN command");
                            continue;
                        }
                        String username = parts[2].replaceAll("\"", "");
                        String password = parts[3].replaceAll("\"", "");

                        if (EmailDatabase.getInstance().authenticateUser(username, password)) {
                            authenticated = true;
                            loggedInUser = username;
                            send(tag + " OK LOGIN successful");
                        } else {
                            send(tag + " NO LOGIN failed");
                        }
                    } else if (command.equals("LOGOUT")) {
                        send("* BYE IMAP server logging out");
                        send(tag + " OK LOGOUT successful");
                        break;
                    } else if (authenticated) {
                        if (command.equals("LIST") || command.equals("LSUB") || command.equals("SUBSCRIBE")) {
                            send("* LIST (\\HasNoChildren) \"/\" \"INBOX\"");
                            send(tag + " OK " + command + " completed");
                        } else if (command.equals("CREATE")) {
                            send(tag + " OK CREATE completed");
                        } else if (command.equals("SELECT")) {
                            if (parts.length < 3 || !parts[2].replaceAll("\"", "").equalsIgnoreCase("INBOX")) {
                                send(tag + " NO No such mailbox");
                                continue;
                            }
                            messageIds = EmailDatabase.getInstance().getEmailIdsForUser(loggedInUser);
                            int emailCount = messageIds.size();
                            send("* " + emailCount + " EXISTS");
                            send("* 0 RECENT");
                            send("* FLAGS (\\Seen)");
                            send(tag + " OK [READ-WRITE] SELECT completed");
                        } else if (command.equals("FETCH") || command.equals("UID")) {
                            handleFetch(tag, inputLine);
                        } else if (command.equals("APPEND")) {
                            handleAppend(tag, inputLine);
                        } else if (command.equals("NOOP")) {
                            send(tag + " OK NOOP completed");
                        } else {
                            send(tag + " BAD Command not implemented");
                        }
                    } else {
                        send(tag + " BAD Please login first");
                    }
                }

            } catch (IOException e) {
                System.err.println("Error with IMAP client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing IMAP socket: " + e.getMessage());
                }
            }
        }

        private void handleFetch(String tag, String rawCommand) {
            try {
                String[] cmdParts = rawCommand.split(" ", 3);
                boolean isUidFetch = cmdParts.length > 1 && cmdParts[1].equalsIgnoreCase("UID");
                String upperCmd = rawCommand.toUpperCase();
                
                int fetchIndex = upperCmd.indexOf("FETCH");
                if (fetchIndex == -1) {
                    send(tag + " BAD Malformed FETCH command");
                    return;
                }
                String commandBody = rawCommand.substring(fetchIndex + 5).trim();
                
                int openParenIndex = commandBody.indexOf('(');
                int closeParenIndex = commandBody.lastIndexOf(')');
                if (openParenIndex == -1 || closeParenIndex == -1 || openParenIndex > closeParenIndex) {
                    send(tag + " BAD Malformed FETCH command: Missing parentheses");
                    return;
                }

                String messageSet = commandBody.substring(0, openParenIndex).trim();
                String fetchAttributes = commandBody.substring(openParenIndex + 1, closeParenIndex).toUpperCase();
                
                if (messageIds == null) {
                    send(tag + " NO Mailbox not selected");
                    return;
                }
                
                List<Long> numbersToFetch = parseMessageSet(messageSet);

                for (long number : numbersToFetch) {
                    long dbId;
                    int sequenceNum;

                    if (isUidFetch) {
                        dbId = number;
                        int index = messageIds.indexOf(dbId);
                        if (index == -1) continue;
                        sequenceNum = index + 1;
                    } else {
                        sequenceNum = (int) number;
                        if (sequenceNum <= 0 || sequenceNum > messageIds.size()) continue;
                        dbId = messageIds.get(sequenceNum - 1);
                    }
                    
                    Map<String, String> email = EmailDatabase.getInstance().getEmail(dbId);
                    if (email.isEmpty()) continue;

                    String headers = email.get("headers") != null ? email.get("headers") : "";
                    String body = email.get("body") != null ? email.get("body") : "";
                    String fullEmail = headers + "\r\n\r\n" + body;
                    int emailSize = fullEmail.length();

                    StringBuilder response = new StringBuilder();
                    response.append("* ").append(sequenceNum).append(" FETCH (");

                    List<String> responseParts = new ArrayList<>();
                    
                    // Per RFC 3501, UID MUST be sent in a UID FETCH response.
                    if (isUidFetch) {
                        responseParts.add("UID " + dbId);
                    }

                    if (fetchAttributes.contains("UID") && !isUidFetch) {
                        responseParts.add("UID " + dbId);
                    }
                    if (fetchAttributes.contains("FLAGS")) {
                        responseParts.add("FLAGS (\\Seen)");
                    }
                    if (fetchAttributes.contains("RFC822.SIZE")) {
                        responseParts.add("RFC822.SIZE " + emailSize);
                    }

                    response.append(String.join(" ", responseParts));

                    String literalPart = null;
                    String literalContent = null;
                    
                    if (fetchAttributes.contains("HEADER.FIELDS") || fetchAttributes.contains("BODY.PEEK[HEADER]") || fetchAttributes.contains("BODY[HEADER]")) {
                        literalPart = "BODY[HEADER]";
                        literalContent = headers;
                    } else if (fetchAttributes.contains("BODY.PEEK[TEXT]") || fetchAttributes.contains("BODY[TEXT]")) {
                        literalPart = "BODY[TEXT]";
                        literalContent = body;
                    } else if (fetchAttributes.contains("BODY[]") || java.util.Arrays.asList(fetchAttributes.split(" ")).contains("RFC822")) {
                        literalPart = "BODY[]";
                        literalContent = fullEmail;
                    }

                    if (literalPart != null) {
                        if (!responseParts.isEmpty()) {
                            response.append(" ");
                        }
                        response.append(literalPart).append(" {").append(literalContent.length()).append("}");
                        send(response.toString());
                        send(literalContent);
                        send(")");
                    } else {
                        response.append(")");
                        send(response.toString());
                    }
                }
                send(tag + " OK FETCH completed");
            } catch (Exception e) {
                System.err.println("Error during FETCH: " + e.getMessage());
                e.printStackTrace();
                send(tag + " BAD Error during FETCH");
            }
        }

        private List<Long> parseMessageSet(String set) {
            List<Long> messageNumbers = new ArrayList<>();
            String[] parts = set.split(",");
            for (String part : parts) {
                try {
                    if (part.contains(":")) {
                        String[] range = part.split(":");
                        long start = range[0].equals("*") ? (messageIds.isEmpty() ? 0 : messageIds.get(messageIds.size() - 1)) : Long.parseLong(range[0]);
                        long end = range[1].equals("*") ? (messageIds.isEmpty() ? 0 : messageIds.get(messageIds.size() - 1)) : Long.parseLong(range[1]);
                        for (long i = start; i <= end; i++) {
                            messageNumbers.add(i);
                        }
                    } else {
                        long num = part.equals("*") ? (messageIds.isEmpty() ? 0 : messageIds.get(messageIds.size() - 1)) : Long.parseLong(part);
                        if (num > 0) messageNumbers.add(num);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Could not parse message number: " + part);
                }
            }
            return messageNumbers;
        }

        private void handleAppend(String tag, String rawCommand) throws IOException {
            try {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{(\\d+)\\}").matcher(rawCommand);
                if (!matcher.find()) {
                    send(tag + " BAD APPEND command requires a literal size like {123}");
                    return;
                }
                int size = Integer.parseInt(matcher.group(1));

                send("+ Ready for literal data");

                char[] buffer = new char[size];
                int bytesRead = 0;
                while (bytesRead < size) {
                    int result = in.read(buffer, bytesRead, size - bytesRead);
                    if (result == -1) {
                        send(tag + " BAD Client closed connection during literal transfer");
                        return;
                    }
                    bytesRead += result;
                }

                String emailData = new String(buffer);

                String headers;
                String body;
                int headerEndIndex = emailData.indexOf("\r\n\r\n");
                if (headerEndIndex != -1) {
                    headers = emailData.substring(0, headerEndIndex);
                    body = emailData.substring(headerEndIndex + 4);
                } else {
                    headers = emailData;
                    body = "";
                }

                String recipient = "";
                String[] headerLines = headers.split("\r\n");
                for (String line : headerLines) {
                    if (line.toUpperCase().startsWith("TO:")) {
                        int start = line.indexOf('<');
                        int end = line.indexOf('>');
                        if (start != -1 && end != -1 && end > start) {
                            recipient = line.substring(start + 1, end).trim();
                        } else {
                            recipient = line.substring(3).trim();
                        }
                        break;
                    }
                }

                if (recipient.isEmpty()) {
                    send(tag + " NOAPPEND failed: Could not determine recipient from headers.");
                    return;
                }

                String messageId = extractMessageId(headers);
                if (messageId == null) {
                    messageId = UUID.randomUUID().toString() + "@" + loggedInUser.substring(loggedInUser.indexOf('@') + 1);
                    System.err.println("Warning: No Message-ID found in APPEND. Generated a new one: " + messageId);
                }

                EmailDatabase.getInstance().saveEmail(messageId, loggedInUser, recipient, headers, body);
                send(tag + " OK APPEND completed");
            } catch (NumberFormatException e) {
                send(tag + " BAD Invalid literal size in APPEND command.");
            } catch (IOException e) {
                System.err.println("IOException in handleAppend: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                System.err.println("Error during APPEND: " + e.getMessage());
                e.printStackTrace();
                send(tag + " BAD Error during APPEND");
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
    }
}
