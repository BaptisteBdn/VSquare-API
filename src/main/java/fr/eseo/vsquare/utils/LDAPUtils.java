package fr.eseo.vsquare.utils;

import com.unboundid.ldap.sdk.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Utility class that store useful LDAP functions.
 * 
 * @author Clement Gouin
 */
public final class LDAPUtils {

	private LDAPUtils() {
	}

	private static String ldapHost = null;
	private static int ldapPort = 389;

	public static void setConnectionString(String connectionString) {
        if (connectionString == null)
            return;
		String[] temp = connectionString.replace("ldap://", "").split(":");
		ldapHost = temp[0];
		if (temp.length > 1) {
			try {
				ldapPort = Integer.parseInt(temp[1]);
			} catch (NumberFormatException e) {
				Logger.log(Level.WARNING, e.toString(), e);
			}
		}
	}

	/**
	 * Safely get the host.
	 * 
	 * @return the LDAP host
	 * @throws ExceptionInInitializerError
	 *             if the host is null
	 */
	private static String getHost() {
		if (ldapHost == null)
			throw new ExceptionInInitializerError("LDAP host is null");
		return ldapHost;
	}

	/**
	 * Get an attribute of a user by its uid.
	 * 
	 * @param uid
	 *            the uid of the user
	 * @param attribute
	 *            the name of the attribute (dn for full DN)
	 * @return the attribute value
	 */
	private static String getAttribute(String uid, String attribute) {
        String baseDns = Utils.getString("ldap_base_dn");
        if (baseDns == null)
            return null;
		try (LDAPConnection conn = new LDAPConnection(getHost(), ldapPort, Utils.getString("ldap_admin_dn"),
				Utils.getString("ldap_admin_pass"))) {

            String[] baseDnsSplit = baseDns.split(";");

            for (String baseDn : baseDnsSplit) {
				SearchResultEntry res = conn.searchForEntry(baseDn, SearchScope.SUB, String.format("(uid=%s)", uid));

				if (res != null) {
					if (attribute.equalsIgnoreCase("dn"))
						return res.getDN();
					Attribute attr = res.getAttribute(attribute);
					return attr == null ? null : attr.getValue();
				}
			}

			return null;

		} catch (LDAPException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			return null;
		}
	}

	/**
	 * Return all uids of users from the given dn.
	 * 
	 * @param conn
	 *            the LDAPConnection
	 * @param baseDN
	 *            the dn to search
	 * @return a List of all users' uid
	 * @throws LDAPSearchException exception
	 */
	private static List<String> scrapDN(LDAPConnection conn, String baseDN) throws LDAPSearchException {
		SearchResult res = conn.search(baseDN, SearchScope.ONE, "(objectClass=*)");
		if (res != null) {
			ArrayList<String> results = new ArrayList<>();
			for (SearchResultEntry entry : res.getSearchEntries()) {
				if (entry.getAttributeValue("objectClass").equals("organizationalUnit")) {
					results.addAll(scrapDN(conn, entry.getDN()));
				} else if (entry.hasAttribute("uid")) {
					results.add(entry.getAttributeValue("uid"));
				}
			}
			return results;
		} else {
			return new ArrayList<>(0);
		}
	}

	/**
	 * Return all uids of users from the LDAP.
	 * 
	 * @return a List of all users' uid
	 */
	public static List<String> scrapLDAP() {
        String baseDns = Utils.getString("ldap_base_dn");
        if (baseDns == null)
            return new ArrayList<>(0);
		try (LDAPConnection conn = new LDAPConnection(getHost(), ldapPort, Utils.getString("ldap_admin_dn"),
				Utils.getString("ldap_admin_pass"))) {
			ArrayList<String> results = new ArrayList<>();
            String[] baseDnsSplit = baseDns.split(";");
            for (String baseDn : baseDnsSplit) {
				results.addAll(scrapDN(conn, baseDn));
			}
			return results;
		} catch (LDAPException e) {
			Logger.log(Level.SEVERE, e.getExceptionMessage(true, false));
			return new ArrayList<>(0);
		}

	}

	/**
	 * Try to connect to the LDAP with the given credentials.
	 * 
	 * @param dn
	 *            the dn of the user
	 * @param password
	 *            the password of the user
	 * @return true if the connection is successful
	 */
	private static boolean tryConnection(String dn, String password) {
		try (LDAPConnection conn = new LDAPConnection(getHost(), ldapPort, dn, password)) {
			return true;
		} catch (LDAPException e) {
			return false;
		}
	}

	/**
	 * Try to connect to the LDAP with the given credentials.
	 * 
	 * @param uid
	 *            the uid of the user
	 * @param password
	 *            the password of the user
	 * @return true if the connection is successful
	 */
	public static boolean tryCredentials(String uid, String password) {
		String dn = getAttribute(uid, "dn");
		if (dn == null)
			return false;
		return tryConnection(dn, password);
	}

	/**
	 * Return the common name of the user.
	 * 
	 * @param uid
	 *            the uid of the user
	 * @return String attribute or null if uid doesn't exist
	 */
	public static String getCommonName(String uid) {
		return getAttribute(uid, "cn");
	}

}
