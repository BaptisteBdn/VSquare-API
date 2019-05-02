package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.ErrorLog;
import fr.eseo.vsquare.model.EventLog;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.utils.Logger;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * Servlet implementation class LogServlet.
 * 
 * Handle log related requests.
 * 
 * @author Clement Gouin
 */
@WebServlet("/log/*")
public class LogServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public LogServlet() {
		super();
	}

	/**
	 * Service at /api/user/*
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
            final User user = ServletUtils.verifyToken(request, response);
			if (user == null)
				return;
			LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
			map.put("GET /api/log/events", () -> listEvents(user, request, response));
			map.put("GET /api/log/errors", () -> listErrors(user, request, response));
			ServletUtils.mapRequest(request, response, map);
		} catch (Exception e) {
            Logger.log(Level.SEVERE, e.toString(), e);
			ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Return the list of the last events logged.
	 * 
	 * See doc GET /api/log/events.
	 * Allow the user to query specific terms to search the event logs.
	 * Uses pagination.
	 *
     * @param user the current user
     * @param request the servlet request
	 * @param response the servlet response
	 */
	private void listEvents(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
			return;

		Integer pageSize = Utils.stringToInteger(request.getParameter("page_size"));
		Integer page = Utils.stringToInteger(request.getParameter("page"));
        String query = request.getParameter("query");

		if(pageSize == null || page == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid page or page_size parameter");
			return;
		}

        List<EventLog> list = EventLog.getExtract(page * pageSize, pageSize, query);

		JSONArray array = new JSONArray();
		for (EventLog u : list)
			array.put(u.toJSON(true));
		
		JSONObject res = new JSONObject();
        res.put("total_count", EventLog.count(query));
		res.put("count", list.size());
		res.put("list", array);

		ServletUtils.sendJSONResponse(response, res);
	}
	
	/**
	 * Return the list of the last error logs.
	 * 
	 * See doc GET /api/log/errors.
	 * Allow the user to query specific terms to search the error logs.
	 * Uses pagination.
     * @param user the current user
	 * @param request the servlet request
	 * @param response the servlet response
	 */
	private void listErrors(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
			return;

		Integer pageSize = Utils.stringToInteger(request.getParameter("page_size"));
		Integer page = Utils.stringToInteger(request.getParameter("page"));
        String query = request.getParameter("query");

		if(pageSize == null || page == null) {
			ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid page or page_size parameter");
			return;
		}

        List<ErrorLog> list = ErrorLog.getExtract(page * pageSize, pageSize, query);

		JSONArray array = new JSONArray();
		for (ErrorLog u : list)
			array.put(u.toJSON(true));
		
		JSONObject res = new JSONObject();
        res.put("total_count", ErrorLog.count(query));
		res.put("count", list.size());
		res.put("list", array);

		ServletUtils.sendJSONResponse(response, res);
	}

}
