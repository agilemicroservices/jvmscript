 import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import static ch.qos.logback.classic.Level.INFO
import static ch.qos.logback.classic.Level.DEBUG
import static ch.qos.logback.classic.Level.ERROR

appender("CONSOLE", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} %-5level Job ID<%X{jobid}> %logger{36} - %msg%n"
    }
}

appender("APPFILE", FileAppender) {
    file = "log/script.log"
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} %-5level Job ID<%X{jobid}> %logger{36} - %msg%n"
    }
}

root(INFO, ["APPFILE", "CONSOLE"])