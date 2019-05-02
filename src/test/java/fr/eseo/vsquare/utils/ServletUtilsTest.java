package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Date;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager" })
@PrepareForTest({ LDAPUtils.class })
public class ServletUtilsTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testVerifyTokenNoHeader() {
		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		assertNull(ServletUtils.verifyToken(request));
	}

	@Test
	public void testVerifyTokenNoHeader2() {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		User user = ServletUtils.verifyToken(request, response);
		assertNull(user);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

	@Test
	public void testVerifyTokenInvalid() {
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), "test");

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, headers);
		assertNull(ServletUtils.verifyToken(request));
	}

	@Test
	public void testVerifyTokenInvalid2() throws ServletException, IOException, SQLException {
		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), "test");

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		User user = ServletUtils.verifyToken(request, response);
		assertNull(user);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

	@Test
	public void testVerifyTokenExpired() throws ServletException, IOException, SQLException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		String stoken = TestUtils.login("test", "test");

		Token token = Token.findByValue(stoken);

		
		
		TestUtils.changeVSquareObjectDate(token,new Date(System.currentTimeMillis() - Utils.getInt("token_max_age") * 1000 - 1));
		token.saveOrUpdate();

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), stoken);

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, headers);
		assertNull(ServletUtils.verifyToken(request));
	}

	@Test
	public void testVerifyTokenExpired2() throws ServletException, IOException, SQLException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		String stoken = TestUtils.login("test", "test");

		Token token = Token.findByValue(stoken);

		TestUtils.changeVSquareObjectDate(token,new Date(System.currentTimeMillis() - Utils.getInt("token_max_age") * 1000 - 1));
		token.saveOrUpdate();

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), stoken);

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		User user = ServletUtils.verifyToken(request, response);
		assertNull(user);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

	@Test
	public void testVerifyToken() throws ServletException, IOException, SQLException {
		String stoken = TestUtils.login("test", "test");

		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), stoken);

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, headers);
		Token token = ServletUtils.verifyToken(request);
		assertEquals(stoken, token.getValue());
	}

	@Test
	public void testVerifyToken2() throws ServletException, IOException, SQLException {
		String stoken = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), stoken);

		HttpServletRequest request = TestUtils.createMockRequest("", "", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		User user = ServletUtils.verifyToken(request, response);
		assertNotNull(user);
	}

	@Test
	public void testMatchingURI() {
		assertTrue(ServletUtils.matchingURI("/api/test/{}/test", "/api2/test/bla/test", 2));
		assertFalse(ServletUtils.matchingURI("/api", "/api2", 1));
		assertFalse(ServletUtils.matchingURI("/api/group/{}", "/api/group", 2));
	}

	@Test
	public void testMapRequestSuccess() {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/test/bla", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

        LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
		map.put("PUT /api/test/{}", () -> {
			fail("Invalid mapping");
		});
		map.put("DELETE /api/test/{}", () -> {
			Integer.parseInt("a");
		});

		try {
			ServletUtils.mapRequest(request, response, map);
			fail("Invalid mapping");
		} catch (NumberFormatException e) {
		}
	}

    @Test
    public void testMapRequestSuccess2() {
        StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/vm/templates", null, null);
        HttpServletResponse response = TestUtils.createMockResponse(writer);

        LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
        map.put("GET /api/vm/templates", () -> {
            Integer.parseInt("a");
        });
        map.put("GET /api/vm/{}", () -> {
            fail("Invalid mapping");
        });

        try {
            ServletUtils.mapRequest(request, response, map);
            fail("Invalid mapping");
        } catch (NumberFormatException e) {
        }
    }

	@Test
	public void testMapRequestWrongMethod() {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/test/bla", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

        LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
		map.put("PUT /api/test/{}", () -> {
			fail("Invalid mapping");
		});
		map.put("DELETE /api/test/{}", () -> {
			fail("Invalid mapping");
		});
		map.put("GET /api/test/bla/{}", () -> {
			fail("Invalid mapping");
		});

		ServletUtils.mapRequest(request, response, map);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testMapRequestNotFound() {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/test/test2", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

        LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
		map.put("GET /api/test/test", () -> {
			fail("Invalid mapping");
		});
		map.put("GET /api/test/test3", () -> {
			fail("Invalid mapping");
		});

		ServletUtils.mapRequest(request, response, map);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}

	@Test
	public void testHandleCrossOrigin() {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "/api/test/test2", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		assertTrue(ServletUtils.handleCrossOrigin(request, response));
	}

	@Test
	public void testHandleCrossOrigin2() {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/test/test2", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		assertFalse(ServletUtils.handleCrossOrigin(request, response));
	}

	@Test
	public void testGetObjectFromRequest() {
		User u = new User("test", "test");
		u.saveOrUpdate();
		
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("", "/api/test/" + u.getId() + "/bla", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		User u2 = ServletUtils.getObjectFromRequest(request, response, 3, User.class);
		assertEquals(u,u2);
	}
	

	@Test
	public void testGetGroupFromRequestBadRequest() {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("", "/api/test/test/bla", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		User u2 = ServletUtils.getObjectFromRequest(request, response, 3, User.class);
		assertNull(u2);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}
	
	@Test
	public void testGetGroupFromRequestNotFound() {
		StringWriter writer = new StringWriter();
		HttpServletRequest request = TestUtils.createMockRequest("", "/api/test/52/bla", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		User u2 = ServletUtils.getObjectFromRequest(request, response, 3, User.class);
		assertNull(u2);
		
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getInt("code"));
	}
}
