package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.model.ErrorLog;
import fr.eseo.vsquare.model.User;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Simple logger for this application.
 * 
 * @author Clement Gouin
 */
public final class Logger {

	private static java.util.logging.Logger appLogger = java.util.logging.Logger.getLogger("VSquare");

	private Logger() {
	}

	/**
	 * Change the log level of this logger.
	 * 
	 * @param newLevel the level of log to show
	 */
	public static void setLevel(Level newLevel) {
		appLogger.setLevel(newLevel);
	}
	
	/**
	 * @return the current logger level;
	 */
	public static Level getLevel() {
		return appLogger.getLevel();
	}

	/**
	 * Load a config file for the logger and set locale to english.
	 * 
	 * @param relativePath the path in resources of the config file
	 */
	public static void init(String relativePath) {
		init(relativePath, Level.INFO);
	}
	
	/**
	 * Load a config file for the logger and set locale to english.
	 * 
	 * @param relativePath the path in resources of the config file
	 * @param level the level to set the logger to
	 */
	public static void init(String relativePath, Level level) {
		Locale.setDefault(Locale.ENGLISH);
		loadConfigFromFile(relativePath);
		Logger.setLevel(level);
	}
	
	/**
	 * Load a config file for the logger.
	 * 
	 * @param relativePath the path in resources of the config file
	 */
    private static void loadConfigFromFile(String relativePath) {
		try {
			InputStream is = Logger.class.getClassLoader().getResourceAsStream(relativePath);
			if(is == null) {
				Logger.log(Level.SEVERE, "Logger config file not found at path {0}", relativePath);
				return;
			}
			LogManager.getLogManager().readConfiguration(is);
			appLogger = java.util.logging.Logger.getLogger("VSquare");
		} catch (IOException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
	}

	/**
	 * Log a message.
	 * 
	 * @param lvl
	 *            the level of logging
	 * @param message
	 *            the message
	 * @param objects
	 *            the object for the message formatting
	 */
	public static void log(Level lvl, String message, Object... objects) {
		Logger.log(Utils.getCallingClassName(3), lvl, message, objects);
	}

	/**
	 * Log a message.
	 * 
	 * @param source
	 *            the source class name
	 * @param lvl
	 *            the level of logging
	 * @param message
	 *            the message
	 * @param objects
	 *            the object for the message formatting
	 */
	public static void log(String source, Level lvl, String message, Object... objects) {
		Logger.log(source, lvl, true, message, objects);
	}
	
	private static void log(String source, Level lvl, boolean errorLog, String message, Object... objects) {
		message = String.format("[VSquare-%s] %s", source, message);
		appLogger.log(lvl, message, objects);
		if(lvl == Level.SEVERE) {
            if (objects.length > 0 && objects[0] instanceof Exception) {
				Exception e = (Exception) objects[0];
				StringBuilder stackTrace = new StringBuilder(message);
				for(StackTraceElement ste: e.getStackTrace()) {
					stackTrace.append('\n');
					stackTrace.append(ste.toString());
					Logger.log(source, Level.SEVERE, false, "\t {0}", ste);
				}
				errorLog(1, stackTrace.toString(), objects);
			}else if(errorLog){
				errorLog(0, message, objects);
			}
		}
	}
	
	private static void errorLog(int index, String message, Object[] objects) {
		if(DatabaseManager.isInitialized()) {
            User u = ServletUtils.getCurrentUser();
            if (u == null && objects.length > index && objects[index] instanceof User)
				u = (User)objects[index];
            ErrorLog.log(u, message, ServletUtils.getCurrentRequest());
		}
	}
}
