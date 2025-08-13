import socket

# Target broadcast address and port
BROADCAST_IP = "255.255.255.255"
PORT = 345

# The message to send
message = (
    b"MAIL FROM:<test@npc.com>\n"
    b"RCPT TO:<prueba@npc.com>\n"
    b"DATA\n"
    b"Subject: UDP Test\n\n"
    b"This is a test email sent via UDP."
)

# Create a UDP socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

# Enable broadcasting mode
s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

try:
    # Send the message to the broadcast address
    s.sendto(message, (BROADCAST_IP, PORT))
    print(f"Successfully sent broadcast packet to {BROADCAST_IP}:{PORT}")
except Exception as e:
    print(f"Error sending broadcast packet: {e}")
finally:
    # Close the socket
    s.close()
