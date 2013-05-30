import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import com.amazonaws.auth.policy.Resource;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult;
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest;
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.AllocateAddressResult;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CreateImageRequest;
import com.amazonaws.services.ec2.model.CreateImageResult;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.CreateSnapshotResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DeregisterImageRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsResult;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.EbsInstanceBlockDevice;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.ReleaseAddressRequest;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.s3.AmazonS3Client;

/*
 * This class is used to handle every detailed operations
 * 
 * The top level logic only should know the generic operation, like createVMs, releaseVMs. Therefore, VM_Manager class only 
 * need to expose createVMs and releaseVMs these two interface. 
 * 
 */

/*
 * Creating instances involves to: selecting security group, authoring security group ingress permission, create key pairs
 * sending request to run an instance, log each instance with the user's id, attach volumes, create tag<instanceid, uid>. 
 */

/*
 * Release an instances involves snapshot every volume, and terminate every VMs, log the snapshot info with instance and 
 * user's id and partition information.
 */

/*
 * For running instance, we need a map to store instanceID and userID.
 * For snapshot, we need objects to store snapshotID, attached instanceID, partition information and userID
 * So we still need an class to integrate that information.
 * I think the manager should keep these run time information. The top level should know as little as possible. 
 */
public class VM_Manager {
	private Map<String,User> userList;
	// For simplify the problem, the security group, key's name and instance type will remain fixed

	static final String DEFAULT_DEVICE_STRING = "/dev/sdf";

	
	/*
	 * Constructor initialize the userList
	 */
	VM_Manager() {
		userList = new HashMap<String, User>();
		
	}

	public String createBucket(AmazonS3Client s3, String uid) {
		String bucketName = "it-department-" + uid;
		s3.createBucket(bucketName);
		return bucketName;
	}
	private void createTags(AmazonEC2 ec2, List<String> instanceIdList, String uid) {
        List<Tag> tags = new LinkedList<Tag>();
        Tag nameTag = new Tag("Name", uid);
        tags.add(nameTag);
        
        CreateTagsRequest ctr = new CreateTagsRequest(instanceIdList, tags);
        ec2.createTags(ctr);
	}
	public void assignElasticIp(AmazonEC2 ec2, String instanceId, String publicIp) {
		
		//associate
		AssociateAddressRequest aar = new AssociateAddressRequest();
		aar.setInstanceId(instanceId);
		aar.setPublicIp(publicIp);
		ec2.associateAddress(aar);
		
	}
	
	public String allocateElasticIp(AmazonEC2 ec2) {
		String ipAddress = null;
		AllocateAddressResult elasticResult = ec2.allocateAddress();
		ipAddress = elasticResult.getPublicIp();
		System.out.println("New elastic IP: "+ipAddress);
		return ipAddress;
	}
	
	public String describeInstanceState(AmazonEC2 ec2, String instanceId) {
		String instanceState = null;
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		describeInstancesRequest.withInstanceIds(instanceId);
		DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
		List<Reservation> reservations = describeInstancesResult.getReservations();
		List<Instance> instances = new LinkedList<Instance>();
		for(Reservation reservation : reservations){
			instances.addAll(reservation.getInstances());
		}
		for(Instance instance : instances){

			instanceState = instance.getState().getName();
		}
		
		return instanceState;
	}
	public String createKeyPair(AmazonEC2 ec2, String uid) throws IOException{
		String keyName = null;
		// Construc a key name
		keyName = "key-" + uid;
		//Describe Key Pair
        DescribeKeyPairsResult describeKeyPairsResult = ec2.describeKeyPairs();
        List<KeyPairInfo> keyPairInfo = describeKeyPairsResult.getKeyPairs();
        //If the key is exist, then go to next step
        boolean is_KeyExist = false;
        for (KeyPairInfo info : keyPairInfo){
            if (info.getKeyName().equals(keyName)) {
                System.out.println("Key \"" + keyName + "\" is exist.");
                is_KeyExist = true;
                break;
            }
        }
        if (!is_KeyExist){
            //Create and initialize a CreateKeyPairRequest instance. Use the withKeyName method to set the key pair name
            CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
            createKeyPairRequest.withKeyName(keyName);
        
            //Pass the request object to the createKeyPair method.
            CreateKeyPairResult createKeyPairResult = ec2.createKeyPair(createKeyPairRequest);
        
            //Call the result object's getKeyPair method to obtain a KeyPair object.
            KeyPair keyPair = new KeyPair();
            keyPair = createKeyPairResult.getKeyPair();
            String privatekey = keyPair.getKeyMaterial();
            //Save the private key as .pem
            File newPrivateKey = new File("/Users/LarryCane/Documents/NYU-POLY/CloudComputing/Key/" + keyName + ".pem");
            FileWriter fileWriter = new FileWriter(newPrivateKey);
            fileWriter.write(privatekey);
            fileWriter.close();
        }
		return keyName;
	}
	
	private List<String> getRunningInstancesIDList(AmazonEC2 ec2){
		// Used for store all the running instance ids
		List<String> instanceIDList = new LinkedList<String>();
		
		// Used for temporarily store instances
		List<Instance> instances = new LinkedList<Instance>();
		
		// Construct request
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		
		// Send request
		DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
		
		// Get reservations
		List<Reservation> reservations = describeInstancesResult.getReservations();
		
		// Get instances into "instances"
		for(Reservation reservation : reservations){
			instances.addAll(reservation.getInstances());
		}
		
		// Pick up running instances and collect their IDs into "instancesIDList"
		for(Instance instance : instances){
			InstanceState state = instance.getState();
        	if(state.getName().equals("running")){
        		instanceIDList.add(instance.getInstanceId());
        	}
		}
		return instanceIDList;
	}
	public void deregisterAMI(AmazonEC2 ec2, String imageId) {
		DeregisterImageRequest deregisterImageRequest = new DeregisterImageRequest();
		deregisterImageRequest.withImageId(imageId);
		ec2.deregisterImage(deregisterImageRequest);
	}
		
	public Boolean cleanOperation(AmazonEC2 ec2, AmazonAutoScalingClient autoScalingClient, AmazonCloudWatchClient cloudWatchClient) throws InterruptedException{
		
		
		
		
		
		// Delete all AMIs
		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest();
		describeImagesRequest.withOwners("self");
		DescribeImagesResult describeImagesResult = ec2.describeImages(describeImagesRequest);
		List<Image> images = describeImagesResult.getImages();
		for(Image image : images){
			deregisterAMI(ec2, image.getImageId());
		}
		Thread.sleep(10000);
		System.out.println("All images has been released");
		
		// Release all snapshots
		DescribeSnapshotsRequest describeSnapshotsRequest = new DescribeSnapshotsRequest();
		describeSnapshotsRequest.withOwnerIds("self");
		DescribeSnapshotsResult describeSnapshotsResult = ec2.describeSnapshots(describeSnapshotsRequest);
		List<Snapshot> snapshots = describeSnapshotsResult.getSnapshots();
		
		for(Snapshot snapshot : snapshots){
			DeleteSnapshotRequest deleteSnapshotRequest = new DeleteSnapshotRequest();
			deleteSnapshotRequest.withSnapshotId(snapshot.getSnapshotId());
			ec2.deleteSnapshot(deleteSnapshotRequest);
		}
		System.out.println("All snapshot has been deleted");
		// Release all the elastic ip
		DescribeAddressesResult describeAddressesResult = ec2.describeAddresses();
		List<Address> addresses = describeAddressesResult.getAddresses();
		for(Address address : addresses){
			String publicIp = address.getPublicIp();
			DisassociateAddressRequest disassociateAddressRequest = new DisassociateAddressRequest();
			disassociateAddressRequest.withPublicIp(publicIp);
			ec2.disassociateAddress(disassociateAddressRequest);
			ReleaseAddressRequest releaseAddressRequest = new ReleaseAddressRequest();
			releaseAddressRequest.withPublicIp(publicIp);
			ec2.releaseAddress(releaseAddressRequest);
		}
		System.out.println("All of addresses has been released");
		// Used to store running instances IDs
		List<String> instanceIds = getRunningInstancesIDList(ec2);
		if(getRunningInstancesIDList(ec2).isEmpty()){
			return true;
		}
		
		// Detach all the data volumes
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		describeInstancesRequest.withInstanceIds(instanceIds);
		DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
		List<Reservation> reservations = new LinkedList<Reservation>();
		reservations = describeInstancesResult.getReservations();
		List<Instance> instances = new LinkedList<Instance>();
		for(Reservation reservation : reservations){
			instances.addAll(reservation.getInstances());
		}
		for(Instance instance : instances){
			List<InstanceBlockDeviceMapping> instanceBlockDeviceMappings = instance.getBlockDeviceMappings();
			for(InstanceBlockDeviceMapping instanceBlockDeviceMapping : instanceBlockDeviceMappings){
				if (!instanceBlockDeviceMapping.getDeviceName().equals("/dev/sda1")) {
					String volumeId = instanceBlockDeviceMapping.getEbs().getVolumeId();
					detachVolume(ec2, volumeId);
					while(!describeVolumeState(ec2, volumeId).equals("available")){
						
					}
					deleteVolume(ec2, volumeId);
					
				}
			}
		}
		DescribeVolumesRequest describeVolumesRequest = new DescribeVolumesRequest();
		DescribeVolumesResult describeVolumesResult = ec2.describeVolumes();
		List<Volume> volumes = describeVolumesResult.getVolumes();
		for(Volume volume : volumes){
			if (volume.getState().equals("available")) {
				deleteVolume(ec2, volume.getVolumeId());
			}
		}
		System.out.println("All volumes has been deleted");
		// Construct request
		TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();
    	terminateInstancesRequest.withInstanceIds(instanceIds);
    	
    	// Send request
    	TerminateInstancesResult terminateInstancesResult = ec2.terminateInstances(terminateInstancesRequest);
    	
    	// Sleep for a while
    	Thread.sleep(30000);
    	
    	// Delete Autoscaling Group
    	DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest = new DescribeAutoScalingGroupsRequest();
    	DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult = autoScalingClient.describeAutoScalingGroups(describeAutoScalingGroupsRequest);
    	List<AutoScalingGroup> autoScalingGroups = describeAutoScalingGroupsResult.getAutoScalingGroups();
    	for(AutoScalingGroup autoScalingGroup : autoScalingGroups){
    		UpdateAutoScalingGroupRequest updateAutoScalingGroupRequest = new UpdateAutoScalingGroupRequest();
    		updateAutoScalingGroupRequest.withMaxSize(0).withMinSize(0).withAutoScalingGroupName(autoScalingGroup.getAutoScalingGroupName());
    		autoScalingClient.updateAutoScalingGroup(updateAutoScalingGroupRequest);
    		System.out.println("Now Deleting AutoScaling Group: " + autoScalingGroup.getAutoScalingGroupName());
    		DeleteAutoScalingGroupRequest deleteAutoScalingGroupRequest = new DeleteAutoScalingGroupRequest();
    		deleteAutoScalingGroupRequest.withAutoScalingGroupName(autoScalingGroup.getAutoScalingGroupName());
    		autoScalingClient.deleteAutoScalingGroup(deleteAutoScalingGroupRequest);
    	}
    	System.out.println("All autoscaling group has been deleted");
    	// Delete Launch Configuration
		DescribeLaunchConfigurationsRequest describeLaunchConfigurationsRequest = new DescribeLaunchConfigurationsRequest();
		DescribeLaunchConfigurationsResult describeLaunchConfigurationsResult = autoScalingClient.describeLaunchConfigurations(describeLaunchConfigurationsRequest);
		List<LaunchConfiguration> launchConfigurations = describeLaunchConfigurationsResult.getLaunchConfigurations();
		for(LaunchConfiguration launchConfiguration : launchConfigurations){
			System.out.println("Now Deleting Launch Configuration: " + launchConfiguration.getLaunchConfigurationName());
			DeleteLaunchConfigurationRequest deleteLaunchConfigurationRequest = new DeleteLaunchConfigurationRequest();
			deleteLaunchConfigurationRequest.withLaunchConfigurationName(launchConfiguration.getLaunchConfigurationName());
			autoScalingClient.deleteLaunchConfiguration(deleteLaunchConfigurationRequest);
		}
		System.out.println("All launch configuration has been deleted");
		// Delete Alarms
		DescribeAlarmsResult describeAlarmsResult = cloudWatchClient.describeAlarms();
		List<MetricAlarm> metricAlarms = describeAlarmsResult.getMetricAlarms();
		List<String> alarmNamesList = new ArrayList<String>();
		for(MetricAlarm metricAlarm : metricAlarms){
			//System.out.println("Alarm: " + metricAlarm.getAlarmName());
			alarmNamesList.add(metricAlarm.getAlarmName());
		}
		if(alarmNamesList.size() > 0){
			DeleteAlarmsRequest deleteAlarmsRequest = new DeleteAlarmsRequest();
			deleteAlarmsRequest.withAlarmNames(alarmNamesList);
			cloudWatchClient.deleteAlarms(deleteAlarmsRequest);
		}
		System.out.println("All alarms has been deleted");
		// Delete Autoscaling policies
		DescribePoliciesResult describePoliciesResult = autoScalingClient.describePolicies();
		List<ScalingPolicy> scalingPolicies = describePoliciesResult.getScalingPolicies();
		for(ScalingPolicy scalingPolicy : scalingPolicies){
			System.out.println("Now Deleting Policy: " + scalingPolicy.getPolicyName());
			DeletePolicyRequest deletePolicyRequest = new DeletePolicyRequest();
			deletePolicyRequest.withPolicyName(scalingPolicy.getPolicyName()).withAutoScalingGroupName("AutoScale Group For IT-Department");
			autoScalingClient.deletePolicy(deletePolicyRequest);
		}
		System.out.println("All autoscaling policy has been deleted");
    	

		
		
    	// Get the list of instances that their state has changed
    	List<InstanceStateChange> instanceStateChangedList = terminateInstancesResult.getTerminatingInstances();
    	
    	// If the number of changed instances is not equal to the initial number of running instances,
    	// return false. Else return true.
    	if(instanceStateChangedList.size() != instanceIds.size())
    		return false;
    	else {
    		
			return true;
		}
	}
	public String createSnapshot(AmazonEC2 ec2, String volumeId) {
		String snapshotId = null;
		CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest();
		createSnapshotRequest.withVolumeId(volumeId);
		CreateSnapshotResult createSnapshotResult = ec2.createSnapshot(createSnapshotRequest);
		snapshotId = createSnapshotResult.getSnapshot().getSnapshotId();
		return snapshotId;
	}
	
	public void createAlarm(AmazonCloudWatchClient cloudWatchClient, List<String> instanceIds, String alarmName, Double threshold, String comparisonOperator) {
		List<Dimension> dimensions = new LinkedList<Dimension>();
		for(String instanceId : instanceIds){
			Dimension dimension = new Dimension();
			dimension.withName("InstanceId").withValue(instanceId);
			dimensions.add(dimension);
		}
		PutMetricAlarmRequest putMetricAlarmRequest = new PutMetricAlarmRequest();
		putMetricAlarmRequest.withAlarmName(alarmName)
		                     .withDimensions(dimensions)
		                     .withEvaluationPeriods(5)
		                     .withMetricName("CPUUtilization")
		                     .withNamespace("AWS/EC2")
		                     .withPeriod(60)
		                     .withStatistic("Average")
		                     .withThreshold(threshold)
		                     .withComparisonOperator(comparisonOperator);
		cloudWatchClient.putMetricAlarm(putMetricAlarmRequest);
	}
	
	public void deleteAlarm(AmazonCloudWatchClient cloudWatchClient, List<String> alarmNames) {
		DeleteAlarmsRequest deleteAlarmsRequest = new DeleteAlarmsRequest();
		deleteAlarmsRequest.withAlarmNames(alarmNames);
		cloudWatchClient.deleteAlarms(deleteAlarmsRequest);
	}
	
	public Map<String, String> getAlarmStateInstances(AmazonCloudWatchClient cloudWatchClient) {
		Map<String, String> instanceWithAlarms = new HashMap<String, String>();
		DescribeAlarmsRequest describeAlarmsRequest = new DescribeAlarmsRequest();
		describeAlarmsRequest.withStateValue("ALARM");
		DescribeAlarmsResult describeAlarmsResult = cloudWatchClient.describeAlarms(describeAlarmsRequest);
		List<MetricAlarm> metricAlarms = describeAlarmsResult.getMetricAlarms();
		for(MetricAlarm metricAlarm : metricAlarms){
			List<Dimension> dimensions = metricAlarm.getDimensions();
			for(Dimension dimension : dimensions){
				instanceWithAlarms.put(dimension.getValue(), metricAlarm.getAlarmName());
			}
		}
		return instanceWithAlarms;
	}
	
	public double getCloudWatchStat(AmazonCloudWatchClient cloudWatchClient, String instanceId){
		double averageUsage = 0;
		
		//create request message
		GetMetricStatisticsRequest statRequest = new GetMetricStatisticsRequest();
		
		//set up request message
		statRequest.setNamespace("AWS/EC2"); //namespace
		statRequest.setPeriod(60); //period of data
		ArrayList<String> stats = new ArrayList<String>();
		
		//Use one of these strings: Average, Maximum, Minimum, SampleCount, Sum 
		stats.add("Average"); 
		stats.add("Sum");
		statRequest.setStatistics(stats);
		
		//Use one of these strings: CPUUtilization, NetworkIn, NetworkOut, DiskReadBytes, DiskWriteBytes, DiskReadOperations  
		statRequest.setMetricName("CPUUtilization"); 
		
		// set time
		GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		calendar.add(GregorianCalendar.SECOND, -1 * calendar.get(GregorianCalendar.SECOND)); // 1 second ago
		Date endTime = calendar.getTime();
		calendar.add(GregorianCalendar.MINUTE, -10); // 10 minutes ago
		Date startTime = calendar.getTime();
		statRequest.setStartTime(startTime);
		statRequest.setEndTime(endTime);
		
		//specify an instance
		ArrayList<Dimension> dimensions = new ArrayList<Dimension>();
		dimensions.add(new Dimension().withName("InstanceId").withValue(instanceId));
		statRequest.setDimensions(dimensions);
		
		//get statistics
		GetMetricStatisticsResult statResult = cloudWatchClient.getMetricStatistics(statRequest);
		
		//display
		//System.out.println(statResult.toString());
		List<Datapoint> dataList = statResult.getDatapoints();
		Date timeStamp = null;
		for (Datapoint data : dataList){
			averageUsage = data.getAverage();
			timeStamp = data.getTimestamp();
			//System.out.println("Instance: " + instanceId + "\n	Average CPU utlilization for last 10 minutes: "+averageUsage);
			//System.out.println("\n	Total CPU utlilization for last 10 minutes: "+data.getSum());
		}
		return averageUsage;
	}
	
	public Instance createInstance(AmazonEC2 ec2, String keyName, String securityGroup, String instanceType, String imageID) {
		Instance instance = null;
		
		
		//Create and initialize a RunInstancesRequest instance.
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.withImageId(imageID)
                           .withInstanceType(instanceType)
                           .withMinCount(1)
                           .withMaxCount(1)
                           .withKeyName(keyName)
                           .withSecurityGroups(securityGroup);
        
        //Pass the request
        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
        
        // Get new instance id
        List<Instance> instances = runInstancesResult.getReservation().getInstances();
        for(Instance instanceObj : instances){
        	instance = instanceObj;
        }
		
        
		return instance;
	}

	public String createAmi(AmazonEC2 ec2, String instanceId, String publicIp) {
		String imageId = null;
		String amiName = null;
		// Construct request
		CreateImageRequest createImageRequest = new CreateImageRequest();
		amiName = constructAMIName(publicIp);
		createImageRequest.withInstanceId(instanceId).withName(amiName);
		CreateImageResult createImageResult = ec2.createImage(createImageRequest);
		imageId = createImageResult.getImageId();
		return imageId;
	}
	public void stopInstance(AmazonEC2 ec2, String instanceId) {
		
		// Contruct request
		StopInstancesRequest stopInstancesRequest = new StopInstancesRequest();
		stopInstancesRequest.withInstanceIds(instanceId);
		StopInstancesResult stopInstancesResult = ec2.stopInstances(stopInstancesRequest);
		
		// Check state
		Boolean is_InstanceStopped = false;
		do {
			if (describeInstanceState(ec2, instanceId).equals("stopped")) {
				is_InstanceStopped = true;
			}
		} while (!is_InstanceStopped);
		
	}
	public void terminateInstance(AmazonEC2 ec2, String instanceId) {
		// Construct request
		TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();
		terminateInstancesRequest.withInstanceIds(instanceId);
		ec2.terminateInstances(terminateInstancesRequest);
		
		
	}
	public void releaseElasticIp(AmazonEC2 ec2, String publicIp) {
			DisassociateAddressRequest disassociateAddressRequest = new DisassociateAddressRequest();
			disassociateAddressRequest.withPublicIp(publicIp);
			ec2.disassociateAddress(disassociateAddressRequest);
			ReleaseAddressRequest releaseAddressRequest = new ReleaseAddressRequest();
			releaseAddressRequest.withPublicIp(publicIp);
			ec2.releaseAddress(releaseAddressRequest);
	}
	
	public String constructAMIName(String publicIp) {
		String amiName = null;
		String delimiter = ".";
		String replacement = "-";
		amiName = publicIp.replace(delimiter, replacement);
		return amiName;
	}
	public void checkImageStatus(AmazonEC2 ec2, String imageId, String state) {
		List<Image> images;
		Boolean is_ImageAvailable = false;
		do {
    		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest();
        	describeImagesRequest.withImageIds(imageId);
            DescribeImagesResult describeImagesResult = ec2.describeImages(describeImagesRequest);
            
            images = describeImagesResult.getImages();
            for(Image image : images){
            	if(!image.getState().equals(state)){
            		continue;
            	}
            	is_ImageAvailable = true;
            	List<BlockDeviceMapping> blockDeviceMappings = image.getBlockDeviceMappings();
            	for(BlockDeviceMapping blockDeviceMapping : blockDeviceMappings){
            		System.out.println("The snapshot id is " + blockDeviceMapping.getEbs().getSnapshotId());
            	}
            }
            
            
		} while (!is_ImageAvailable);
		
	}
	
	public String createVolume(AmazonEC2 ec2, int volumeSize, String availableZone, String snapshotId) {
		String volumeId = null;
		CreateVolumeRequest createVolumeRequest = new CreateVolumeRequest();
		createVolumeRequest.withSize(volumeSize).withAvailabilityZone(availableZone).withSnapshotId(snapshotId).withVolumeType("standard");
		CreateVolumeResult createVolumeResult = ec2.createVolume(createVolumeRequest);
		Volume volume = createVolumeResult.getVolume();
		volumeId = volume.getVolumeId();
		return volumeId;
	}
	
	public void attachDataVolume(AmazonEC2 ec2, String volumeId, String instanceId, String deviceName) {
		AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest();
		attachVolumeRequest.withInstanceId(instanceId).withDevice(deviceName).withVolumeId(volumeId);
		ec2.attachVolume(attachVolumeRequest);
	}
	
	public boolean checkVolumeStatus(AmazonEC2 ec2, String volumeId) {
		DescribeVolumesRequest describeVolumesRequest = new DescribeVolumesRequest();
		describeVolumesRequest.withVolumeIds(volumeId);
		DescribeVolumesResult describeVolumesResult = ec2.describeVolumes(describeVolumesRequest);
		List<Volume> volumes = describeVolumesResult.getVolumes();
		for(Volume volume : volumes){
			if (volume.getVolumeId().equals(volumeId) && volume.getState().equals("available")) {
				return true;
			}
		}
		return false;
	}
	
	public String describeVolume(AmazonEC2 ec2, String instanceId) {
		String volumeId = null;
		
		
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		describeInstancesRequest.withInstanceIds(instanceId);
		DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
		List<Reservation> reservations = describeInstancesResult.getReservations();
		Set<Instance> instances = new HashSet<Instance>();
		for(Reservation reservation : reservations){
			instances.addAll(reservation.getInstances());
		}
		for(Instance instance : instances){
			List<InstanceBlockDeviceMapping> instanceBlockDeviceMappings = instance.getBlockDeviceMappings();
			for(InstanceBlockDeviceMapping instanceBlockDeviceMapping : instanceBlockDeviceMappings){
				if (instanceBlockDeviceMapping.getDeviceName().equals(DEFAULT_DEVICE_STRING)) {
					EbsInstanceBlockDevice ebsInstanceBlockDevice = instanceBlockDeviceMapping.getEbs();
					volumeId = ebsInstanceBlockDevice.getVolumeId();
				}
				
			}
		}
		
		
		
		return volumeId;
	}

	public void detachVolume(AmazonEC2 ec2, String volumeId) {
		DetachVolumeRequest detachVolumeRequest = new DetachVolumeRequest();
		detachVolumeRequest.withVolumeId(volumeId);
		ec2.detachVolume(detachVolumeRequest);
	}
	
	public void deleteVolume(AmazonEC2 ec2, String volumeId) {
		DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest();
		deleteVolumeRequest.withVolumeId(volumeId);
		ec2.deleteVolume(deleteVolumeRequest);
	}

	public void deleteSnapshot(AmazonEC2 ec2, String snapshotId) {
		DeleteSnapshotRequest deleteSnapshotRequest = new DeleteSnapshotRequest();
		deleteSnapshotRequest.withSnapshotId(snapshotId);
		ec2.deleteSnapshot(deleteSnapshotRequest);
	}
	
	public String describeSnapshotState(AmazonEC2 ec2, String snapshotId) {
		String snapshotState = null;
		DescribeSnapshotsRequest describeSnapshotsRequest = new DescribeSnapshotsRequest();
		describeSnapshotsRequest.withSnapshotIds(snapshotId);
		DescribeSnapshotsResult describeSnapshotsResult = ec2.describeSnapshots(describeSnapshotsRequest);
		
		List<Snapshot> snapshots = describeSnapshotsResult.getSnapshots();
		for(Snapshot snapshot : snapshots){
			snapshotState = snapshot.getState();
		}
		return snapshotState;
	}
	
	public String describeVolumeState(AmazonEC2 ec2, String volumeId) {
		String volumeState = null;
		DescribeVolumesRequest describeVolumesRequest = new DescribeVolumesRequest();
		describeVolumesRequest.withVolumeIds(volumeId);
		DescribeVolumesResult describeVolumesResult = ec2.describeVolumes(describeVolumesRequest);
		
		List<Volume> volumes = describeVolumesResult.getVolumes();
		for(Volume volume : volumes){
			volumeState = volume.getState();
		}
		
		return volumeState;
	}
	
	public String describeImageState(AmazonEC2 ec2, String imageId) {
		String imageState = null;
		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest();
		describeImagesRequest.withImageIds(imageId);
		DescribeImagesResult describeImagesResult = ec2.describeImages(describeImagesRequest);
		
		List<Image> images = describeImagesResult.getImages();
		for(Image image : images){
			imageState = image.getState();
		}
		return imageState;
	}
	
	public String describeElasticIp(AmazonEC2 ec2, String instanceId) {
		String elasticIp = null;
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		describeInstancesRequest.withInstanceIds(instanceId);
		DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
		List<Reservation> reservations = describeInstancesResult.getReservations();
		List<Instance> instances = new LinkedList<Instance>();
		for(Reservation reservation : reservations){
			instances.addAll(reservation.getInstances());
		}
		for(Instance instance : instances){
			if(instance.getInstanceId().equals(instanceId)){
				elasticIp = instance.getPublicIpAddress();
			}
		}
		
		return elasticIp;
	}
	
	public void createTag(AmazonEC2 ec2, String instanceId, String tagName, String tagValue) {
		Tag tag = new Tag();
		tag.withKey(tagName).withValue(tagValue);
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		describeInstancesRequest.withInstanceIds(instanceId);
		DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
		List<Reservation> reservations = describeInstancesResult.getReservations();
		List<Instance> instances = new LinkedList<Instance>();
		for(Reservation reservation : reservations){
			instances.addAll(reservation.getInstances());
		}
		for(Instance instance : instances){
			if (instance.getInstanceId().equals(instanceId)) {
				List<String> resources = new ArrayList<String>();
				resources.add(instanceId);
				CreateTagsRequest createTagsRequest = new CreateTagsRequest();
				createTagsRequest.withTags(tag).withResources(resources);
				ec2.createTags(createTagsRequest);
			}
		}
	}
	
	public String getInstanceTag(AmazonEC2 ec2, String instanceId, String tagName) {
		String tagValue = null;
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		describeInstancesRequest.withInstanceIds(instanceId);
		DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest);
		List<Reservation> reservations = describeInstancesResult.getReservations();
		List<Instance> instances = new LinkedList<Instance>();
		for(Reservation reservation : reservations){
			instances.addAll(reservation.getInstances());
		}
		for(Instance instance : instances){
			if (instance.getInstanceId().equals(instanceId)) {
				List<Tag> tags = new LinkedList<Tag>();
				tags = instance.getTags();
				for(Tag tag : tags){
					if(tag.getKey().equals(tagName)){
						tagValue = tag.getValue();
						
					}
				}
			}
		}
		return tagValue;
	}
	
	
	
}
