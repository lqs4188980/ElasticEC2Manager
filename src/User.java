import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class User {
	String uid;
	// This map for ip address and image id
	Map<String, String> imageMap;
	// bucketList is used to store every S3 bucket.
	List<String> bucketList;
	// keyName stores the key's name used by this user.
	String keyName;
	// elasticIpMap for ip address mapping to instance id
	Map<String, String> elasticIpMap;
	// snapshotMap for ip address mapping to snapshot of data volume 
	//Map<String, String> snapshotMap;
	List<String> scaledInstances;
	
	User(String id){
		this.uid = id;
		imageMap = new HashMap<String, String>();
		bucketList = new ArrayList<String>();
		keyName = null;
		elasticIpMap = new HashMap<String, String>();
		scaledInstances = new ArrayList<String>();
		//snapshotMap = new HashMap<String, String>();
	}
}
