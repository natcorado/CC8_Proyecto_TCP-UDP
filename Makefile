# Compiler and JVM settings
JC = javac
JVM = java
JFLAGS = -g
LIB = sqlite-jdbc-3.7.2.jar
OUT_DIR = bin

# Classpath for compilation and execution
CP = -cp $(OUT_DIR):$(LIB)

# Find all .java files in the current directory
SOURCES = $(wildcard *.java)
# Generate .class file paths in the output directory
CLASSES = $(SOURCES:%.java=$(OUT_DIR)/%.class)

# The default target to be executed when you just run "make"
default: all

# Compile all java files
all: $(CLASSES)

$(OUT_DIR)/%.class: %.java
	@mkdir -p $(OUT_DIR)
	$(JC) $(JFLAGS) -d $(OUT_DIR) -cp $(OUT_DIR):$(LIB) $<

# --- Run Rules ---
run-smtp: all
	$(JVM) $(CP) SMTPServer

run-imap: all
	$(JVM) $(CP) IMAPServer

run-udp: all
	$(JVM) $(CP) UDPServer

# --- Cleanup ---
clean:
	@echo "Cleaning up compiled files and database..."
	@rm -rf $(OUT_DIR)
	@rm -f SMTP_SERVER.db
	@echo "Cleanup complete."

# Phony targets are not real files
.PHONY: all clean run-smtp run-imap run-udp