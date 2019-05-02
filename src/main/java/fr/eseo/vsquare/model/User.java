package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.DatabaseManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User object model.
 * <p>
 * Implements the user object as defined in the domain.
 */

@Entity
@Table(name = "user")
public class User extends VSquareObject {

    public enum UserType {
        STUDENT, REFERENT, ADMIN;

        private Group defaultGroup = null;

        public Group getDefaultGroup() {
            return defaultGroup;
        }

        void setDefaultGroup(Group defaultGroup) {
            this.defaultGroup = defaultGroup;
        }

        public boolean greaterThan(UserType other) {
            return this.compareTo(other) > 0;
        }

        public boolean lesserThan(UserType other) {
            return this.compareTo(other) < 0;
        }
    }

    public static final UserType DEFAULT_USER_TYPE = UserType.STUDENT;

    // region Variables

    @Column(name = "login")
    private String login;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private UserType type;

    @Column(name = "common_name")
    private String commonName;

    @ManyToMany(cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    @JoinTable(name = "link_user_group", joinColumns = {@JoinColumn(name = "id_user")}, inverseJoinColumns = {
            @JoinColumn(name = "id_group")})
    private Set<Group> groups = new HashSet<>();

    @Column(name = "id_network_vcenter")
    private String privateNetwork;

    // endregion

    // region Constructors

    public User() {

    }

    /**
     * Constructor
     *
     * @param login      the login of the user
     * @param commonName the name used for the user
     */
    public User(String login, String commonName) {
        this(login, User.DEFAULT_USER_TYPE, commonName);
    }

    /**
     * Constructor
     *
     * @param login      the login of the user
     * @param type       defines the rights for the user between preselected choices
     * @param commonName the name used for the user
     */
    public User(String login, UserType type, String commonName) {
        super();
        this.login = login;
        this.type = type;
        this.commonName = commonName;
        if (type.defaultGroup != null)
            this.addGroup(type.defaultGroup);
    }

    // endregion

    // region Accessors

    /**
     * @return the login of the user
     */
    public String getLogin() {
        return login;
    }

    /**
     * @return the type of the user defined in UserType
     */
    public UserType getType() {
        return type;
    }

    /**
     * @param type the new type of the user, must be defined in UserType
     */
    public void setType(UserType type) {
        this.type = type;
    }

    /**
     * @return the name in use for the user
     */
    public String getCommonName() {
        return commonName;
    }

    /**
     * @return a set of all the groups of the user
     */
    public Set<Group> getGroups() {
        return groups;
    }

    public String getPrivateNetwork() {
        return privateNetwork;
    }

    public void setPrivateNetwork(String privateNetwork) {
        this.privateNetwork = privateNetwork;
    }

    // endregion

    // region Functions

    /**
     * Return a boolean if the user is in the group.
     *
     * @param group the group to check
     * @return a boolean if the user is in the group group, false otherwise
     */
    private boolean hasGroup(Group group) {
        return this.getGroups().contains(group);
    }

    /**
     * Add a user in a group if he is not already in.
     *
     * @param group the group to add the user in
     */
    public void addGroup(Group group) {
        if (!this.hasGroup(group)) {
            this.groups.add(group);
        }
    }

    /**
     * Remove a user from a group if he is in.
     *
     * @param group the group to remove the user from
     */
    public void removeGroup(Group group) {
        if (this.hasGroup(group)) {
            this.groups.remove(group);
        }
        for (Group child : group.getAllChildren())
            if (this.hasGroup(child))
                this.groups.remove(child);
    }

    /**
     * check if the user is in the right groups
     * @return true on change
     */
    public boolean checkGroups() {
        boolean change = false;
        Set<Group> currGroups = new HashSet<>(getGroups());
        for (Group g : currGroups)
            if (g.isDefaultGroup() && !type.defaultGroup.equals(g)) {
                this.removeGroup(g);
                change = true;
            }

        if (getGroups().isEmpty()) {
            this.addGroup(type.defaultGroup);
            change = true;
        }

        return change;
    }

    /**
     * @return all the Vms of the current user
     */
    public List<Vm> getVms() {
        return DatabaseManager.getRowsFromSessionQuery("FROM " + Vm.class.getSimpleName() + " WHERE id_user =?0 AND template = 0", this.getId());
    }

    /**
     * @return true if the user is a student, false otherwise
     */
    public boolean isStudent() {
        return this.getType() == UserType.STUDENT;
    }

    /**
     * @return true if the user is an admin, false otherwise
     */
    public boolean isAdmin() {
        return this.getType() == UserType.ADMIN;
    }

    /**
     * Set this user as admin and add it to the admin group.
     */
    public void setAdmin() {
        this.setType(UserType.ADMIN);
        this.groups = new HashSet<>();
        if (type.defaultGroup != null)
            this.addGroup(type.defaultGroup);
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
     * Generate a new token for this user.
     *
     * @return the generated token
     */
    public Token generateToken() {
        Token token = new Token(this);
        if (token.saveOrUpdate())
            return token;
        else
            return null;
    }

    /**
     * Return a completed permission with validated values.
     * <p>
     * Takes in account the maximum value for the every permission of the user
     * thus parcouring every group he is in.
     *
     * @return the effective permission for the user
     */
    public Permission getEffectivePermission() {
        Permission perm = null;
        for (Group g : this.getGroups()) {
            if (perm == null)
                perm = g.getEffectivePermission();
            else
                perm = perm.getMax(g.getEffectivePermission());
        }
        return perm == null ? new Permission() : perm;
    }

    public Set<Network> getAvailableNetworks() {
        Set<Network> out = new HashSet<>();
        for (Group g : getGroups()) {
            out.addAll(g.getAvailableNetworks());
        }
        return out;
    }

    public Set<Vm> getAvailableTemplates() {
        Set<Vm> out = new HashSet<>();
        for (Group g : getGroups()) {
            out.addAll(g.getAvailableTemplates());
        }
        return out;
    }

    @Override
    public JSONObject toJSON(boolean detailed) {
        JSONObject json = super.toJSON(detailed);
        json.put("common_name", getCommonName());
        json.put("login", getLogin());
        json.put("type", getType().toString());

        if (detailed) {
            groups = getGroups();
            JSONArray groupsId = new JSONArray();
            for (Group g : groups) {
                groupsId.put(g.getId());
            }

            json.put("groups_id", groupsId);
        }
        return json;
    }

    /**
     * @return a set of Ids of the groups of the user
     */
    public Set<Group> getGroupsForUser() {
        return User.getGroupsForUser(this.getId());
    }

    /**
     * Find all the groups the user has access to.
     *
     * @param idUser the user to look at
     * @return List or null if the user doesn't exist
     */
    private static Set<Group> getGroupsForUser(int idUser) {
        User user = User.findById(idUser);

        Set<Group> groups = new HashSet<>();

        if (user == null) {
            return groups;
        }

        if (user.isAdmin()) {
            return new HashSet<>(Group.getAll());
        }

        for (Group currentGroup : user.getGroups()) {
            while (currentGroup != null) {
                groups.add(currentGroup);
                currentGroup = currentGroup.getParent();
            }
        }

        return groups;
    }

    /**
     * Find a user by its id.
     *
     * @param id the id to search
     * @return the user or null if not found
     */
    public static User findById(int id) {
        return VSquareObject.findById(id, User.class);
    }

    /**
     * Get all users from the table.
     *
     * @return all the users from the database
     */
    public static List<User> getAll() {
        return VSquareObject.getAll(User.class);
    }

    /**
     * Find a user by its login.
     *
     * @param login the login to search
     * @return the user or null if not found
     */
    public static User findByLogin(String login) {
        return DatabaseManager.getFirstFromSessionQuery("FROM User WHERE login = ?0", login);
    }

    /**
     * Update default group for user type.
     *
     * @param type         the type to change
     * @param defaultGroup the selected group
     */
    public static void setDefaultGroup(UserType type, Group defaultGroup) {
        type.setDefaultGroup(defaultGroup);
    }

    /**
     * Parse a user type by its string value.
     *
     * @param type the string value to parse
     * @return the UserType or null if invalid type
     */
    public static UserType parseType(String type) {
        for (UserType ut : UserType.values())
            if (ut.toString().equalsIgnoreCase(type))
                return ut;
        return null;
    }
    // endregion

}
