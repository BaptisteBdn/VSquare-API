package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class UtilsTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        Logger.init("logging.properties", TestUtils.LOG_LEVEL);
    }

    @Test
    public void testStringToIntegerSuccess() {
        String test = "123456";
        assertEquals(Integer.valueOf(123456), Utils.stringToInteger(test));
    }

    @Test
    public void testStringToIntegerFail() {
        String test = "test";
        assertNull(Utils.stringToInteger(test));
    }

    @Test
    public void testContainsIgnoreCase() {
        assertTrue(Utils.containsIgnoreCase("abcdef", "def"));
        assertTrue(Utils.containsIgnoreCase("abcDef", "def"));
        assertTrue(Utils.containsIgnoreCase("abcdef", "dEf"));
        assertTrue(Utils.containsIgnoreCase("abcdeF", "Def"));
        assertFalse(Utils.containsIgnoreCase("abcdef", "aef"));
    }

    @Test
    public void testGetStringFail() {
        assertNull(Utils.getString("invalid key"));
    }

    @Test
    public void testGetIntFail() {
        assertEquals(0, Utils.getInt("invalid key"));
    }

    @Test
    public void testGetIntFail2() {
        assertEquals(0, Utils.getInt("db_user"));
    }

    @Test
    public void testGetVersionStringFail() {
        assertNull(Utils.getVersionString("invalid key"));
    }

    @Test
    public void testGetConnectionStringFail() {
        assertNull(Utils.getConnectionString("invalid key"));
    }

    @Test
    public void testGetOsInfoFail() {
        String[] info = Utils.getOsInfo("invalid key");
        assertEquals(2, info.length);
        assertEquals("Other Operating System", info[0]);
        assertEquals("os_other", info[1]);
    }

    @Test
    public void testGetExtensionFile() {
        File file = new File("test.ova");
        assertEquals("ova", Utils.getExtension(file));
        file = new File("test");
        assertNull(Utils.getExtension(file));
        assertNull(Utils.getExtension((File) null));
    }

    @Test
    public void testCoalesceNull() {
        assertNull(Utils.coalesce());
        assertNull(Utils.coalesce((String) null));
        assertNull(Utils.coalesce((String) null, null));
    }

    @Test
    public void testCoalesceNotNull() {
        assertEquals("a", Utils.coalesce(null, "a"));
        assertEquals("a", Utils.coalesce(null, "a", "b"));
    }

    @Test
    public void testMibToKib() {
        assertEquals(1048576, Utils.mibToKib(1024).longValue());
    }

    @Test
    public void testKibToMib() {
        assertEquals(1024, Utils.kibToMib(1048576));
    }

    @Test
    public void testMibTob() {
        assertEquals(1073741824, Utils.mibTob(1024).longValue());
    }

    @Test
    public void testbToMib() {
        assertEquals(1024, Utils.bToMib(1073741824));
    }

    @Test
    public void testIsAlphaNumeric() {
        assertTrue(Utils.isAlphaNumeric("aBc"));
        assertTrue(Utils.isAlphaNumeric("123"));
        assertTrue(Utils.isAlphaNumeric("1B2a3Z45bc"));
        assertFalse(Utils.isAlphaNumeric(" -;!:,%"));
        assertFalse(Utils.isAlphaNumeric("1B2a3Z4 5bc"));
        assertTrue(Utils.isAlphaNumeric(""));
        assertTrue(Utils.isAlphaNumeric(null));
        assertTrue(Utils.isAlphaNumeric("1B2a3Z4 5bc", ' '));
        assertTrue(Utils.isAlphaNumeric(" -;!:,%", ' ', '-', ';', '!', ':', ',', '%'));
    }
}
