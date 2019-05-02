package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.utils.HttpUtils.HttpResult;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class VSphereManagerTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        Logger.init("logging.properties", TestUtils.LOG_LEVEL);
        assertTrue(VSphereManager.init(Utils.getString("vsphere_host")));
    }

    @Before
    public void setUp() {
        VSphereManager.setHost(Utils.getString("vsphere_host"));
	}
	
	@Test
	public void testRequestVSphereAPI() {
		HttpResult hr = VSphereManager.requestVSphereAPI("GET", "/vcenter/datastore");
		assertEquals(200, hr.code);
		JSONObject obj = hr.getJSON();
		assertTrue(obj.has("value"));
	}
	
	@Test
	public void testRequestVSphereAPIInvalidSession() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field sessionId = VSphereManager.class.getDeclaredField("sessionId");
		sessionId.setAccessible(true);
		sessionId.set(VSphereManager.class, "invalidSessionId");
		HttpResult hr = VSphereManager.requestVSphereAPI("GET", "/vcenter/datastore");
		assertEquals(200, hr.code);
		JSONObject obj = hr.getJSON();
		assertTrue(obj.has("value"));
	}
	
	@Test
	public void testRequestVSphereNullHost() {
		VSphereManager.setHost(null);
		try {
			assertFalse(VSphereManager.init(null));
			VSphereManager.requestVSphereAPI("GET", "/vcenter/datastore");
			fail("No exception");
		}catch(ExceptionInInitializerError e) {}
	}
	
	@Test
	public void testRequestVSphereNullSession() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		Field sessionId = VSphereManager.class.getDeclaredField("sessionId");
		sessionId.setAccessible(true);
		sessionId.set(VSphereManager.class, null);
		try {
			VSphereManager.requestVSphereAPI("GET", "/vcenter/datastore");
			fail("No exception");
		}catch(ExceptionInInitializerError e) {}
	}
}
