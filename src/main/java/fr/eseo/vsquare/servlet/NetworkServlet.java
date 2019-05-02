package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.ErrorLog;
import fr.eseo.vsquare.model.EventLog;
import fr.eseo.vsquare.model.Network;
import fr.eseo.vsquare.model.User;
import fr.eseo.vsquare.utils.Logger;
import fr.eseo.vsquare.utils.ServletUtils;
import fr.eseo.vsquare.utils.Utils;
import fr.eseo.vsquare.utils.VCenterManager;
import org.json.JSONArray;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Servlet implementation class NetworkServlet.
 *
 * @author Clement Gouin
 */
@WebServlet(name = "networks", urlPatterns = {"/network/*", "/networks"})
public class NetworkServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public NetworkServlet() {
        super();
    }

    /**
     * Service at /api/networks or /api/network/*.
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) {
        try {
            final User user = ServletUtils.verifyToken(request, response);
            if (user == null)
                return;
            LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
            map.put("GET /api/networks", () -> getAvailableNetworks(user, request, response));
            map.put("PUT /api/networks", () -> addNetwork(user, request, response));
            map.put("POST /api/network/{}", () -> editNetwork(user, request, response));
            map.put("DELETE /api/network/{}", () -> deleteNetwork(user, request, response));
            ServletUtils.mapRequest(request, response, map);
        } catch (Exception e) {
            Logger.log(Level.SEVERE, e.toString(), e);
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Return available networks for the user.
     * <p>
     * See doc GET /api/networks.
     *
     * @param user     the calling user
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void getAvailableNetworks(User user, HttpServletRequest request, HttpServletResponse response) {
        JSONArray output = new JSONArray();
        boolean details = user.isAdmin() && request.getParameter("details") != null && request.getParameter("details").equalsIgnoreCase("true");
        for (Network network : user.isAdmin() ? Network.getAll() : user.getAvailableNetworks()) {
            output.put(network.toJSON(details));
            if (details) {
                output.getJSONObject(output.length() - 1).put("port_num",
                        VCenterManager.getNetworkMaxPorts(network.getIdNetworkVcenter()));
            }
        }

        ServletUtils.sendJSONResponse(response, output);
    }

    /**
     * Create a new network.
     * <p>
     * See doc PUT /api/networks.
     *
     * @param user     the calling user
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void addNetwork(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;

        Map<String, String> params = ServletUtils.readParameters(request);
        String name = params.get("name");
        Integer portNum = Utils.stringToInteger(params.get("port_num"));
        if (name == null || portNum == null) {
            ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String netId = VCenterManager.createNetwork(Utils.getString("vsphere_main_dswitch"), name, portNum, false);

        if (netId == null) {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Creation failed");
            return;
        }

        Network newNetwork = new Network(name, netId);
        if (newNetwork.saveOrUpdate()) {
            EventLog.log(user, EventLog.EventAction.CREATE, newNetwork);
            ServletUtils.sendJSONResponse(response, newNetwork.toJSON(true));
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Creation failed");
            ErrorLog.log(user, "Network creation failed");
        }

    }

    /**
     * Edit the network.
     * <p>
     * See doc POST /api/network/{}.
     *
     * @param user     the calling user
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void editNetwork(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;

        Network network = ServletUtils.getObjectFromRequest(request, response, 3, Network.class);
        if (network == null)
            return;

        String name = request.getParameter("name");
        if (name != null) {
            network.setName(name);
            if (!network.saveOrUpdate()) {
                ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot edit network");
                return;
            }
        }

        Integer portNum = Utils.stringToInteger(request.getParameter("port_num"));
        if (portNum != null && !VCenterManager.editNetwork(network.getIdNetworkVcenter(), portNum)) {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot edit network");
            return;
        }

        EventLog.log(user, EventLog.EventAction.EDIT, network);

        ServletUtils.sendOK(response);
    }

    /**
     * Delete the network.
     * <p>
     * See doc DELETE /api/network/{}.
     *
     * @param user     the calling user
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void deleteNetwork(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;

        Network network = ServletUtils.getObjectFromRequest(request, response, 3, Network.class);
        if (network == null)
            return;

        EventLog.log(user, EventLog.EventAction.DELETE, network);

        if (!VCenterManager.deleteNetwork(network.getIdNetworkVcenter()) || !network.delete()) {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot delete network");
            return;
        }

        ServletUtils.sendOK(response);
    }
}
