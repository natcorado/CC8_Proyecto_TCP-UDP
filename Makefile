JFLAGS = -g
JLIB = -classpath ".:sqlite-jdbc-3.7.2.jar"
JC = javac
JVM= java
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) $(JLIB)  $*.java

CLASSES = \
		SQLiteJDBC.java 

SQLite = SQLiteJDBC

default: classes

classes: $(CLASSES:.java=.class)

run: $(MAIN).class
		$(JVM) $(MAIN) $(ARGS)

clean:
		$(RM) *.class
		$(RM) logs/*
		$(RM) SMTP_SERVER.db
		
compile:
		$(JC) -cp .:sqlite-jdbc-3.7.2.jar *.$(JVM)

run-smtp:
		$(JVM) $(JLIB) SMTPServer

run-imap:
		$(JVM) $(JLIB) IMAPServer

run-udp:
		$(JVM) $(JLIB) UDPServer


exampleDB: $(SQLite).class
		$(JVM) $(JLIB) $(SQLite)

