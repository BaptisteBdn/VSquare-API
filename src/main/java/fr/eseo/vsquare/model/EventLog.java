package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.DatabaseManager;
import fr.eseo.vsquare.utils.Logger;
import org.json.JSONObject;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * Event Log object model.
 * <p>
 * Implements the event log object class used to log every action done on VSquare.
 *
 * @author Clement Gouin
 */
@Entity
@Table(name = "event_log")
public class EventLog extends VSquareObject {

    public enum EventAction {
        CREATE, EDIT, DELETE, POWER_ON, POWER_OFF, RESET, SUSPEND, CLONE, EXPORT, IMPORT
    }

    public enum EventObject {
        USER, GROUP, VM, NETWORK
    }


    // region Variables

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_user")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private EventAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type")
    private EventObject objectType;

    @Column(name = "object_id")
    private int objectId;

    @Column(name = "object_name")
    private String objectName;

    // endregion

    // region Constructors

    public EventLog() {

    }

    /**
     * Constructor
     *
     * @param user   the user that did the action
     * @param action the action made
     * @param object the object affected by the change
     */
    public EventLog(User user, EventAction action, VSquareObject object) {
        super();
        this.user = user;
        this.action = action;

        Integer tmpId = null;
        if (object instanceof User) {
            User u = (User) object;
            tmpId = u.getId();
            this.objectName = u.getCommonName();
            this.objectType = EventObject.USER;
        } else if (object instanceof Group) {
            Group g = (Group) object;
            tmpId = g.getId();
            this.objectName = g.getName();
            this.objectType = EventObject.GROUP;
        } else if (object instanceof Vm) {
            Vm v = (Vm) object;
            tmpId = v.getId();
            this.objectName = v.getName();
            this.objectType = EventObject.VM;
        } else if (object instanceof Network) {
            Network n = (Network) object;
            tmpId = n.getId();
            this.objectName = n.getName();
            this.objectType = EventObject.NETWORK;
        }

        if (tmpId == null) //cannot have null id
            throw new IllegalArgumentException("Event object is invalid");

        this.objectId = tmpId;
    }


    // endregion

    // region Accessors

    /**
     * @return the user at the origin of the event
     */
    public User getUser() {
        return user;
    }

    /**
     * @return the type of action
     */
    public EventAction getAction() {
        return action;
    }

    /**
     * @return the id of the concerned object
     */
    public int getObjectId() {
        return objectId;
    }

    // endregion

    // region Functions


    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * @return this event object
     */
    public VSquareObject getObject() {
        switch (objectType) {
            case GROUP:
                return Group.findById(objectId);
            case USER:
                return User.findById(objectId);
            case VM:
                return Vm.findById(objectId);
            case NETWORK:
                return Network.findById(objectId);
        }
        return null;
    }

    /**
     * @return all logs from the table
     */
    public static List<EventLog> getAll() {
        return VSquareObject.getAll(EventLog.class);
    }

    /**
     * Return a list of the last event logs.
     *
     * @param start the starting log
     * @param max   the size of the list
     * @param query the string to find
     * @return an extract of the logs (latest first)
     */
    public static List<EventLog> getExtract(int start, int max, String query) {
        if (query == null)
            return DatabaseManager.getRowsFromSessionQueryNamed("FROM EventLog ORDER BY creation_date DESC", start, max, new HashMap<>());
        HashMap<String, Object> params = new HashMap<>();

        String sqlQuery = getQuery(query, params);
        if (sqlQuery == null)
            return new ArrayList<>();

        return DatabaseManager.getRowsFromSessionQueryNamed("" +
                "SELECT el " + sqlQuery, start, max, params);
    }

    /**
     * @param query the string to find
     * @return the total count of all logs
     */
    public static long count(String query) {
        if (query == null)
            return DatabaseManager.getFirstFromSessionQuery("SELECT count(*) FROM EventLog");

        HashMap<String, Object> params = new HashMap<>();

        String sqlQuery = getQuery(query, params);
        if (sqlQuery == null)
            return 0;

        return DatabaseManager.getFirstFromSessionQueryNamed("SELECT count(*) " + sqlQuery, params);
    }

    /**
     * Get the HQL query to be used in DatabaseManager.
     *
     * @param query  the string to search
     * @param params the query parameters
     * @return the query starting from "FROM" or null on error
     */
    private static String getQuery(String query, HashMap<String, Object> params) {
        final String queryParameter = "query";

        if (query.startsWith("user:")) {
            query = query.substring("user:".length());
            params.put(queryParameter, "%" + query.toUpperCase() + "%");
            return "FROM EventLog el LEFT JOIN el.user u WHERE el.objectType = 'USER' AND upper(el.objectName) LIKE :query OR upper(u.commonName) LIKE :query OR upper(u.login) LIKE :query ORDER BY el.creationDate DESC";
        }

        if (query.startsWith("action:")) {
            query = query.substring("action:".length());
            EventAction eventAction = parseEventAction(query);
            if (eventAction != null) {
                params.put(queryParameter, eventAction);
                return "FROM EventLog el WHERE el.action = :query ORDER BY el.creationDate DESC";
            } else {
                return null;
            }
        }

        if (query.startsWith("vm:")) {
            query = query.substring("vm:".length());
            params.put(queryParameter, "%" + query.toUpperCase() + "%");
            return "FROM EventLog el WHERE el.objectType = 'VM' AND upper(el.objectName) LIKE :query ORDER BY el.creationDate DESC";
        }

        if (query.startsWith("group:")) {
            query = query.substring("group:".length());
            params.put(queryParameter, "%" + query.toUpperCase() + "%");
            return "FROM EventLog el WHERE el.objectType = 'GROUP' AND upper(el.objectName) LIKE :query ORDER BY el.creationDate DESC";
        }

        params.put(queryParameter, "%" + query.toUpperCase() + "%");
        return "FROM EventLog el LEFT JOIN el.user u WHERE upper(el.objectName) LIKE :query OR upper(u.commonName) LIKE :query OR upper(u.login) LIKE :query ORDER BY el.creationDate DESC";
    }

    @Override
    public JSONObject toJSON(boolean detailed) {
        JSONObject json = super.toJSON(true);
        json.put("user", getUser().toJSON());
        json.put("action", action.toString());

        JSONObject jsonObject = new JSONObject();
        if (detailed) {
            VSquareObject vSquareObject = getObject();
            if (vSquareObject != null)
                jsonObject = vSquareObject.toJSON();
        }
        jsonObject.put("id", objectId);
        jsonObject.put("name", objectName);
        jsonObject.put("object", objectType.toString());

        json.put("object", jsonObject);

        return json;
    }

    /**
     * Log an event.
     *
     * @param user   the event user
     * @param action the action
     * @param object the object of the action
     */
    public static void log(User user, EventAction action, VSquareObject object) {
        Logger.log(Level.INFO, "{0} ({1}) : {2} -> {3}", user.getLogin(), user, action.toString(), object);
        new EventLog(user, action, object).saveOrUpdate();
    }

    public static EventAction getActionFromVmPower(String action) {
        switch (action) {
            case "start":
                return EventAction.POWER_ON;
            case "stop":
                return EventAction.POWER_OFF;
            case "reset":
                return EventAction.RESET;
            case "suspend":
                return EventAction.SUSPEND;
            default:
                return EventAction.EDIT;
        }
    }

    /**
     * Return the EventAction object after parsing the string.
     *
     * @param eventAction the String to parse
     * @return the parsed value or null
     */
    private static EventAction parseEventAction(String eventAction) {
        for (EventAction ea : EventAction.values()) {
            if (ea.toString().equalsIgnoreCase(eventAction))
                return ea;
        }
        return null;
    }

    // endregion

}
