package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.*;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.logging.Level;

/**
 * Servlet implementation class InfoServlet.
 * 
 * @author Baptiste Beduneau
 */
@WebServlet({"/info", "/hosts/*", "/datastore/*", "/datastores/*"})
public class InfoServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String UNKNOWN_VALUE = "unknown";

	public InfoServlet() {
		super();
	}

	/**
	 * Service at /api/*.
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
			map.put("GET /api", () -> getAPIInfo(response));
			map.put("GET /api/hosts", () -> getListHost(request, response));
			map.put("GET /api/datastores", () -> getListDatastore(request, response));
			map.put("GET /api/datastore/{}", () -> getDetailsDatastore(request, response));
			map.put("GET /api/networks", () -> getListNetwork(request, response));
			ServletUtils.mapRequest(request, response, map);
		} catch (Exception e) {
			Logger.log(Level.SEVERE, e.toString());
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Return API information.
	 * 
	 * Return various information about API version, build number.
	 * See doc GET /api.
	 *
	 * @param response the servlet response
	 */
	private void getAPIInfo(HttpServletResponse response) {
		JSONObject result = new JSONObject();

		String sCodeVersion = Utils.getVersionString("code_version");
		result.put("code_version", sCodeVersion == null ? UNKNOWN_VALUE : Integer.parseInt(sCodeVersion));
		String sBuild = Utils.getVersionString("build");
		result.put("build", sBuild == null ? UNKNOWN_VALUE : Integer.parseInt(sBuild));

		readDatabaseVersion(result);

		ServletUtils.sendJSONResponse(response, result);
	}

	/**
	 * Return database version and add it to the json object.
	 * 
	 * @param result the json object to edit
	 */
	private void readDatabaseVersion(JSONObject result) {
		int dbVersion = -1;
		Timestamp dbUpdate = null;
		try (Connection conn = DatabaseManager.openConnection()) {
			try (Statement st = conn.createStatement()) {
				try (ResultSet rs = st.executeQuery("SELECT * FROM db_info")) {
					if (rs.first()) {
						dbVersion = rs.getInt("version");
						dbUpdate = rs.getTimestamp("update_date");
					}
				}
			}
		} catch (SQLException e) {
			Logger.log(Level.SEVERE, e.toString(), e);
		}
		result.put("db_version", dbUpdate == null ? UNKNOWN_VALUE : dbVersion);
		result.put("db_last_update", dbUpdate == null ? UNKNOWN_VALUE : dbUpdate);
	}

	/**
	 * Return the list of the VSphere hosts.
	 * 
	 * See doc GET /api/hosts.
	 *
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void getListHost(HttpServletRequest request, HttpServletResponse response) {
		if (!verifyUser(request, response))
			return;

		JSONObject json = VSphereConnector.getHostList();

		ServletUtils.sendVSphereResponse(response, json);
	}

	/**
	 * Return the list of the VSphere datastore.
	 * 
	 * See doc GET /api/datastores.
	 *
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void getListDatastore(HttpServletRequest request, HttpServletResponse response) {
		if (!verifyUser(request, response))
			return;

		JSONObject json = VSphereConnector.getDatastoreList();

		ServletUtils.sendVSphereResponse(response, json);
	}

	/**
	 * Return the list of VSphere networks.
	 * 
	 * See doc GET /api/networks.
	 *
	 * @param request  the servlet request
	 * @param response the servlet response
	 */
	private void getListNetwork(HttpServletRequest request, HttpServletResponse response) {
		if (!verifyUser(request, response))
			return;

		JSONObject json = VSphereConnector.getNetworkList();

		ServletUtils.sendVSphereResponse(response, json);
	}

	/**
	 * Return detailled information about the datastore.
	 * 
	 * See doc GET /api/datastore/{datastore-id}.
	 *
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void getDetailsDatastore(HttpServletRequest request, HttpServletResponse response) {
		String[] path = request.getRequestURI().split("/");
		String datastore = path[3];

		if (!verifyUser(request, response))
			return;

        if (!Utils.isAlphaNumeric(datastore, '-')) {
            ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid datastore name format");
            return;
        }

		JSONObject json = VSphereConnector.getDatastoreDetails(datastore);

		ServletUtils.sendVSphereResponse(response, json);
	}

	/**
	 * Verify a user.
	 * 
	 * Check if the user is a registered user with a valid token and
	 * has admin privileges.
	 * 
	 * @param request the servlet request
	 * @param response the servlet response
	 * @return true if the user is logged and admin, false otherwise
	 */
	private boolean verifyUser(HttpServletRequest request, HttpServletResponse response) {
		User user = ServletUtils.verifyToken(request, response);
		if (user == null) {
			return false;
		}

		if (user.getType() != UserType.ADMIN) {
			ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
			return false;
		}
		return true;
	}

}
