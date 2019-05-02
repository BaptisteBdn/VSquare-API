package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.DatabaseManager;
import org.json.JSONException;
import org.json.JSONObject;

import javax.persistence.*;
import java.util.HashMap;
import java.util.List;

/**
 * Error Log object model.
 * 
 * Implements the error log object class used to log error happening during execution.
 * 
 * @author Clement Gouin
 */
@Entity
@Table(name = "error_log")
public class ErrorLog extends VSquareObject {
	
	private static final int MAX_ERROR_LENGTH = 65535;
	
	// region Variables

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "id_user")
	private User user;
	
	@Column(name = "error")
	private String error;

    @Column(name = "request")
    private String request;
	
	// endregion

	// region Constructors

	public ErrorLog() {

	}
	
	/**
	 * Constructor 
	 * 
	 * @param user the user creating the error
	 * @param error the stack trace of the error
	 */
	public ErrorLog(User user, String error) {
        this(user, error, null);
    }

	/**
	 * Constructor
	 * 
	 * @param user the user creating the error
	 * @param error the stack trace of the error
	 * @param request the request made by the user
	 */
    public ErrorLog(User user, String error, String request) {
        super();
        this.user = user;
        if (error.length() > MAX_ERROR_LENGTH)
            error = error.substring(0, MAX_ERROR_LENGTH - 3) + "...";
        this.error = error;
        this.request = request;
    }

	
	// endregion

	// region Accessors

    /**
     * @return the user at the origin of the error
     */
	public User getUser() {
		return user;
	}

	/**
	 * @return the description of the error
	 */
	public String getError() {
		return error;
	}

	/**
	 * @return the request causing the error
	 */
    public String getRequest() {
        return request;
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
	 * @return all error logs from the table
	 */
	public static List<ErrorLog> getAll() {
		return VSquareObject.getAll(ErrorLog.class);
	}

	/**
	 * Return the last error logs in database.
     *
	 * @param start the starting log
	 * @param max the size of the list
     * @param query the string to find
     * @return an extract of the logs (latest first)
	 */
    public static List<ErrorLog> getExtract(int start, int max, String query) {
        if (query == null)
            return DatabaseManager.getRowsFromSessionQueryNamed("FROM ErrorLog ORDER BY creation_date DESC", start, max, new HashMap<>());
        HashMap<String, Object> params = new HashMap<>();
        params.put("query", "%" + query.toUpperCase() + "%");
        return DatabaseManager.getRowsFromSessionQueryNamed("" +
                "SELECT el FROM ErrorLog el " +
                "LEFT JOIN el.user u " +
                "WHERE upper(el.error) LIKE :query OR upper(u.commonName) LIKE :query " +
                "OR upper(u.login) LIKE :query " +
                "ORDER BY el.creationDate DESC", start, max, params);
	}
	
	/**
	 * Return the error log count when searching for a string.
	 * 
     * @param query the string to find
	 * @return the total count of all logs
	 */
    public static long count(String query) {
        if (query == null)
            return DatabaseManager.getFirstFromSessionQuery("SELECT count(*) FROM ErrorLog");
        HashMap<String, Object> params = new HashMap<>();
        params.put("query", "%" + query.toUpperCase() + "%");
        return DatabaseManager.getFirstFromSessionQueryNamed("" +
                "SELECT count(*) FROM ErrorLog el " +
                "LEFT JOIN el.user u " +
                "WHERE upper(el.error) LIKE :query OR upper(u.commonName) LIKE :query " +
                "OR upper(u.login) LIKE :query " +
                "ORDER BY el.creationDate DESC", params);
	}

	@Override
	public JSONObject toJSON(boolean detailed) {
		JSONObject json = super.toJSON(true);
        if (getUser() != null)
            json.put("user", getUser().toJSON());
		json.put("error", error);
        if (request != null && request.length() > 0) {
            try {
                json.put("request", new JSONObject(request));
            } catch (JSONException e) {
                json.put("request", request);
            }
        }
		return json;
	}

    /**
     * Log an event.
     *
     * @param user  the event user
     * @param error the error
     */
    public static void log(User user, String error) {
        log(user, error, null);
    }

	/**
	 * Log an event.
	 * 
	 * @param user the event user
	 * @param error the error
     * @param request the original requeest
     */
    public static void log(User user, String error, String request) {
        new ErrorLog(user, error, request).saveOrUpdate();
	}
	
	// endregion

}
