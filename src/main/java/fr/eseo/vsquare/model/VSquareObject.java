package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.DatabaseManager;
import fr.eseo.vsquare.utils.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.json.JSONObject;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * Implements the Vsquare Object model.
 * 
 * Every object must inherit from the VSquare Object Class.
 * 
 * @author Clement Gouin
 */
@MappedSuperclass
public abstract class VSquareObject {

	// region Variables

	@Id
	@GeneratedValue
	@Column(name = "id")
	private Integer id;

	@Column(name = "creation_date")
	private Date creationDate;

	// endregion

	// region Constructors

	public VSquareObject() {

	}

	// endregion

	// region Accessors

	/**
	 * @return the Id of the VSquare object
	 */
	public Integer getId() {
		return id;
	}

	/**
	 * @return the creation date of the object
	 */
    Date getCreationDate() {
		return creationDate;
	}

	// endregion

	// region Functions

	/**
	 * Insert a new row or update it if it already exists.
	 * 
	 * @return true if operation is successful
	 */
	public boolean saveOrUpdate() {
		if(!DatabaseManager.isHibernateInitialized()) {
			Logger.log(Level.SEVERE, "Database not initialized, cannot save object");
			return false;
		}
		Transaction tx = null;
		Session session = DatabaseManager.getSessionFactory().getCurrentSession();
		try {
			tx = session.beginTransaction();
			if (id == null) {
				id = (Integer) session.save(this);
				creationDate = new Date();
			} else {
				session.update(this);
			}
			tx.commit();
			return true;
		} catch (HibernateException e) {
			if (tx != null)
				tx.rollback();
			Logger.log(Level.SEVERE, e.toString(), e);
			return false;
		}
	}

	/**
	 * Delete the row.
	 * 
	 * @return true if operation is successful
	 */
	public boolean delete() {
		if(!DatabaseManager.isHibernateInitialized()) {
			Logger.log(Level.SEVERE, "Database not initialized, cannot delete object");
			return false;
		}
		if (id == null)
			return false;
		Transaction tx = null;
		Session session = DatabaseManager.getSessionFactory().getCurrentSession();
		try {
			tx = session.beginTransaction();
			session.delete(this);
			tx.commit();
			id = null;
			creationDate = null;
			return true;
		} catch (HibernateException e) {
			if (tx != null)
				tx.rollback();
			Logger.log(Level.SEVERE, e.toString(), e);
			return false;
		}
	}

	/**
	 * Return a row by its id.
	 * 
	 * @param id the id to find
	 * @param objectClass the class of the object
     * @param <T> the class to find
     * @return the object or null if not found
	 */
    public static <T> T findById(int id, Class<T> objectClass) {
		return DatabaseManager.getFirstFromSessionQuery("FROM " + objectClass.getSimpleName() + " WHERE id = ?0", id);
	}

	/**
	 * Return all rows from table.
     * @param <T> the class to find
	 * @param objectClass the class of the object
	 * @return all the rows from the database
	 */
	static <T> List<T> getAll(Class<T> objectClass) {
		return DatabaseManager.getRowsFromSessionQuery("FROM " + objectClass.getSimpleName());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (obj instanceof VSquareObject) {
			VSquareObject other = (VSquareObject) obj;
			//Fix hibernate anonymous class
			String thisSimpleName = this.getClass().getSimpleName();
			String objSimpleName = obj.getClass().getSimpleName();
			if(!thisSimpleName.startsWith(objSimpleName) && !objSimpleName.startsWith(thisSimpleName))
				return false;
			if (id == null || other.id == null) {
				return false;
            } else return id == (int) other.id;
		}
		return false;
	}
	
	@Override
	public String toString() {
		return String.format("%s:%d", this.getClass().getSimpleName(), this.getId() == null ? 0 : this.getId());
	}
	
	/**
	 * @return a simple representation of this object in JSON
	 */
	public JSONObject toJSON() {
		return toJSON(false);
	}
	
	/**
	 * @param detailed detailed version
	 * @return a representation of this object in JSON
	 */
    JSONObject toJSON(boolean detailed) {
		JSONObject json = new JSONObject();
		json.put("id", (id == null) ? JSONObject.NULL : id);
		if(detailed)
			json.put("creation_date", creationDate);
		return json;
	}
	
	// endregion

}
