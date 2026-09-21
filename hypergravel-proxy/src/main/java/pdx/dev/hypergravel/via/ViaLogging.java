package pdx.dev.hypergravel.via;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.apache.logging.log4j.LogManager;

final class ViaLogging {

    private ViaLogging() {
    }

    static java.util.logging.Logger create(String name) {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
        logger.setUseParentHandlers(false);
        for (Handler existing : logger.getHandlers()) {
            logger.removeHandler(existing);
        }
        logger.addHandler(new Log4jHandler(name));

        logger.setLevel(Level.ALL);
        return logger;
    }

    private static final class Log4jHandler extends Handler {

        private final org.apache.logging.log4j.Logger delegate;

        Log4jHandler(String name) {
            this.delegate = LogManager.getLogger(name);
        }

        @Override
        public void publish(LogRecord record) {
            if (record == null) {
                return;
            }
            String message = record.getMessage();
            Object[] parameters = record.getParameters();
            if (message != null && parameters != null && parameters.length > 0) {
                message = java.text.MessageFormat.format(message, parameters);
            }

            int level = record.getLevel().intValue();
            if (level >= Level.SEVERE.intValue()) {
                delegate.error(message, record.getThrown());
            } else if (level >= Level.WARNING.intValue()) {
                delegate.warn(message, record.getThrown());
            } else if (level >= Level.INFO.intValue()) {
                delegate.info(message, record.getThrown());
            } else {
                delegate.debug(message, record.getThrown());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
