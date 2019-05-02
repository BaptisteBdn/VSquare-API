package fr.eseo.vsquare;

import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.model.VSquareObject;
import fr.eseo.vsquare.model.Vm;
import fr.eseo.vsquare.servlet.AuthServlet;
import fr.eseo.vsquare.utils.*;
import fr.eseo.vsquare.utils.HttpUtils.HttpResult;
import org.json.JSONObject;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public final class TestUtils {

	public static final String DB_CONNECTION_STRING = Utils.getConnectionString("db_test_connection_string");
	public static final String LDAP_CONNECTION_STRING = Utils.getConnectionString("ldap_connection_string");

	public static final String TEMP_VM_NAME = "test_cc_group2";

	private static final String JENKINS_SERVER_IP = "192.168.4.242";
	private static final String JENKINS_JOB_URL = String.format("http://%s/jenkins/job/VSquare-API/api/json",
			JENKINS_SERVER_IP);

	public static final Level LOG_LEVEL = Level.INFO;

	private static Connection conn = null;

	private TestUtils() {

	}

	/*
	 * Database test utils
	 */

	public static void cleanDatabase(Connection conn) throws SQLException, IOException {
		try (InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream("sql/clean.sql")) {
			if (is != null)
				DatabaseManager.importSQL(conn, is);
			else
				fail("Unable to find SQL cleaning file");
		}

	}

	public static void emptyDatabase() throws SQLException {
		try (Statement st = conn.createStatement()) {
			st.addBatch("DELETE FROM error_log WHERE 1");
			st.addBatch("DELETE FROM event_log WHERE 1");
			st.addBatch("DELETE FROM token WHERE 1");
			st.addBatch("DELETE FROM vm WHERE 1");
			st.addBatch("DELETE FROM user WHERE 1");
			st.addBatch("DELETE FROM link_user_group WHERE 1");
			st.addBatch("DELETE FROM network WHERE 1");
			st.addBatch(String.format("DELETE FROM user_group WHERE id NOT IN (%d,%d,%d)",
					UserType.ADMIN.getDefaultGroup().getId(), UserType.REFERENT.getDefaultGroup().getId(),
					UserType.STUDENT.getDefaultGroup().getId()));
			st.addBatch("DELETE FROM permission WHERE 1");
			st.executeBatch();
		}
		// In case of default groups being deleted
		DatabaseManager.initDefaultGroups();
	}

	/**
	 * prepare different elements for tests : database, LDAPUtils
     * @param emptyDatabase if the database is emptied
     * @throws Exception on error
	 */
	public static void initTest(boolean emptyDatabase) throws Exception {
		Logger.setLevel(LOG_LEVEL);
		TestUtils.checkForJenkinsBuild();
		if (conn == null) {
			Logger.init("logging.properties", LOG_LEVEL);
			assertTrue(DatabaseManager.init(TestUtils.DB_CONNECTION_STRING));
			conn = DatabaseManager.openConnection();
			LDAPUtils.setConnectionString(Utils.getConnectionString("ldap_connection_string"));

			Field bruteForceSecurity = ServletUtils.class.getDeclaredField("bruteForceSecurity");
			bruteForceSecurity.setAccessible(true);
			bruteForceSecurity.set(ServletUtils.class, false);

		}
		if (emptyDatabase)
			TestUtils.emptyDatabase();
	}

	public static Connection getConnection() {
		return conn;
	}

	/*
	 * Servlet test utils
	 */

	@SuppressWarnings("unchecked")
	public static void mockVSphereAllResponse(int code, JSONObject response) {
		PowerMockito.mockStatic(VSphereManager.class);
		HttpResult res = new HttpResult(code, response.toString(), null);
		when(VSphereManager.requestVSphereAPI(any(), any(), any(), any())).thenReturn(res);
		when(VSphereManager.requestVSphereAPI(any(), any(), any(JSONObject.class))).thenReturn(res);
		when(VSphereManager.requestVSphereAPI(any(), any(), any(Map.class))).thenReturn(res);
		when(VSphereManager.requestVSphereAPI(any(), any())).thenReturn(res);
	}

	public static void mockVSphereResponse(String method, String URI, int code, JSONObject response) {
		mockVSphereResponse(method, URI, code, response, true);
	}

	/**
	 * @param method the request method
	 * @param URI the request URI
	 * @param code the code to respond
	 * @param response the respond to send
	 * @param firstLaunch when using multiple calls, set "true" to the first and "false" to
	 *                    the other. "true" will basically render void all previous
	 *                    settings.
	 */
	@SuppressWarnings("unchecked")
	public static void mockVSphereResponse(String method, String URI, int code, JSONObject response,
			boolean firstLaunch) {
		if (firstLaunch) {
			mockVSphereAllResponse(500, new JSONObject());
		}
		HttpResult res = new HttpResult(code, (response != null) ? response.toString() : null, null);
		when(VSphereManager.requestVSphereAPI(eq(method), eq(URI), any(), any())).thenReturn(res);
		when(VSphereManager.requestVSphereAPI(eq(method), eq(URI), any(JSONObject.class))).thenReturn(res);
		when(VSphereManager.requestVSphereAPI(eq(method), eq(URI), any(Map.class))).thenReturn(res);
		when(VSphereManager.requestVSphereAPI(eq(method), eq(URI))).thenReturn(res);
	}

	public static JSONObject getVmMockVcenterJSON(Vm vm) {
		return getVmMockVcenterJSON(vm, false);
	}

	public static JSONObject getVmMockVcenterJSON(Vm vm, boolean full) {
		JSONObject vsphereRes = new JSONObject();
		if (full) {
			vsphereRes = new JSONObject(
					"{\"parallel_ports\":{},\"cdroms\":{},\"name\":\"string\",\"floppies\":{},\"boot\":{\"enter_setup_mode\":true,\"retry\":true,\"efi_legacy_boot\":true,\"network_protocol\":\"IPV4\",\"delay\":0,\"retry_delay\":0,\"type\":\"BIOS\"},\"disks\":{},\"boot_devices\":[{\"nic\":\"string\",\"disks\":[\"string\"],\"type\":\"CDROM\"}],\"guest_OS\":\"DOS\",\"serial_ports\":{},\"hardware\":{\"version\":\"VMX_03\",\"upgrade_status\":\"NONE\",\"upgrade_version\":\"VMX_03\",\"upgrade_policy\":\"NEVER\"},\"nics\":[],\"power_state\":\"POWERED_ON\",\"memory\":{\"hot_add_increment_size_MiB\":0,\"hot_add_enabled\":true,\"hot_add_limit_MiB\":0,\"size_MiB\":4096},\"scsi_adapters\":{},\"sata_adapters\":{},\"cpu\":{\"count\":4,\"hot_add_enabled\":true,\"hot_remove_enabled\":true,\"cores_per_socket\":0}}");
			vsphereRes.getJSONArray("nics").put(new JSONObject("{\"value\":{\"start_connected\":true,\"pci_slot_number\":33,\"backing\":{\"network_name\":\"VM Network\",\"type\":\"STANDARD_PORTGROUP\",\"network\":\"network-33\"},\"mac_address\":\"00:50:56:ad:2e:04\",\"mac_type\":\"ASSIGNED\",\"allow_guest_control\":true,\"wake_on_lan_enabled\":true,\"label\":\"Networkadapter1\",\"state\":\"CONNECTED\",\"type\":\"E1000\"},\"key\":\"4000\"}"));
		}
		vsphereRes.put("memory_size_MiB", 0);
		vsphereRes.put("power_state", "POWERED_ON");
		vsphereRes.put("cpu_count", 0);
		vsphereRes.put("name", "name");
		vsphereRes.put("vm", vm.getIdVmVcenter());
		return vsphereRes;
	}

	public static HttpServletRequest createMockRequest(String method, String URI, Map<String, String> parameters,
			Map<String, String> headers) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getMethod()).thenReturn(method);
		when(request.getRequestURI()).thenReturn(URI);
		StringBuilder data = new StringBuilder();
		if (parameters != null) {
			for (Map.Entry<String, String> param : parameters.entrySet()) {
				when(request.getParameter(param.getKey())).thenReturn(param.getValue());
				data.append(param.getKey()).append("=").append(param.getValue()).append("&");
			}
		}
		try {
			doReturn(new ServletInputStream() {
				ByteArrayInputStream stream = new ByteArrayInputStream(data.toString().getBytes());
				@Override
				public boolean isFinished() {
					return stream.available() == 0;
				}

				@Override
				public boolean isReady() {
					return stream.available() > 0;
				}

				@Override
				public void setReadListener(ReadListener readListener) {
				}

				@Override
				public int read() throws IOException {
					return stream.read();
				}
			}).when(request).getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		when(request.getParameterMap()).thenReturn(new HashMap<>());
		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				when(request.getHeader(header.getKey())).thenReturn(header.getValue());
			}
		}
		return request;
	}

	public static void addPartMockRequest(HttpServletRequest request, String name, File file) throws IOException, ServletException{
		InputStream is = null;

		try {
			is = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}

		when(request.getContentType()).thenReturn("multipart/form-data");
		Part part = mock(Part.class);
		when(part.getName()).thenReturn(file.getName());
		when(part.getInputStream()).thenReturn(is);
		when(part.getSize()).thenReturn(file.length());
		when(part.getSubmittedFileName()).thenReturn(file.getName());
		when(request.getPart(name)).thenReturn(part);


	}

	public static HttpServletResponse createMockResponse(StringWriter stringWriter) {
		HttpServletResponse response = mock(HttpServletResponse.class);
		try {
			when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
		} catch (IOException e) {
		}
		return response;
	}

	public static JSONObject getResponseAsJSON(StringWriter stringWriter) {
		return new JSONObject(stringWriter.toString());
	}

	public static String login(String username, String password) throws ServletException, IOException {
		return login(username, password, UserType.STUDENT);
	}

	public static String login(String username, String password, UserType type) throws ServletException, IOException {
		return login(username, password, type, true);
	}

	private static String login(String username, String password, UserType type, boolean deleteIfAny)
			throws ServletException, IOException {
		User u;
		if (deleteIfAny) {
			u = User.findByLogin(username);
			if (u != null)
				u.delete();
		}

		StringWriter writer = new StringWriter();
		HashMap<String, String> params = new HashMap<>();
		params.put("username", username);
		params.put("password", password);

		PowerMockito.mockStatic(LDAPUtils.class);

		Mockito.when(LDAPUtils.tryCredentials(username, password)).thenReturn(true);
		Mockito.when(LDAPUtils.getCommonName(username)).thenReturn("unknown");

		HttpServletRequest request = TestUtils.createMockRequest("POST", "/api/auth/login", params, null);
		HttpServletResponse response = TestUtils.createMockResponse(writer);

		new AuthServlet().service(request, response);

		JSONObject res = TestUtils.getResponseAsJSON(writer);
		assertEquals(HttpServletResponse.SC_OK, res.getInt("code"));
		assertTrue(res.getJSONObject("value").has("token"));

		Token t = Token.findByValue(res.getJSONObject("value").getString("token"));
		u = t.getUser();
		u.setType(type);
		u.saveOrUpdate();

		return t.getValue();
	}

	/**
	 * Check for builds on jenkins to not interfere with tests
	 */
	private static void checkForJenkinsBuild() {
		if (!JENKINS_SERVER_IP.equals(Utils.getLocalIP())) {
			// local tests
			Map<String, String> headers = HttpUtils.getBasicAuthHeaders("jenkins", "7b619638d5bbbf18a7c4ea3318399a3e");
			HttpResult hr = HttpUtils.executeRequest("GET", JENKINS_JOB_URL, null, headers);
			if (hr.code == 200) {
				JSONObject job_info = hr.getJSON();
				if (!job_info.isNull("lastBuild") && !job_info.isNull("lastCompletedBuild")) {
					int lastBuildNumber = job_info.getJSONObject("lastBuild").getInt("number");
					int lastCompletedBuildNumber = job_info.getJSONObject("lastCompletedBuild").getInt("number");
					if (lastBuildNumber > lastCompletedBuildNumber)
						fail("A build is running on Jenkins");
				}
			}
		}
	}

	public static void changeVSquareObjectDate(VSquareObject object, Date date) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field creationDate = VSquareObject.class.getDeclaredField("creationDate");
		creationDate.setAccessible(true);
		creationDate.set(object, date);
	}

	

}
