package io.strata.core.util;

import io.strata.core.config.StrataCoreConfig;
import io.strata.core.config.StrataConfigHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StrataLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("Strata");

    private StrataLogger() {}

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    public static void debug(String message, Object... args) {
        if (StrataConfigHelper.get(StrataCoreConfig.class).verboseLogging) {
            LOGGER.info("[DEBUG] " + message, args);
        }
    }
}
