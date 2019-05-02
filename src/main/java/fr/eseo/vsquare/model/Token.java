package fr.eseo.vsquare.model;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.json.JSONObject;

import fr.eseo.vsquare.utils.DatabaseManager;
import fr.eseo.vsquare.utils.Utils;

/**
 * Token object model.
 * 
 * Implements the token object class used for the API rest authentication.
 * 
 * @author Clement Gouin
 */
@Entity
@Table(name = "token")
public class Token extends VSquareObject {

	private static final int TOKEN_LENGTH = 32;

	// region Variables

	@Column(name = "value")
	private String value;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "id_user")
	private User user;

	// endregion

	// region Constructors

	public Token() {

	}

	/**
	 * Constructor
	 * 
	 * @param user the user creating the token
	 */
	public Token(User user) {
		super();
		this.value = Utils.getRandomString(TOKEN_LENGTH);
		this.user = user;
	}
	
	/**
	 * Constructor
	 * 
	 * @param value the token value
	 * @param user the user creating the token
	 */
	public Token(String value, User user) {
		super();
		this.value = value;
		this.user = user;
	}

	// endregion

	// region Accessors

	/**
	 * @return the value of the token
	 */
	public String getValue() {
		return value;
	}

	/**
	 * @param value the new token to set
	 */
	public void setValue(String value) {
		this.value = value;
	}

	/**
	 * @return the user associated with the token
	 */
	public User getUser() {
		return user;
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
	 * @return the age of this token (in milliseconds)
	 */
	public long getAge() {
		return System.currentTimeMillis() - this.getCreationDate().getTime();
	}

	/**
	 * @return all tokens from the table
	 */
	public static List<Token> getAll() {
		return VSquareObject.getAll(Token.class);
	}

	/**
	 * Find a token by its value.
	 * 
	 * @param value the value to find
	 * @return the token or null if not found
	 */
	public static Token findByValue(String value) {
		return DatabaseManager.getFirstFromSessionQuery("FROM Token WHERE value = ?0", value);
	}

	@Override
	public JSONObject toJSON(boolean detailed) {
		JSONObject json = super.toJSON(detailed);
		json.put("value", value);
		json.put("user", getUser().toJSON());
		return json;
	}

	// endregion

}
