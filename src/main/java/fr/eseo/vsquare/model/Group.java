package fr.eseo.vsquare.model;

import fr.eseo.vsquare.model.User.UserType;
import fr.eseo.vsquare.utils.DatabaseManager;
import fr.eseo.vsquare.utils.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.persistence.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Group object model.
 * 
 * Implements the group object representing an ensemble of users.
 * 
 * @author Clement Loiselet
 */

@Entity
@Table(name = "user_group")
public class Group extends VSquareObject {

	// region Variables

	@Column(name = "name")
	private String name;

	@Column(name = "description")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_parent_group")
	private Group parent;
	
	@ManyToMany(mappedBy = "groups",
			fetch=FetchType.EAGER)
    private Set<User> users = new HashSet<>();
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_permission")
	private Permission permission;

    @ManyToMany(mappedBy = "groups",
            fetch = FetchType.LAZY)
    private Set<Network> networks = new HashSet<>();
    
    @OneToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "link_template_group", joinColumns = { @JoinColumn(name = "id_group") }, inverseJoinColumns = {
			@JoinColumn(name = "id_vm") })
    private Set<Vm> templates = new HashSet<>();

	// endregion

	// region Constructors

	public Group() {

	}

	/**
	 * Constructor
	 * 
	 * @param name the name of the group
	 * @param description the associated description of the group
	 */
	public Group(String name, String description) {
		super();
		this.name = name;
		this.description = description;
	}

	/**
	 * Constructor
	 * 
	 * @param name the name of the group
	 * @param description the associated description of the group
	 * @param newParentGroup the parent group
	 */
	public Group(String name, String description, Group newParentGroup) {
		this(name, description, newParentGroup.getId());
		this.parent = newParentGroup;
	}

	/**
	 * Constructor
	 * 
	 * @param name the name of the group
	 * @param description the associated description of the group
	 * @param idParentGroup the id of the wanted parent group
	 */
	public Group(String name, String description, Integer idParentGroup) {
		super();
		this.name = name;
		this.description = description;
		if(idParentGroup != null)
			this.parent = Group.findById(idParentGroup);
	}

	// endregion

	// region Accessors

	/**
	 * @return name of the group
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name of the group
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return description associated with the group
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description the description of the group
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * @return the parent group of the current group
	 */
	public Group getParent() {
		return parent;
	}
	
	/**
	 * @return a set of all users in the group
	 */
	public Set<User> getUsers() {
		return users;
	}
	
	/**
	 * @return permissions defined for the group
	 */
    private Permission getPermission() {
		return permission;
	}
	
	/**
	 * @param permission the permission object to set for the group
	 */
	public void setPermission(Permission permission) {
		this.permission = permission;
	}

    public Set<Network> getNetworks() {
        return networks;
    }
    
    public Set<Vm> getTemplates(){
    	return templates;
    }
    
    public void addTemplate(Vm vm) {
    	this.templates.add(vm);
    }
    
    public void removeTemplate(Vm vm) {
    	this.templates.remove(vm);
    }

    // endregion

    // region Functions

	/**
	 * Return a complete permission object with validated values.
     *
     * A group may not have a permission set, requiring to check
	 * for default value when calling for a group permission instead.
	 * It also check for the parent group permission.
     *
	 * @return permission object with effective values
	 */
    public Permission getEffectivePermission() {
        if (getPermission() != null)
            return getPermission();
        if (getParent() == null)
            return new Permission();
        return getParent().getEffectivePermission();
    }

    public Set<Network> getAvailableNetworks() {
        Set<Network> out = getNetworks();
        if (out == null)
            out = new HashSet<>();
        if (getParent() != null)
            out.addAll(getParent().getAvailableNetworks());
        return out;
    }
    
    public Set<Vm> getAvailableTemplates() {
        Set<Vm> out = getTemplates();
        if (out == null)
            out = new HashSet<>();
        if (getParent() != null)
            out.addAll(getParent().getAvailableTemplates());
        return out;
    }

	/**
	 * Check if the group is one of the default group.
	 * 
	 * @return true if this group is the default group for a user type
	 */
	public boolean isDefaultGroup() {
		return this.equals(UserType.ADMIN.getDefaultGroup()) || this.equals(UserType.REFERENT.getDefaultGroup()) || this.equals(UserType.STUDENT.getDefaultGroup());
	}
	
	/**
	 * @param idParentGroup id of the new parent group to be set
	 */
	public void setIdParentGroup(Integer idParentGroup) {
		if(idParentGroup == null)
			this.parent = null;
		else
			this.parent = Group.findById(idParentGroup);
	}
	
	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	/**
	 * Return all children group for this group.
	 * 
	 * @return a list of all children group
	 */
	public List<Group> getChildren() {
		HashMap<String, Object> params = new HashMap<>();
		params.put("id_parent_group", this.getId());
		return DatabaseManager.getRowsFromSessionQueryNamed("FROM Group WHERE id_parent_group=:id_parent_group",
				params);
	}
	
	/**
	 * Return all cascading children group for this group.
	 * 
	 * @return a set of all children of this group
	 */
	public Set<Group> getAllChildren(){
		Set<Group> groups = new HashSet<>();
		for (Group group : getChildren()) {
			groups.add(group);
			groups.addAll(group.getAllChildren());
		}
		return groups;
	}

	/**
	 * Return all the users associated with this group.
	 *
	 * @param withChildren if you wish to include users from children groups
	 * @return List or null if the group isn't stored in the database
	 */
	public List<User> getUsers(boolean withChildren) {
		return Group.getUsers(this.getId(), withChildren);
	}

	/**
	 * Return all the users associated with the group in parameters.
	 * 
	 * @param idGroup the group id to search
	 * @param withChildren if you wish to include users from children groups
	 * @return List of users associated with this group
	 */
	private static List<User> getUsers(int idGroup, boolean withChildren) {
		Group group = Group.findById(idGroup);
		HashSet<User> users = new HashSet<>();

		if (group == null) {
			Logger.log(Level.WARNING, "group is null cannot find users");
			return new ArrayList<>(users);
		}

		users.addAll(group.getUsers());
		if (withChildren) {
			List<Group> childrenGroup = group.getChildren();
			for (Group g : childrenGroup) {
				users.addAll(g.getUsers(true));
			}
		}

		return new ArrayList<>(users);
	}
	
	@Override
	public JSONObject toJSON(boolean detailed) {
		JSONObject json = super.toJSON(detailed);
		json.put("name", getName());
		json.put("description", getDescription());
		json.put("id_parent_group", (parent == null) ? JSONObject.NULL : parent.getId());
		
		if (detailed){
			JSONArray usersJson = new JSONArray();
			List<User> usersList = this.getUsers(true);
			json.put("number_users", usersList.size());
			for (User user : usersList) {
				usersJson.put(user.toJSON());
			}
			json.put("users", usersJson);

            JSONArray networksJson = new JSONArray();
            for (Network network : getAvailableNetworks())
                networksJson.put(network.toJSON());
            json.put("networks", networksJson);
		}
		return json;
	}
	

	/**
	 * Find a group by its id.
	 * 
	 * @param id the id to search
	 * @return the group or null if not found
	 */
	public static Group findById(int id) {
		return VSquareObject.findById(id, Group.class);
	}

	/**
	 * @return all groups from the table
	 */
	public static List<Group> getAll() {
		return VSquareObject.getAll(Group.class);
	}

	// endregion
}
