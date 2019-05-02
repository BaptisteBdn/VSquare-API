package fr.eseo.vsquare.model;

import fr.eseo.vsquare.utils.DatabaseManager;
import fr.eseo.vsquare.utils.Utils;

import javax.persistence.*;
import java.io.File;
import java.util.Date;
import java.util.UUID;

/**
 * DownloadLink Object Model.
 * 
 * Implements the download link object as defined in the domain.
 * 
 */

@Entity
@Table(name = "download_link")
public class DownloadLink extends VSquareObject {

	// region Variables

	/**
	 * the number of time the user tried to use this link
	 */
	@Column(name = "download_try")
	private int downloadTry;

	@Column(name = "download_success")
	private int downloadSuccess;

	/**
	 * link used to access this ressource. this is the link the user will receive
	 */
	@Column(name = "external_link")
	private String externalLink;
	
	/**
	 * link used to read the ressource.
	 */
	@Column(name = "internal_link")
	private String internalLink;
	
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "id_vm")
	private Vm vm;

	// endregion

	// region Constructors

	public DownloadLink() {}
	
	public DownloadLink(String internalLink, Vm vm){
		this(UUID.randomUUID().toString(), internalLink, vm);
	}

	public DownloadLink(String externalLink, String internalLink, Vm vm) {
		super();
		this.externalLink = externalLink;
		this.internalLink = internalLink;
		this.vm = vm;
	}



	// endregion

	// region Accessors
	
	public int getDownloadTry() {
		return downloadTry;
	}

	public void setDownloadTry(int downloadTry) {
		this.downloadTry = downloadTry;
	}

	public int getDownloadSuccess() {
		return downloadSuccess;
	}

	public void setDownloadSuccess(int downloadSuccess) {
		this.downloadSuccess = downloadSuccess;
	}

	public String getExternalLink() {
		return externalLink;
	}

	public void setExternalLink(String externalLink) {
		this.externalLink = externalLink;
	}

	public String getInternalLink() {
		return internalLink;
	}

	public void setInternalLink(String internalLink) {
		this.internalLink = internalLink;
	}

	public Vm getVm() {
		return vm;
	}

    // endregion

    // region Functions

	/**
	 * Increase the download try counter by 1.
	 */
	public void increaseDownloadTry(){
		this.downloadTry++;
	}
	
	/**
	 * Increase the download success counter by 1.
	 */
	public void increaseDownloadSuccess(){
		this.downloadSuccess++;
	}
	
	/**
	 * @return the link as a file
	 */
	public File getInternalLinkAsFile(){
		return new File(internalLink);
	}
	
	/**
	 * @return true if the timeout is exceeded, false otherwise
	 */
	public boolean isTimeoutExceeded(){
		int timeout = Utils.getInt("timeout_link");
		long diffInMillis = (new Date()).getTime() - this.getCreationDate().getTime();
		return diffInMillis > timeout*60*1000;
	}

	@Override
	public int hashCode() {
        return super.hashCode();
	}	
	

	@Override
	public boolean equals(Object obj) {
        return super.equals(obj);
	}

	@Override
	public String toString() {
		return "DownloadLink [downloadTry=" + downloadTry + ", downloadSuccess=" + downloadSuccess + ", externalLink="
				+ externalLink + ", internalLink=" + internalLink + ", vm=" + vm + "]";
	}

	/**
	 * Find a DownloadLink by its id.
	 * 
	 * @param id the id to find
	 * @return the DownloadLink or null if not found
	 */
	public static DownloadLink findById(int id) {
		return VSquareObject.findById(id, DownloadLink.class);
	}
	

	/**
	 * Find a DownloadLink by its external link.
	 * 
	 * @param externalLink the id to find
	 * @return the DownloadLink or null if not found
	 */
	public static DownloadLink findByExternalLink(String externalLink) {
		return DatabaseManager.getFirstFromSessionQuery("FROM " + DownloadLink.class.getSimpleName() + " WHERE external_link = ?0", externalLink);
	}
	
	/**
	 * Find a DownloadLink by its external link.
	 * 
	 * @param internalLink the id to find
	 * @return the DownloadLink or null if not found
	 */
	public static DownloadLink findByInternalLink(String internalLink) {
		return DatabaseManager.getFirstFromSessionQuery("FROM " + DownloadLink.class.getSimpleName() + " WHERE internal_link = ?0", internalLink);
	}
	
	/**
	 * Find a DownloadLink by its Vm.
     *
     * @param vm the source vm
	 * @return the DownloadLink or null if not found
	 */
	public static DownloadLink findByVm(Vm vm) {
		return DatabaseManager.getFirstFromSessionQuery("FROM " + DownloadLink.class.getSimpleName() + " WHERE id_vm = ?0", vm.getId());
	}
		
	
	
	// endregion

}
