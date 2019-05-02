package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.utils.HttpUtils.HttpResult;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Utility class that store useful VSphere functions and handle VSphere session.
 * 
 * @author Clement Gouin
 */
public final class VSphereManager {

	private static final String SESSION_ID_COOKIE = "vmware-api-session-id=%s;Path=/rest;Secure;HttpOnly";

	private static String sessionId = null;
	private static String vsphereHost = null;

	private VSphereManager() {
	}

	public static void setHost(String vSphereHost) {
		VSphereManager.vsphereHost = vSphereHost;
	}

	private static String getHost() {
		if (vsphereHost == null)
			throw new ExceptionInInitializerError("VSphere host is null");
		return vsphereHost;
	}
	
	public static boolean init(){
		return init(Utils.getString("vsphere_host"));
	}

	/**
	 * Login to vSphereInterface.
	 * 
	 * @param vSphereHost
	 *            the host of VSphere
	 * @return true if the operation is successful
	 */
	public static boolean init(String vSphereHost) {
		Logger.log(Level.INFO, "Initializing VSphere session...");
		if (vSphereHost != null)
			setHost(vSphereHost);
		HttpResult hr = HttpUtils.executeRequest("POST",
				String.format("https://%s/rest/com/vmware/cis/session", getHost()), null,
				HttpUtils.getBasicAuthHeaders(Utils.getString("vsphere_user"), Utils.getString("vsphere_pass")));
		if (hr.code != 200) {
			Logger.log(Level.SEVERE, "VSphere returned code {0}", hr.code);
			return false;
		}
		sessionId = hr.getJSON().getString(ServletUtils.VALUE_KEY);
		Logger.log(Level.INFO, "Connected to VSphere with sessionId {0}", sessionId);
		return true;
	}

	/**
	 * Make a request to VSphere API.
	 * 
	 * @param method
	 *            the http method
	 * @param request
	 *            the request url (example /vcenter/vm)
	 * @return an HttpResult object of the request
	 */
	public static HttpResult requestVSphereAPI(String method, String request) {
		return requestVSphereAPI(method, request, null, null);
	}

	/**
	 * Make a request to VSphere API.
	 * 
	 * @param method
	 *            the http method
	 * @param request
	 *            the request url (example /vcenter/vm)
	 * @param params
	 *            the url parameters (or null if not needed)
	 * @return an HttpResult object of the request
	 */
	public static HttpResult requestVSphereAPI(String method, String request, Map<String, String[]> params) {
		return requestVSphereAPI(method, request, params, null);
	}

	/**
	 * Make a request to VSphere API.
	 * 
	 * @param method
	 *            the http method
	 * @param request
	 *            the request url (example /vcenter/vm)
	 * @param data
	 *            the json data of the request
	 * @return an HttpResult object of the request
	 */
	public static HttpResult requestVSphereAPI(String method, String request, JSONObject data) {
		return requestVSphereAPI(method, request, null, data);
	}

	/**
	 * Make a request to VSphere API.
	 * 
	 * @param method
	 *            the http method
	 * @param request
	 *            the request url (example /vcenter/vm)
	 * @param params
	 *            the url parameters (or null if not needed)
	 * @param data
	 *            the json data of the request
	 * @return an HttpResult object of the request
	 */
	public static HttpResult requestVSphereAPI(String method, String request, Map<String, String[]> params,
			JSONObject data) {
		return requestVSphereAPI(method, request, params, data, false);
	}

	/**
	 * Make a request to VSphere API.
	 * 
	 * @param method
	 *            the http method
	 * @param request
	 *            the request url (example /vcenter/vm)
	 * @param params
	 *            the url parameters (or null if not needed)
	 * @param data
	 *            the json data of the request
	 * @param reloadSessionId
	 *            reload the vsphere session
	 * @return an HttpResult object of the request
	 */
	private static HttpResult requestVSphereAPI(String method, String request, Map<String, String[]> params,
			JSONObject data, boolean reloadSessionId) {
		if (sessionId == null) {
			throw new ExceptionInInitializerError("VSphere sessionId is null");
		}
		if (reloadSessionId) {
			init(null);
		}

		method = method.toUpperCase();

		HashMap<String, String> headers = new HashMap<>();
		headers.put("Cookie", String.format(SESSION_ID_COOKIE, sessionId));
		HttpResult hr = HttpUtils.executeRequest(method, String.format("https://%s/rest%s", getHost(), request), params,
				headers, data);
		if (!reloadSessionId && (hr.code == 401 || hr.code == 403)) {
			hr = requestVSphereAPI(method, request, params, data, true);
		}

		return hr;
	}
}
