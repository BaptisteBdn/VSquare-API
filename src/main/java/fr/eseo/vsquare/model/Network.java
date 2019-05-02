package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.DatabaseManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Network object model.
 * <p>
 * Implements the network object class used for network linking.
 *
 * @author Clement Gouin
 */
@Entity
@Table(name = "network")
public class Network extends VSquareObject {

    // region Variables

    @Column(name = "name")
    private String name;

    @Column(name = "id_network_vcenter")
    private String idNetworkVcenter;

    @ManyToMany(cascade = {CascadeType.ALL}, fetch = FetchType.LAZY)
    @JoinTable(name = "link_network_group", joinColumns = {@JoinColumn(name = "id_network")}, inverseJoinColumns = {
            @JoinColumn(name = "id_group")})
    private Set<Group> groups = new HashSet<>();

    // endregion

    // region Constructors

    public Network() {

    }

    /**
     * Constructor
     * 
     * @param name the name of the network
     * @param idNetworkVcenter the id of the network in VCenter
     */
    public Network(String name, String idNetworkVcenter) {
        this.name = name;
        this.idNetworkVcenter = idNetworkVcenter;
    }

    // endregion

    // region Accessors

    /**
     * @return the name of the network
     */
    public String getName() {
        return name;
    }

    /**
     * @return the id of the network in Vcenter
     */
    public String getIdNetworkVcenter() {
        return idNetworkVcenter;
    }

    /**
     * @param name the new name of the network
     */
    public void setName(String name) {
        this.name = name;
    }

    public Set<Group> getGroups() {
        return groups;
    }

    public void addGroup(Group g) {
        groups.add(g);
    }

    public void removeGroup(Group g) {
        groups.remove(g);
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
     * @return all networks from the table
     */
    public static List<Network> getAll() {
        return VSquareObject.getAll(Network.class);
    }

    /**
     * Find a Network by its vCenter id.
     *
     * @param idNetworkVcenter the id to find
     * @return the first network found or null
     */
    public static Network findByIdNetworkVcenter(String idNetworkVcenter) {
        return DatabaseManager.getFirstFromSessionQuery("FROM " + Network.class.getSimpleName() + " WHERE id_network_vcenter = ?0", idNetworkVcenter);
    }

    /**
     * Find a network by its id.
     *
     * @param id the id to search
     * @return the network or null if not found
     */
    public static Network findById(int id) {
        return VSquareObject.findById(id, Network.class);
    }

    @Override
    public JSONObject toJSON(boolean detailed) {
        JSONObject json = super.toJSON(detailed);
        json.put("name", name);
        if (detailed) {
            json.put("network", idNetworkVcenter);
            groups = getGroups();
            JSONArray groupsId = new JSONArray();
            for (Group g : groups)
                groupsId.put(g.toJSON(false));
            json.put("groups", groupsId);
        }
        return json;
    }

    // endregion

}
