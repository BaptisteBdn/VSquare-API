package fr.eseo.vsquare.servlet;

import fr.eseo.vsquare.model.*;
import fr.eseo.vsquare.model.EventLog.EventAction;
import fr.eseo.vsquare.utils.*;
import fr.klemek.betterlists.BetterArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Servlet implementation class APIGroupsServlet.
 *
 * @author Clement Loiselet
 */
@WebServlet(name = "groups", urlPatterns = {"/group/*", "/groups"})
public class GroupServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public GroupServlet() {
        super();
    }

    /**
     * Service at /api/groups or /api/group/*.
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            User user = ServletUtils.verifyToken(request, response);
            if (user == null)
                return;
            LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
            map.put("GET /api/groups", () -> getGroupsForUser(user, request, response));
            map.put("GET /api/group/{}", () -> getGroupDetails(request, response));
            map.put("GET /api/group/{}/vm", () -> getGroupVmList(user, request, response));
            map.put("POST /api/group", () -> createGroup(user, request, response));
            map.put("POST /api/group/{}", () -> editGroup(user, request, response));
            map.put("DELETE /api/group/{}", () -> deleteGroup(user, request, response));
            map.put("PUT /api/group/{}/user/{}", () -> addUserToGroup(user, request, response));
            map.put("DELETE /api/group/{}/user/{}", () -> removeUserFromGroup(user, request, response));
            map.put("GET /api/group/{}/permission", () -> getGroupPermission(user, request, response));
            map.put("POST /api/group/{}/permission", () -> createGroupPermission(user, request, response));
            map.put("DELETE /api/group/{}/permission", () -> resetGroupPermission(user, request, response));
            map.put("GET /api/group/{}/networks", () -> getGroupNetworks(user, request, response));
            map.put("PUT /api/group/{}/network/{}", () -> addGroupNetwork(user, request, response));
            map.put("DELETE /api/group/{}/network/{}", () -> deleteGroupNetwork(user, request, response));
            map.put("PUT /api/group/{}/template/{}", () -> addTemplateToGroup(user, request, response));
            map.put("DELETE /api/group/{}/template/{}", () -> deleteTemplateFromGroup(user, request, response));
            map.put("GET /api/group/{}/templates", () -> getGroupTemplateList(user, request, response));
            map.put("POST /api/group/{}/power/{}", () -> setPowerAll(user, request, response));
            map.put("DELETE /api/group/{}/reset/users", () -> resetGroupsUsers(user, request, response));
            map.put("DELETE /api/group/{}/reset/vms", () -> resetGroupsVms(user, request, response));
            ServletUtils.mapRequest(request, response, map);
        } catch (Exception e) {
            Logger.log(Level.SEVERE, e.toString(), e);
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Return the groups of the user.
     * <p>
     * See doc GET /api/groups.
     *
     * @param user     the user who made the request
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void getGroupsForUser(User user, HttpServletRequest request, HttpServletResponse response) {
        Set<Group> groups = user.getGroupsForUser();

        boolean details = Boolean.parseBoolean(request.getParameter("details"));
        // convert to json quickly
        JSONArray json = new JSONArray();
        for (Group group : groups) {
            json.put(group.toJSON(details));
        }

        ServletUtils.sendJSONResponse(response, json);
    }

    /**
     * Return detailed information about the group.
     * <p>
     * See doc GET /api/group/{}.
     *
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void getGroupDetails(HttpServletRequest request, HttpServletResponse response) {
        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null) {
            return;
        }

        ServletUtils.sendJSONResponse(response, group.toJSON(true));
    }

    /**
     * Return a list of all the VM associated to the group.
     * <p>
     * See doc GET /api/group/{}/vm.
     *
     * @param user     the user who made the request
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void getGroupVmList(User user, HttpServletRequest request, HttpServletResponse response) {
        if (user.isStudent()) {
        	
            ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null) {
            return;
        }

        BetterArrayList<Vm> vmList = new BetterArrayList<>();

        Set<User> groupUsers = group.getUsers();
        group.getAllChildren().forEach(g -> groupUsers.addAll(g.getUsers()));
        groupUsers.forEach(u -> {
            if (user.isAdmin() || u.getType().lesserThan(user.getType())) {
                vmList.addAll(u.getVms());
            }
        });

        JSONObject json = VSphereConnector.getVmList(vmList);

        ServletUtils.sendVSphereResponse(response, json);
    }

    /**
     * Creates a new group.
     * <p>
     * Creates a new group with the name, the description and a parent group.
     * <p>
     * See doc POST /api/group.
     *
     * @param user     the user who made the request
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void createGroup(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;

        String name = request.getParameter("name");
        String description = request.getParameter("description");
        Integer idParent = Utils.stringToInteger(request.getParameter("id_parent_group"));

        if (name == null || description == null || idParent == null) {
            ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing name or description or idParent parameter");
            return;
        }

        Group group = new Group(name, description, idParent);
        if (group.saveOrUpdate()) {
            EventLog.log(user, EventAction.CREATE, group);
            ServletUtils.sendJSONResponse(response, group.toJSON(true));
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Creation failed");
            ErrorLog.log(user, "Group creation failed");
        }
    }

    /**
     * Edit information about the group.
     * <p>
     * See doc POST /api/group/{}.
     *
     * @param user     the user who made the request
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void editGroup(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null) {
            return;
        }

        String name = request.getParameter("name");
        String description = request.getParameter("description");
        Integer idParent = Utils.stringToInteger(request.getParameter("id_parent_group"));

        if (name != null) {
            group.setName(name);
        }
        if (description != null) {
            group.setDescription(description);
        }
        if (idParent != null) {
            group.setIdParentGroup(idParent);
        }

        if (group.saveOrUpdate()) {
            EventLog.log(user, EventAction.EDIT, group);
            ServletUtils.sendJSONResponse(response, group.toJSON());
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Edition failed - server side problem");
            ErrorLog.log(user, String.format("Group %s : edition failed", group.getName()));
        }
    }

    /**
     * Delete the group.
     * <p>
     * See doc DELETE /api/group/{}.
     *
     * @param user     the user who made the request
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void deleteGroup(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null) {
            return;
        }

        if (group.isDefaultGroup()) {
            ServletUtils.sendError(response, HttpServletResponse.SC_FORBIDDEN, "You cannot delete default group");
            return;
        }

        EventLog.log(user, EventAction.DELETE, group); // group need to have an id

        if (group.delete()) {

            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("success", true);
            ServletUtils.sendJSONResponse(response, jsonResponse);
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Deletion failed - server side problem");
            ErrorLog.log(user, String.format("Group %s : deletion failed", group.getName()));
        }
    }

    /**
     * Add the user to the group.
     * <p>
     * See doc PUT /api/group/{}/user/{}.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void addUserToGroup(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        User user = ServletUtils.getObjectFromRequest(request, response, 5, User.class);

        if (group == null || user == null) {
            return;
        }

        Group tmp = group;
        while (tmp != null) {
            if (tmp.getUsers().contains(user))
                user.removeGroup(tmp);
            tmp = tmp.getParent();
        }

        user.addGroup(group);
        user.checkGroups();
        if (user.saveOrUpdate()) {
            EventLog.log(callingUser, EventAction.EDIT, group);
            ServletUtils.sendOK(response);
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorLog.log(callingUser, "Cannot add user '" + user.getLogin() + "' to group '" + group.getName() + "'");
        }
    }

    /**
     * Delete the user from the group.
     * <p>
     * See doc DELETE /api/group/{}/user/{}.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void removeUserFromGroup(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        User user = ServletUtils.getObjectFromRequest(request, response, 5, User.class);

        if (group == null || user == null) {
            return;
        }

        user.removeGroup(group);
        user.checkGroups();
        if (user.saveOrUpdate()) {
            EventLog.log(callingUser, EventAction.EDIT, group);
            ServletUtils.sendOK(response);
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorLog.log(callingUser,
                    "Cannot remove user '" + user.getLogin() + "' from group '" + group.getName() + "'");
        }
    }

    /**
     * Return the permissions associated with this group.
     * <p>
     * See doc GET /api/group/{}/permission.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void getGroupPermission(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null) {
            return;
        }

        ServletUtils.sendJSONResponse(response, group.getEffectivePermission().toJSON());
    }

    /**
     * Edit the permission associated with the group.
     * <p>
     * See doc POST /api/group/{}/permission.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */

    private void createGroupPermission(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null)
            return;

        Integer vmCount = Utils.stringToInteger(request.getParameter("vm_count"));
        Integer cpuCount = Utils.stringToInteger(request.getParameter("cpu_count"));
        Integer memorySize = Utils.stringToInteger(request.getParameter("memory_size"));
        Integer diskStorage = Utils.stringToInteger(request.getParameter("disk_storage"));

        Permission permission = group.getEffectivePermission();

        permission.setVmCount(vmCount != null ? vmCount : permission.getVmCount());
        permission.setCpuCount(cpuCount != null ? cpuCount : permission.getCpuCount());
        permission.setMemorySize(memorySize != null ? memorySize : permission.getMemorySize());
        permission.setDiskStorage(diskStorage != null ? diskStorage : permission.getDiskStorage());

        if (!permission.isValid()) {
            ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Operation failed - invalid parameter");
            return;
        }

        group.setPermission(permission);

        if (permission.saveOrUpdate() && group.saveOrUpdate()) {
            Logger.log(Level.INFO, permission.toJSON().toString());
            EventLog.log(callingUser, EventAction.EDIT, group);
            updatePermission(group);
            ServletUtils.sendJSONResponse(response, permission.toJSON());
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorLog.log(callingUser, "Group permission'" + group.getName() + "' : edition/creation failed");
        }
    }

    /**
     * Delete the permission associated with the group.
     * <p>
     * Note that the permission of this group will be deleted but the effective
     * permission for users will be the minimal values as set in the config file.
     * <p>
     * See doc DELETE /api/group/{}/permission.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */

    private void resetGroupPermission(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null) {
            return;
        }

        group.setPermission(null);

        if (group.saveOrUpdate()) {
            EventLog.log(callingUser, EventAction.EDIT, group);
            updatePermission(group);
            ServletUtils.sendJSONResponse(response, group.getEffectivePermission().toJSON());
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Operation failed - server side problem");
            ErrorLog.log(callingUser, "Group permission'" + group.getName() + "' : deletion failed");
        }
    }

    private void updatePermission(Group group) {
        for (User user : group.getUsers(true)) {
            Permission permission = user.getEffectivePermission();
            if (user.getPrivateNetwork() != null) {
                int vmCount = user.getVms().size();
                VCenterManager.editNetwork(user.getPrivateNetwork(), Math.max(permission.getVmCount(), vmCount));
            }
            for (Vm vm : user.getVms())
                updatePermissionVm(permission, vm);
        }
    }

    private void updatePermissionVm(Permission permission, Vm vm) {
        JSONObject json = null;
        try {
            json = VSphereConnector.getVmDetails(vm);
            if (json != null) {
                json = json.getJSONObject("value");
                String state = json.getString("power_state");
                int cpuCount = json.getJSONObject("cpu").getInt("count");
                int memorySize = json.getJSONObject("memory").getInt("size_MiB");

                if (state.equals("POWERED_ON") &&
                        (cpuCount > permission.getCpuCount() || memorySize > permission.getMemorySize()))
                    VCenterManager.shutdownVM(vm);

                if (cpuCount > permission.getCpuCount())
                    VSphereConnector.updateCpu(vm, cpuCount);
                if (memorySize > permission.getMemorySize())
                    VSphereConnector.updateRam(vm, memorySize);
            }
        } catch (JSONException e) {
            Logger.log(Level.WARNING, "Malformed JSON for VM " + vm.getIdVmVcenter() + " : " + e.toString() + " " + json);
        }
    }

    /**
     * Set the power of all the VMs the admin has access to.
     *
     * @param user     the user who made the request
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void setPowerAll(User user, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(user, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        if (group == null)
            return;

        String[] path = request.getRequestURI().split("/");
        String action = path[5];

        if (!Arrays.asList("start;reset;stop;suspend".split(";")).contains(action)) {
            ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Bad action");
            return;
        }

        List<Vm> vms = BetterArrayList.fromList(group.getUsers(true)).selectMany(User::getVms);
        int vmAffected = VSphereConnector.setVmPowerAll(user, action, vms);

        JSONObject output = new JSONObject();
        output.put("total_vms", vms.size());
        output.put("total_success", vmAffected);

        EventLog.log(user, EventLog.getActionFromVmPower(action), user); // using self to indicate all vms

        ServletUtils.sendJSONResponse(response, output);
    }


    /**
     * Return the list of networks of the group.
     * <p>
     * See doc GET /api/group/{}/networks.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void getGroupNetworks(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        if (group == null)
            return;

        Set<Network> networks = group.getNetworks();
        Set<Network> inheritedNetworks = group.getParent() != null ? group.getParent().getAvailableNetworks() : null;

        JSONArray output = new JSONArray();
        if (networks != null)
            for (Network n : networks) {
                JSONObject json = n.toJSON();
                json.put("inherited", false);
                output.put(json);
            }
        if (inheritedNetworks != null)
            for (Network n : inheritedNetworks) {
                JSONObject json = n.toJSON();
                json.put("inherited", true);
                output.put(json);
            }

        ServletUtils.sendJSONResponse(response, output);
    }

    /**
     * Add a network to the group.
     * <p>
     * See doc PUT /api/group/{}/network/{}.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void addGroupNetwork(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        if (group == null)
            return;

        Network network = ServletUtils.getObjectFromRequest(request, response, 5, Network.class);
        if (network == null)
            return;

        network.addGroup(group);

        if (network.saveOrUpdate()) {
            EventLog.log(callingUser, EventAction.EDIT, network);
            ServletUtils.sendOK(response);
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Operation failed - server side problem");
            ErrorLog.log(callingUser, "Group'" + group.getName() + "' : cannot add network");
        }
    }

    /**
     * Delete the network from the group.
     * <p>
     * See doc DELETE /api/group/{}/network/{}.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void deleteGroupNetwork(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        if (group == null)
            return;

        Network network = ServletUtils.getObjectFromRequest(request, response, 5, Network.class);
        if (network == null)
            return;

        if (!network.getGroups().contains(group)) {
            ServletUtils.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "this network doesn't belong to this group");
            return;
        }

        network.removeGroup(group);

        if (network.saveOrUpdate()) {
            EventLog.log(callingUser, EventAction.EDIT, network);
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("success", true);
            ServletUtils.sendJSONResponse(response, jsonResponse);
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Deletion failed - server side problem");
            ErrorLog.log(callingUser,
                    "Group '" + group.getName() + "' network '" + network.getName() + "' : deletion failed");
        }
    }

    /**
     * Add the template to the group.
     * <p>
     * See doc POST /api/group/{}/template{}.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void addTemplateToGroup(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        Vm vm = ServletUtils.getVmFromRequest(request, response, 5, callingUser, true);

        if (group == null || vm == null)
            return;

        group.addTemplate(vm);

        if (group.saveOrUpdate()) {
            EventLog.log(callingUser, EventAction.EDIT, group);
            ServletUtils.sendOK(response);
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorLog.log(callingUser, "Cannot add template '" + vm.getIdVmVcenter() + "' to group '" + group.getName() + "'");
        }
    }

    /**
     * Delete the template from the group.
     * <p>
     * See doc DELETE /api/group/{}/template{}.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void deleteTemplateFromGroup(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        Vm vm = ServletUtils.getVmFromRequest(request, response, 5, callingUser, true);

        if (group == null || vm == null)
            return;

        group.removeTemplate(vm);

        if (group.saveOrUpdate()) {
            EventLog.log(callingUser, EventAction.EDIT, group);
            ServletUtils.sendOK(response);
        } else {
            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorLog.log(callingUser, "Cannot add template '" + vm.getIdVmVcenter() + "' to group '" + group.getName() + "'");
        }
    }

    /**
     * Return a list of all the template associated to the group.
     * <p>
     * See doc GET /api/group/{}/template.
     *
     * @param request  the servlet request
     * @param response the servlet response
     */
    private void getGroupTemplateList(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group group = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        if (group == null)
            return;

        BetterArrayList<Vm> templateList = BetterArrayList.fromList(group.getAvailableTemplates());

        JSONObject json = VSphereConnector.getTemplateList(templateList);

        ServletUtils.sendVSphereResponse(response, json);
    }

    /**
     * Reset the group and every child group users.
     * <p>
     * Remove students associated with the group and every child group.
     * <p>
     * See doc DELETE /api/group/{}/reset/users.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     */
    private void resetGroupsUsers(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group callingGroup = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);
        for (User user : callingGroup.getUsers(true)) {
            user.removeGroup(callingGroup);
            user.saveOrUpdate();
        }
        ServletUtils.sendOK(response);
    }

    /**
     * Reset the group and every child group VMs.
     * <p>
     * Remove students VMs associated with the group and every child group.
     * <p>
     * See doc DELETE /api/group/{}/reset/vms.
     *
     * @param callingUser the user who made the request
     * @param request     the servlet request
     * @param response    the servlet response
     * @throws InterruptedException
     * @throws RemoteException
     */
    private void resetGroupsVms(User callingUser, HttpServletRequest request, HttpServletResponse response) {
        if (!ServletUtils.checkUserRight(callingUser, response))
            return;

        Group callingGroup = ServletUtils.getObjectFromRequest(request, response, 3, Group.class);

        try {
            for (Vm vm : BetterArrayList.fromList(callingGroup.getUsers(true)).selectMany(User::getVms)) {
                VCenterManager.shutdownVM(vm);
                VCenterManager.deleteVm(vm);
                vm.delete();
            }
        } catch (InterruptedException | RemoteException e) {

            ServletUtils.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorLog.log(callingUser, "Could not reset groups Vms");
            return;
        }

        ServletUtils.sendOK(response);
    }


}
