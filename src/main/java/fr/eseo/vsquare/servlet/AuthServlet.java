package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.utils.LDAPUtils;
import fr.eseo.vsquare.utils.Logger;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.logging.Level;

/**
 * Servlet implementation class AuthServlet.
 * 
 * Handle the login and logout requests to VSquare.
 * 
 * @author Clement Gouin
 */
@WebServlet("/auth/*")
public class AuthServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public AuthServlet() {
		super();
	}

	/**
	 * Service at /api/auth/*
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
            LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
			map.put("GET /api/auth", () -> check(request, response));
			map.put("POST /api/auth/login", () -> login(request, response));
			map.put("DELETE /api/auth/logout", () -> logout(request, response));
			ServletUtils.mapRequest(request, response, map);

		} catch (Exception e) {
			Logger.log(Level.SEVERE, e.toString(), e);
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Login to the API.
	 * 
	 * See doc POST /api/auth/login.
	 * Implements a protection against bruteforcing.
	 * Return the token to the user if the credentials are valid.
     *
     * @param request the servlet request
     * @param response the servlet response
	 */
	private void login(HttpServletRequest request, HttpServletResponse response) {

		ServletUtils.bruteForceSecurity();

		String username = request.getParameter("username");
		String password = request.getParameter("password");

		if (username == null || password == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
					"Missing username or password parameter");
			return;
		}

		if (LDAPUtils.tryCredentials(username, password)) {
			User u = User.findByLogin(username);
			if (u == null) {
				String commonName = LDAPUtils.getCommonName(username);
				u = new User(username, commonName);
				if (Utils.isAdminByConfig(username))
					u.setAdmin();
				if (!u.saveOrUpdate()) {
					ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"Cannot register new user");
					Logger.log(Level.SEVERE, "Cannot register new user : '" + username + "'");
					return;
				}
			}
			Token token = u.generateToken();
			if (token == null) {
				ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot generate token");
				Logger.log(Level.SEVERE, "Cannot generate token for user '" + username + "'");
				return;
			}
			JSONObject result = new JSONObject();
			result.put("token", token.getValue());
			result.put("common_name", u.getCommonName());
			result.put("user_type", u.getType().toString());
			ServletUtils.sendJSONResponse(response, result);
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
		}
	}

	/**
	 * Validate the token.
	 * 
	 * See doc GET /api/auth.
     *
     * @param request the servlet request
     * @param response the servlet response
	 */
	private void check(HttpServletRequest request, HttpServletResponse response) {
		if (ServletUtils.verifyToken(request, response) == null) {
			return;
		}

		ServletUtils.sendOK(response);
	}

	/**
	 * Logout the user.
	 * 
	 * See doc POST /api/auth/logout.
	 * Delete the token from the database.
     *
     * @param request the servlet request
     * @param response the servlet response
	 */
	private void logout(HttpServletRequest request, HttpServletResponse response) {
		Token token = ServletUtils.verifyToken(request);
		if (token == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		if (token.delete()) {
			ServletUtils.sendJSONResponse(response, new JSONObject());
		} else {
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot delete token");
			Logger.log(Level.SEVERE, "Cannot delete token for user '" + token.getUser().getLogin() + "'",
					token.getUser());
		}
	}

}
