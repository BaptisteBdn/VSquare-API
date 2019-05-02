package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.ErrorLog;
import fr.eseo.vsquare.model.EventLog;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.Logger;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
import fr.klemek.betterlists.BetterArrayList;
import fr.klemek.betterlists.BetterList;
import org.json.JSONArray;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.logging.Level;

/**
 * Servlet implementation class UserServlet.
 *
 * @author Clement Gouin
 */
@WebServlet("/user/*")
public class UserServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public UserServlet() {
        super();
    }

    /**
     * Service at /api/user/*
     * 
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            final User user = ServletUtils.verifyToken(request, response);
            if (user == null)
                return;
            LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
            map.put("GET /api/user", () -> listUsers(user, request, response));
            map.put("GET /api/user/{}", () -> getUserInfo(request, response));
            map.put("POST /api/user/{}", () -> editUserInfo(user, request, response));
            map.put("PUT /api/user/action/ldap", () -> scrapLDAP(user, response));
            ServletUtils.mapRequest(request, response, map);
        } catch (Exception e) {
            Logger.log(Level.SEVERE, e.toString(), e);
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Return the list of all the users.
     * 
     * See doc GET /api/user.
     *
     * @param user the user who made the request
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void listUsers(User user, HttpServletRequest request, HttpServletResponse response) {
        if (user.isStudent()) {
            ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        BetterList<User> list = BetterArrayList.fromList(User.getAll());
        if (!user.isAdmin()) {
            list = list.where(u -> u.getType().lesserThan(user.getType()));
        }

        String query = request.getParameter("query");
        if (query != null) {
            list = list.where(u -> Utils.containsIgnoreCase(u.getCommonName(), query)
                    || Utils.containsIgnoreCase(u.getLogin(), query));
        }

        JSONArray array = new JSONArray();
        for (User u : list)
            array.put(u.toJSON(false));

        ServletUtils.sendJSONResponse(response, array);
    }

    /**
     * Return the user information.
     * 
     * See doc GET /api/user/{}.
     *
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void getUserInfo(HttpServletRequest request, HttpServletResponse response) {
        User user = ServletUtils.getObjectFromRequest(request, response, 3, User.class);
        if (user == null)
            return;

        ServletUtils.sendJSONResponse(response, user.toJSON(true));
    }

    /**
     * Edit the user information.
     * 
     * See doc POST /api/user/{}.
     *
     * @param callingUser the current user
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void editUserInfo(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        User user = ServletUtils.getObjectFromRequest(request, response, 3, User.class);
        if (user == null)
            return;

        if (!callingUser.isAdmin()) {
            ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String type = request.getParameter("type");
        if (type != null) {
            if (user.equals(callingUser)) {
                ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN, "You cannot change your user type");
                return;
            }
            UserType userType = User.parseType(type);
            if (userType == null) {
                ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid user type");
                return;
            }
            user.setType(userType);
            user.checkGroups();
        }

        if (!user.saveOrUpdate()) {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot edit user");
            ErrorLog.log(callingUser, "User '" + user.getLogin() + "' : edition failed");
            return;
        }

        EventLog.log(callingUser, EventAction.EDIT, user);

        ServletUtils.sendJSONResponse(response, user.toJSON(true));
    }

    /**
     * Return the ussers from in the LDAP.
     * 
     * See doc PUT /api/user/ldap.
     *
     * @param user     the calling user
     * @param response the servlet response
     */
    private void scrapLDAP(User user, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;
        Utils.createLDAPUsers();
        ServletUtils.sendOK(response);
    }


}
