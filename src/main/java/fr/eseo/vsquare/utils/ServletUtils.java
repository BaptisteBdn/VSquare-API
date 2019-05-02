package fr.eseo.vsquare.utils;

import fr.eseo.vsquare.model.Token;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.model.VSquareObject;
import fr.eseo.vsquare.model.Vm;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Utility class that store useful servlet functions.
 *
 * @author Clement Gouin
 */
public final class ServletUtils {

    public static final String VALUE_KEY = "value";
    public static final String CAPACITY_KEY = "capacity";
    public static final String DISKS_KEY = "disks";

    private static boolean bruteForceSecurity = true;

    private static HashMap<Long, User> currentUsers = new HashMap<>();
    private static HashMap<Long, String> currentRequests = new HashMap<>();

    private ServletUtils() {
    }

    /**
     * Compute an http code into a message.
     *
     * @param code the http code
     * @return the message of the http code
     */
    private static String getError(int code) {
        switch (code) {
            case HttpServletResponse.SC_BAD_REQUEST:
                return "Bad request - a parameter MAY be missing";
            case HttpServletResponse.SC_UNAUTHORIZED:
                return "Unauthorized - authentication needed";
            case HttpServletResponse.SC_FORBIDDEN:
                return "Forbidden - Insufficient rights";
            case HttpServletResponse.SC_NOT_FOUND:
                return "The resource was not found on the server";
            case HttpServletResponse.SC_INTERNAL_SERVER_ERROR:
                return "An error has occurred, check server logs for more details";
            default:
                return "Unknown error";
        }
    }

    /**
     * Send the content via the response.
     *
     * @param response the servlet response
     * @param code     the http status
     * @param result   the json object sent
     */
    private static void sendContent(HttpServletResponse response, int code, JSONObject result) {
        response.setStatus(code);
        if (!result.has(VALUE_KEY)) {
            JSONObject temp = result;
            result = new JSONObject();
            result.put(VALUE_KEY, temp);
        }
        result.put("code", code);
        response.setHeader("Content-Type", "application/json");
        try {
            response.getWriter().print(result.toString());
            response.flushBuffer();
        } catch (IOException e) {
            Logger.log(Level.SEVERE, e.toString(), e);
        }

    }

    /**
     * Send an error via the response and log it.
     *
     * @param response the servlet response
     * @param code     the code of the error
     */

    public static void sendError(HttpServletResponse response, int code) {
        sendError(response, code, null);
    }

    /**
     * Send an error via the response and log it.
     *
     * @param response the servlet response
     * @param code     the code of the error
     * @param message  the message of the error
     */
    public static void sendError(HttpServletResponse response, int code, String message) {
        String className = ServletUtils.class.getSimpleName();
        String source;
        int stackTraceLevel = 3;
        do {
            source = Utils.getCallingClassName(stackTraceLevel++);
        } while (source != null && source.equals(className));
        if (code != HttpServletResponse.SC_UNAUTHORIZED)
            Logger.log(source, Level.WARNING, "Error {0} sent : {1} (request : {2})", code, message == null ? "" : ": " + message, getCurrentRequest());
        JSONObject error = new JSONObject();
        error.put("error", message == null ? getError(code) : message);
        sendContent(response, code, error);
    }

    /**
     * Send a JSONObject as a response.
     *
     * @param response the servlet response
     * @param result   the json object to send
     */
    public static void sendJSONResponse(HttpServletResponse response, JSONObject result) {
        sendContent(response, HttpServletResponse.SC_OK, result);
    }

    public static void sendOK(HttpServletResponse response) {
        JSONObject json = new JSONObject();
        json.put("success", true);
        sendJSONResponse(response, json);
    }

    /**
     * Send a JSONArray as a response (will be sent as a JSONObject containing a
     * field "value").
     *
     * @param response  the servlet response
     * @param jsonArray the array to send
     */
    public static void sendJSONResponse(HttpServletResponse response, JSONArray jsonArray) {
        JSONObject result = new JSONObject();
        result.put(VALUE_KEY, jsonArray);
        sendContent(response, HttpServletResponse.SC_OK, result);
    }

    /**
     * Handle a cross origin request (OPTIONS) and respond to it.
     *
     * @param request  the servlet request
     * @param response the servlet response
     * @return true if this is a cross origin request and the response should be
     * sent as is
     */
    public static boolean handleCrossOrigin(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", Utils.getString("auth_token_header"));
        if (request.getMethod().equals("OPTIONS")) {
            sendOK(response);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Verify if a token is valid.
     *
     * @param request the servlet request
     * @return the user identified by the token
     */
    public static Token verifyToken(HttpServletRequest request) {
        String tokenHeader = request.getHeader(Utils.getString("auth_token_header"));
        if (tokenHeader == null)
            return null;
        Token token = Token.findByValue(tokenHeader);
        if (token != null && token.getAge() > Utils.getInt("token_max_age")) {
            token.delete();
            return null;
        }
        return token;
    }

    /**
     * Verify if a token is valid.
     *
     * @param request  the servlet request
     * @param response the servlet response
     * @return the api user or null if not valid
     */
    public static User verifyToken(HttpServletRequest request, HttpServletResponse response) {
        if (ServletUtils.handleCrossOrigin(request, response))
            return null;
        Token token = verifyToken(request);
        if (token == null) {
            ServletUtils.sendError(response, HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }
        User user = token.getUser();
        currentUsers.put(Thread.currentThread().getId(), user);
        return user;
	}

	/**
	 * Map a request to the given functions and handle wrong method and invalid url.
     *
     * @param request
     *            the servlet request
     * @param response
     *            the servlet response
     * @param map
     *            the mapping as "METHOD /api/path" - Method to call
     */
    public static void mapRequest(HttpServletRequest request, HttpServletResponse response, Map<String, Runnable> map) {
        if (ServletUtils.handleCrossOrigin(request, response))
            return;
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); //must be rewritten by mapped function
        boolean matchingWrongMethod = false;
        boolean matchingDone = false;

        currentRequests.put(Thread.currentThread().getId(), requestToJSON(request).toString());
		for (Map.Entry<String, Runnable> entry : map.entrySet()) {
			String[] mapping = entry.getKey().split(" ");
            if (mapping.length != 2)
                throw new IllegalArgumentException(String.format("Wrongly mapped URI : '%s'", entry.getKey()));
            if (matchingURI(mapping[1], request.getRequestURI(), 2)) {
                if (request.getMethod().equalsIgnoreCase(mapping[0])) {
                    entry.getValue().run();
                    matchingDone = true;
                    break;
                } else {
					matchingWrongMethod = true;
				}
			}
		}
        if (!matchingDone) {
            if (matchingWrongMethod) {
                ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid method");
            } else {
                ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Invalid api url");
            }
        }
        currentUsers.remove(Thread.currentThread().getId());
        currentRequests.remove(Thread.currentThread().getId());
    }

    public static Map<String, String> readParameters(HttpServletRequest request) {
        Map<String, String> out = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(request.getInputStream()))) {
            String data = br.readLine();
            if (data != null && data.length() > 0)
                for (String parameter : data.split("&")) {
                    String[] spl = parameter.split("=", 2);
                    if (spl.length == 2)
                        out.put(spl[0].trim(), URLDecoder.decode(spl[1].trim(), "UTF-8"));
                }
        } catch (IOException e) {
            Logger.log(Level.WARNING, "Cannot open input stream on {0} request", request.getMethod());
        }
        return out;
    }

    /**
     * @return the user registered with the thread's servlet
     */
    static User getCurrentUser() {
        return currentUsers.get(Thread.currentThread().getId());
    }

    static String getCurrentRequest() {
        return currentRequests.get(Thread.currentThread().getId());
    }

    private static JSONObject requestToJSON(HttpServletRequest request) {
        JSONObject params = new JSONObject();
        for (Map.Entry<String, String[]> param : request.getParameterMap().entrySet())
            if (param.getValue().length > 0)
                params.put(param.getKey(), param.getValue()[0]);

        JSONObject json = new JSONObject();
        json.put("requested", request.getRequestURI());
        json.put("method", request.getMethod());
        json.put("params", params);

        return json;
    }

	/**
	 * Compare given URI to reference and check if it match.
	 *
     * @param ref
     *            reference URI, can contains '{anything}' as wildcard
     * @param src
     *            URI to check
     * @param ignoreLevel
     *            where to start the comparison
     * @return true if its a match
     */
    public static boolean matchingURI(String ref, String src, int ignoreLevel) {
        String[] refPath = ref.split("/");
        String[] srcPath = src.split("/");
        if (refPath.length != srcPath.length)
            return false;
        for (int i = ignoreLevel; i < refPath.length; i++)
            if (!refPath[i].startsWith("{") && !srcPath[i].equals(refPath[i]))
                return false;
        return true;
    }

    /**
     * Delay request to avoid brute force.
     */
    public static void bruteForceSecurity() {
        if (bruteForceSecurity) {
            //Delay login to avoid brute force
            try {
                Thread.sleep(300);
            } catch (Exception e) {
                Logger.log(Level.WARNING, "Cannot delay login");
            }
        }
    }

    /**
     * Get an object from the requested path.
     *
     * @param request      the servlet request
     * @param response     the servlet response
     * @param pathPosition the id position in the uri path
     * @param objectClass the class to find
     * @param <T> the class to find
     * @return the found object or null if an error was sent in the response
     */
    public static <T extends VSquareObject> T getObjectFromRequest(HttpServletRequest request, HttpServletResponse response, int pathPosition, Class<T> objectClass) {
        String[] path = request.getRequestURI().split("/");
        Integer id = Utils.stringToInteger(path[pathPosition]);
        return getVSquareObject(response, id, true, objectClass);
	}

	/**
     * Get an object from the requested path.
     *
     * @param request      the servlet request
     * @param response     the servlet response
     * @param parameterKey the parameter name
     * @param objectClass the class to find
     * @param <T> the class to find
     * @param sendError to send Error in response
     * @return the found object or null if an error was sent in the response
     */
    public static <T extends VSquareObject> T getObjectFromRequest(HttpServletRequest request, HttpServletResponse response, String parameterKey, boolean sendError, Class<T> objectClass) {
        Integer id = Utils.stringToInteger(request.getParameter(parameterKey));
        return getVSquareObject(response, id, sendError, objectClass);
    }

    /**
     * Get an object by its id or send right error
     *
     * @param response the servlet response
     * @param id       the object's id
     * @param objectClass the class to find
     * @param <T> the class to find
     * @param sendError to send Error in response
     * @return the found object or null if an error was sent in the response
     */
    public static <T extends VSquareObject> T getVSquareObject(HttpServletResponse response, Integer id, boolean sendError, Class<T> objectClass) {
        if (id == null) {
            if (sendError)
                ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Bad Url Arguments");
            return null;
        }
        T object = T.findById(id, objectClass);
        if (object == null) {
            if (sendError)
                ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND,
                        objectClass.toString() + " not found");
            return null;
        }

        return object;
    }

    /**
     * Get a vm from the requested path.
     *
     * @param request      the servlet request
     * @param response     the servlet response
     * @param pathPosition the id position in the uri path
     * @param user         the user associated with the request
     * @param writeAccess  if it is a reading or writing access
     * @return the found vm or null if an error was sent in the response
     */

    public static Vm getVmFromRequest(HttpServletRequest request, HttpServletResponse response, int pathPosition, User user, boolean writeAccess) {
        String[] path = request.getRequestURI().split("/");
        String vmIdVCenter = path[pathPosition];
        Vm vm = Vm.findByIdVmVcenter(vmIdVCenter);
        if (vm == null || vm.getIdVmVcenter() == null) {
            ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND, "VM not found");
            return null;
        }
        if (!writeAccess && !vm.hasAccessRead(user)) {
            ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "You do not possess reading rights on this VM");
            return null;
        }
        if (writeAccess && !vm.hasAccessWrite(user)) {
            ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "You do not possess writing rights on this VM");
            return null;
        }
        return vm;
    }

    /**
     * Verify if the VSphere response is good and send the correct message.
     *
     * @param response the servlet request
     * @param json     the VSphere response
     */
    public static void sendVSphereResponse(HttpServletResponse response, JSONObject json) {
        if (json == null) {
            ServletUtils.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, "VSphere error");
        } else if (json.equals(VSphereConnector.JSON_NOT_FOUND)) {
            ServletUtils.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Resource not found on VSphere");
        } else {
            ServletUtils.sendJSONResponse(response, json);
        }
	}

    public static boolean checkUserRight(User user, HttpServletResponse response) {
        if (!user.isAdmin()) {
            ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
