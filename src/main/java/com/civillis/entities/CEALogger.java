package com.civillis.entities;

import org.slf4j.Logger;

/**
 * Utility class for controlled logging based on config settings
 */
public class CEALogger {
    
    /**
     * Log an info message only if verbose logging is enabled
     */
    public static void info(Logger logger, String message, Object... args) {
        if (Config.ENABLE_VERBOSE_LOGGING.getAsBoolean()) {
            logger.info(message, args);
        }
    }
    
    /**
     * Log a debug message only if verbose logging is enabled
     */
    public static void debug(Logger logger, String message, Object... args) {
        if (Config.ENABLE_VERBOSE_LOGGING.getAsBoolean()) {
            logger.debug(message, args);
        }
    }
    
    /**
     * Log a warning message (always logged regardless of verbose setting)
     */
    public static void warn(Logger logger, String message, Object... args) {
        logger.warn(message, args);
    }
    
    /**
     * Log an error message (always logged regardless of verbose setting)
     */
    public static void error(Logger logger, String message, Object... args) {
        logger.error(message, args);
    }
}
