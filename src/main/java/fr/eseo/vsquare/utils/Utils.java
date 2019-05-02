package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.model.User;
import fr.klemek.betterlists.BetterArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Utility class that store useful misc functions.
 * 
 * @author Clement Gouin
 */
public final class Utils {

	private static final ResourceBundle CONFIGURATION_BUNDLE = ResourceBundle.getBundle("Configuration");

	private static final ResourceBundle OS_CODES_BUNDLE = ResourceBundle.getBundle("os_codes");
	
	private static String localIP = null;
	
	private Utils() {
	}

	/*
	 * Configuration utils
	 */

	/**
	 * Get a configuration string by its key.
	 * 
	 * @param key
	 *            the key in the config file
	 * @return the string or null if not found
	 */
	public static String getString(String key) {
		try {
			return CONFIGURATION_BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			Logger.log(Level.SEVERE, "Missing configuration string {0}", key);
			return null;
		}
	}

	/**
	 * Get a configuration string by its key.
	 * 
	 * @param key
	 *            the key in the config file
	 * @return the integer or 0 if not found
	 */
	public static int getInt(String key) {
		try {
			String string = Utils.getString(key);
			if (string == null)
				return 0;
			return Integer.parseInt(string);
		} catch (NumberFormatException e) {
			Logger.log(Level.SEVERE, "Not integer string at key {0}", key);
			return 0;
		}
	}

	/**
	 * Get a connection string from configuration.
	 * 
	 * @param key
	 *            the key in the config file
	 * @return the string or null if not found
	 */
	public static String getVersionString(String key) {
		try {
			return ResourceBundle.getBundle("Version").getString(key);
		} catch (MissingResourceException e) {
			Logger.log(Level.SEVERE, "Missing version string {0}", key);
			return null;
		}
	}

	/**
	 * Get a string from version.
	 * 
	 * @param key
	 *            the key in the config file
	 * @return the string or null if not found
	 */
	public static String getConnectionString(String key) {
		String connectionString = Utils.getString(key);
		if (connectionString == null)
			return null;
		String localIP = getLocalIP();
		if (localIP != null)
			return connectionString.replace(localIP, "localhost");
		else
			return connectionString;
	}
	
	/**
	 * Get the corresponding os name and icon.
	 * 
	 * @param osCode the code given by vsphere
	 * @return a String array containing os name and icon file name
	 */
	public static String[] getOsInfo(String osCode) {
		try {
			return OS_CODES_BUNDLE.getString(osCode).split(";");
		} catch (MissingResourceException e) {
			Logger.log(Level.SEVERE, "Missing OS Code {0}", osCode);
			return new String[] {"Other Operating System","os_other"};
		}
	}
	
	/**
	 * Check if a user is admin in the config by its login.
	 * 
	 * @param login the login to check
	 * @return true if this login is admin in the configuration
	 */
	public static boolean isAdminByConfig(String login) {
		String admins = Utils.getString("admins");
		if(admins == null)
			return false;
		BetterArrayList<String> configAdmins = new BetterArrayList<>(Arrays.asList(admins.split(";")));
		return configAdmins.any(admin -> admin.equals(login));
	}

	/*
	 * Connection utils
	 */

	/**
	 * @return the current ip on the first local network
	 */
	public static String getLocalIP() {
		if(localIP != null)
			return localIP;
		try {
			InetAddress localhost = InetAddress.getLocalHost();
			String localIp = localhost.getHostAddress();
			if (localIp.startsWith("192.168")) {
				localIP = localIp;
				return localIP;
			}
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface itf = networkInterfaces.nextElement();
				if (itf.isUp() && !itf.isLoopback() && !itf.isVirtual() && !itf.isPointToPoint()) {
					Enumeration<InetAddress> adds = itf.getInetAddresses();
					while (adds.hasMoreElements()) {
						InetAddress add = adds.nextElement();
						if (add.isSiteLocalAddress() && add.getHostAddress().startsWith("192.168")) {
							localIP = add.getHostAddress();
							return localIP;
						}
					}
				}
			}
		} catch (UnknownHostException | SocketException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		return localIP;
	}

	/*
	 * Other
	 */

	/**
	 * Generate a random string with numbers, uppercase and lowercase letters.
	 * 
	 * @param length
	 *            the length of the string
	 * @return the generated string
	 */
	public static String getRandomString(int length) {
		StringBuilder output = new StringBuilder();
		int pos;
		for (int i = 0; i < length; i++) { // 48-57 65-90 97-122
			pos = ThreadLocalRandom.current().nextInt(62);
			if (pos < 10)
				output.append((char) (pos + 48)); // numbers
			else if (pos < 36)
				output.append((char) (pos + 55)); // uppercase letters
			else
				output.append((char) (pos + 61)); // lowercase letters
		}
		return output.toString();
	}

	/**
	 * Transform a JSONArray into a List of jsonObject.
	 * 
	 * @param src
	 *            the source JSONArray
	 * @return a list of jsonObject
	 */
	public static List<JSONObject> jArrayToJObjectList(JSONArray src) {
		ArrayList<JSONObject> lst = new ArrayList<>(src.length());
		for (int i = 0; i < src.length(); i++)
			lst.add(src.getJSONObject(i));
		return lst;
	}

	/**
	 * Convert a string to an Integer.
	 * 
	 * @param text
	 *            a text to convert to Integer
	 * @return Integer or null if the string couldn't be converted
	 */
	public static Integer stringToInteger(String text) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	/**
	 * Convert a string into a Long.
	 * 
	 * @param text
	 *            a text to convert to Long
	 * @return Long or null if the string couldn't be converted
	 */
	public static Long stringToLong(String text) {
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	public static Long mibToKib(int mib) {
		return ((long) mib) * 1024;
	}
	
	public static int kibToMib(long kib) {
		return (int) (kib / 1024);
	}
	
	public static Long mibTob(int mib) {
		return ((long) mib) * 1024 * 1024;
	}
	
	public static int bToMib(long kib) {
		return (int) (kib / 1024 / 1024);
	}


    public static Long bToKib(long kib) {
        return (kib / 1024);
    }


    /**
     * Return the class name from the calling class in th stack trace.
     * 
     * @param stackLevel the level in the stack trace
     * @return the classname of th calling class
     */
    public static String getCallingClassName(int stackLevel) {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		if(stackLevel >= stackTrace.length)
			return null;
		String[] source = stackTrace[stackLevel].getClassName().split("\\.");
		return source[source.length-1];
	}
	
	public static boolean containsIgnoreCase(String s1, String s2) {
		return s1.toLowerCase().contains(s2.toLowerCase());
	}

	/**
	 * Return the extension of a file.
     *
     * @param file the file
	 * @return the extension of the file
	 */
	public static String getExtension(File file){
		if (file == null){
			return null;
		}
		return getExtension(file.getName());
	}
	
	/**
	 * Return the extension of a file.
	 * 
	 * @param fileName the file
	 * @return the extension of the file
	 */
	public static String getExtension(String fileName){
		if (fileName == null){
			return null;
		}
		int i = fileName.lastIndexOf('.');
		if (i > 0) {
			return fileName.substring(i+1);
		}
		return null;
	}

	
	@SuppressWarnings("unchecked")
	public static <T> T coalesce(T ...items) {
	    for(T i : items) if(i != null) return i;
	    return null;
	}
	
	/**
	 * Delete a folder and all its content.
     *
     * @param file the folder to delete
     * @return true on success
	 */
    public static boolean deleteFolder(File file) {
        if (!file.exists()) {
            return false;
        }

        if (file.isDirectory()) {
            String[] entries = file.list();
            boolean success = true;
            for (String s : entries) {
                File currentFile = new File(file.getPath(), s);
                success &= deleteFolder(currentFile);
            }

            if (!success) {
                return false;
            }

            try {
                Files.delete(file.toPath());
                return success;
            } catch (IOException e) {
                Logger.log(Level.WARNING, "could not delete file : {0}", file);
                return false;
            }
        } else {
            try {
                Files.delete(file.toPath());
                return true;
            } catch (IOException e) {
                Logger.log(Level.WARNING, "could not delete file : {0}", file);
                return false;
            }
        }
    }

    /**
     * Check if a String is alphanumeric including some chars
     *
     * @param source   the String to test
     * @param included included chars other than alphanumerics
     * @return true if it passes
     */
    public static boolean isAlphaNumeric(String source, Character... included) {
        if (source == null)
            return true;
        List<Character> includedList = Arrays.asList(included);
        for (char c : source.toCharArray())
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && !includedList.contains(c))
                return false;
        return true;
    }

    /**
     * Navigate through a JSONObject by keys
     *
     * @param source the original JSONObject
     * @param keys   the keys to find successively
     * @return the found JSONObject or null if the key was not found
     */
    public static JSONObject navigateJSON(JSONObject source, String... keys) {
        JSONObject obj = source;
        for (String key : keys) {
            if (!obj.has(key))
                return null;
            obj = obj.getJSONObject(key);
        }
        return obj;
    }

	/**
	 * Scrap and create new users from LDAP
	 */
	public static void createLDAPUsers() {
		Logger.log(Level.INFO, "Scrapping LDAP...");
		List<String> allUids = LDAPUtils.scrapLDAP();
		BetterArrayList<User> allUsers = new BetterArrayList<>(User.getAll());

		int newUsers = 0;

		for (String uid : allUids) {
			if (!allUsers.any(u -> u.getLogin().equals(uid))) {
				String commonName = LDAPUtils.getCommonName(uid);
				User u = new User(uid, commonName);
				if (Utils.isAdminByConfig(uid))
					u.setAdmin();
				if (!u.saveOrUpdate())
					Logger.log(Level.WARNING, "User {0} : registration failed", uid);
				else
					newUsers++;
			}
		}

		if (newUsers > 0)
			Logger.log(Level.INFO, "Added {0} new users to database", newUsers);
	}

	/**
	 * Check all users
	 */
	public static void checkUsers() {
		for (User user : User.getAll())
			if (user.checkGroups())
				user.saveOrUpdate();
	}
}
