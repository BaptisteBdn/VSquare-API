package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 
 * @author Baptiste Beduneau
 *
 */

public class LDAPUtilsTest {
	
	/**
	 * A user must have been created in the LDAP in order to run the following tests
	 */

	private final String uid = "test";
	private final String pw = "test";
	private final String common_name = "Test Test";

    @BeforeClass
    public static void setUpClass() throws Exception {
        Logger.init("logging.properties", TestUtils.LOG_LEVEL);
    }

	@Before
	public void setUp() throws Exception {
		LDAPUtils.setConnectionString(Utils.getConnectionString("ldap_connection_string"));
	}
	
	/**
	 * Get common name with a correct user : test
	 */
	@Test
	public void testGetCommonNameSuccess() {
		assertEquals(common_name, LDAPUtils.getCommonName("test"));
	}
	
	@Test
	public void testNullHostName() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field ldapHost = LDAPUtils.class.getDeclaredField("ldapHost");
		ldapHost.setAccessible(true);
		ldapHost.set(LDAPUtils.class, null);
		try {
			LDAPUtils.getCommonName("test");
			fail("No error");
		}catch(ExceptionInInitializerError e) {}
	}
	
	/**
	 * Get common name with a incorrect user : testNotAdded
	 */
	@Test
	public void testGetCommonNameFail() {
		assertNull(LDAPUtils.getCommonName("testNotAdded"));
	}
	
	/**
	 * Try connect with a correct user : test
	 */
	@Test
	public void testTryCredentialSuccess() {
		assertTrue(LDAPUtils.tryCredentials(uid, pw));
	}
	
	/**
	 * Try connect : 
	 * correct user : test
	 * incorrect password : testNotAdded 
	 */
	@Test
	public void testTryCredentialFailUid() {
		assertFalse(LDAPUtils.tryCredentials("testNotAdded", pw));
	}
	
	/**
	 * Try connect :
	 * incorrect user : testNotAdded
	 * correct password : test 
	 */
	@Test
	public void testTryCredentialFailPassword() {
		assertFalse(LDAPUtils.tryCredentials(uid, "testNotAdded"));
	}
	
	/**
	 * Try connect :
	 * incorrect user : testNotAdded
	 * correct password : test 
	 */
	@Test
	public void testScrapLDAP() {
		List<String> scrapped = LDAPUtils.scrapLDAP();
		assertTrue(scrapped.size() > 0);
		assertTrue(scrapped.contains("test"));
	}
}
