package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.TestUtils;
import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.LDAPUtils;
import fr.eseo.vsquare.utils.Utils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "java.*", "javax.*", "org.*", "fr.eseo.vsquare.model.*", "fr.eseo.vsquare.utils.DatabaseManager" })
@PrepareForTest({ LDAPUtils.class, Utils.class })
public class AuthServletTest {

	@Before
	public void setUp() throws Exception {
		TestUtils.initTest(true);
	}

	@Test
	public void testServletOptions() throws ServletException, IOException {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("OPTIONS", "", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);
		new AuthServlet().service(request, response);
		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(200, res.getInt("code"));
	}

	@Test
	public void testLoginBadRequest() throws Exception {
		StringWriter writer = new StringWriter();
		HashMap<String, String> params = new HashMap<>();
		params.put("username", "test");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/auth/login", params, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testLoginBadRequest2() throws Exception {
		StringWriter writer = new StringWriter();
		HashMap<String, String> params = new HashMap<>();
		params.put("password", "test");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/auth/login", params, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getInt("code"));
	}

	@Test
	public void testLoginSuccessNewUser() throws Exception {

		String username = "test";
		String password = "test";

		PowerMockito.mockStatic(LDAPUtils.class);

		Mockito.when(LDAPUtils.tryCredentials(username, password)).thenReturn(true);
        Mockito.when(LDAPUtils.getCommonName(username)).thenReturn("unknown");

		StringWriter writer = new StringWriter();
		HashMap<String, String> params = new HashMap<>();
		params.put("username", username);
		params.put("password", password);

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/auth/login", params, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));
		assertTrue(res.getJSONObject("value").has("token"));
		assertTrue(res.getJSONObject("value").has("common_name"));
		assertEquals(User.DEFAULT_USER_TYPE.toString(), res.getJSONObject("value").getString("user_type"));

		List<User> users = User.getAll();
		assertEquals(1, users.size());
		assertEquals("test", users.get(0).getLogin());
		assertTrue(users.get(0).getGroups().contains(UserType.STUDENT.getDefaultGroup()));

		List<Token> tokens = Token.getAll();
		assertEquals(1, tokens.size());
		assertEquals(users.get(0), tokens.get(0).getUser());
	}

	@Test
	public void testLoginSuccessNewUserAdmin() throws Exception {

		String username = "test";
		String password = "test";

		PowerMockito.mockStatic(LDAPUtils.class);

		Mockito.when(LDAPUtils.tryCredentials(username, password)).thenReturn(true);
        Mockito.when(LDAPUtils.getCommonName(username)).thenReturn("unknown");

		StringWriter writer = new StringWriter();
		HashMap<String, String> params = new HashMap<>();
		params.put("username", username);
		params.put("password", password);

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/auth/login", params, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		PowerMockito.mockStatic(Utils.class);
		when(Utils.isAdminByConfig(anyString())).thenReturn(true);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));
		assertTrue(res.getJSONObject("value").has("token"));
		assertTrue(res.getJSONObject("value").has("common_name"));
		assertEquals(UserType.ADMIN.toString(), res.getJSONObject("value").getString("user_type"));

		List<User> users = User.getAll();
		assertEquals(1, users.size());
		assertEquals("test", users.get(0).getLogin());
		assertTrue(users.get(0).getGroups().contains(UserType.ADMIN.getDefaultGroup()));

		List<Token> tokens = Token.getAll();
		assertEquals(1, tokens.size());
		assertEquals(users.get(0), tokens.get(0).getUser());
	}

	@Test
	public void testLoginSuccessExistingUser() throws Exception {

		String username = "test";
		String password = "test";

		PowerMockito.mockStatic(LDAPUtils.class);

		Mockito.when(LDAPUtils.tryCredentials(username, password)).thenReturn(true);
        Mockito.when(LDAPUtils.getCommonName(username)).thenReturn("unknown");

        User u = new User(username, UserType.STUDENT, "unknown");
		u.saveOrUpdate();

		StringWriter writer = new StringWriter();
		HashMap<String, String> params = new HashMap<>();
		params.put("username", username);
		params.put("password", password);

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/auth/login", params, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));
		assertTrue(res.getJSONObject("value").has("token"));
		assertTrue(res.getJSONObject("value").has("common_name"));
		assertEquals(User.DEFAULT_USER_TYPE.toString(), res.getJSONObject("value").getString("user_type"));

		List<User> users = User.getAll();
		assertEquals(1, users.size());
		assertEquals(u, users.get(0));

		List<Token> tokens = Token.getAll();
		assertEquals(1, tokens.size());
		assertEquals(u, tokens.get(0).getUser());
	}

	@Test
	public void testLogout() throws Exception {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/auth/logout", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));
	}

	@Test
	public void testLogoutBadToken() throws Exception {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("DELETE", "/api/auth/logout", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

	@Test
	public void testCheck() throws Exception {
		String token = TestUtils.login("test", "test");

		StringWriter writer = new StringWriter();
		HashMap<String, String> headers = new HashMap<>();
		headers.put(Utils.getString("auth_token_header"), token);

		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/auth", null, headers);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));
	}

	@Test
	public void testCheckBadToken() throws Exception {
		StringWriter writer = new StringWriter();

		HttpServletRequest request = TestUtils.createMockRequest("GET", "/api/auth", null, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

	@Test
	public void testLoginBadCredentials() throws Exception {
		StringWriter writer = new StringWriter();
		HashMap<String, String> params = new HashMap<>();
		params.put("username", "test");
		params.put("password", "test2");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/auth/login", params, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.getInt("code"));
	}

}
