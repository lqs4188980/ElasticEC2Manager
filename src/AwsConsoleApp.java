/*
 * Copyright 2010-2013 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult;
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.s3.AmazonS3Client;

/**
 * Welcome to your new AWS Java SDK based project!
 *
 * This class is meant as a starting point for your console-based application that
 * makes one or more calls to the AWS services supported by the Java SDK, such as EC2,
 * SimpleDB, and S3.
 *
 * In order to use the services in this sample, you need:
 *
 *  - A valid Amazon Web Services account. You can register for AWS at:
 *       https://aws-portal.amazon.com/gp/aws/developer/registration/index.html
 *
 *  - Your account's Access Key ID and Secret Access Key:
 *       http://aws.amazon.com/security-credentials
 *
 *  - A subscription to Amazon EC2. You can sign up for EC2 at:
 *       http://aws.amazon.com/ec2/
 *
 *  - A subscription to Amazon SimpleDB. You can sign up for Simple DB at:
 *       http://aws.amazon.com/simpledb/
 *
 *  - A subscription to Amazon S3. You can sign up for S3 at:
 *       http://aws.amazon.com/s3/
 */
public class AwsConsoleApp {

    /*
     * Important: Be sure to fill in your AWS access credentials in the
     *            AwsCredentials.properties file before you try to run this
     *            sample.
     * http://aws.amazon.com/security-credentials
     */
	
	/*
	 * Class VM_Manager handles operations of instances
	 * uid stores the user IDs. 
	 */
	
    static AmazonEC2      ec2;
    static AmazonS3Client s3;
	static AmazonCloudWatchClient cloudWatch;
	static AmazonAutoScalingClient autoScalingClient;
    static VM_Manager manager;
    static List<User> users;
    static int instanceNum;
    static final int USERNUM = 2;
	static final String INIT_IMAGEID = "ami-54cf5c3d";
	static final String INSTANCE_TYPE_STRING = "t1.micro";
	static final String SECURITY_GROUP_STRING = "JavaConsoleSecurityGroup";
	static final String AVAILABILITY_ZONE_STRING = "us-east-1b";
	static final String DEFAULT_DEVICE_STRING = "/dev/sdf";
	static final double UPPER_BOUND_THRESHOLD = 70.0;
	static final double LOWER_BOUND_THRESHOLD = 20.0;
	static final String LESS_THAN_THRESHOLD = "LessThanThreshold";
	static final String GREATER_THAN_THRESHOLD = "GreaterThanThreshold";
	static List<String> availabilityZones;
	

    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     */
    private static void init() throws Exception {
    	System.out.println("********************* Initialization **********************");
    	
    	// Some boolean values
    	Boolean is_AllInstancesTerminate;
    	
    	// Initialize users
    	users = new LinkedList<User>();
    	User user1 = new User("100");
    	User user2 = new User("101");
    	users.add(user1);
    	users.add(user2);
    	
    	// Initial a new VM_Manager
    	manager = new VM_Manager();
    	
    	// Initial availablity zone list
    	availabilityZones = new LinkedList<String>();
    	
    	/*
		 * This credentials provider implementation loads your AWS credentials
		 * from a properties file at the root of your classpath.
		 */
        AWSCredentialsProvider credentialsProvider = new ClasspathPropertiesFileCredentialsProvider();
        ec2 = new AmazonEC2Client(credentialsProvider);
        s3 = new AmazonS3Client(credentialsProvider);
        cloudWatch = new AmazonCloudWatchClient(credentialsProvider);
        autoScalingClient = new AmazonAutoScalingClient(credentialsProvider);
        
        // Describe availability
        DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
        for(AvailabilityZone availabilityZone : availabilityZonesResult.getAvailabilityZones()){
        	availabilityZones.add(availabilityZone.getZoneName());
        }
        System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() +
                " Availability Zones.");
        
        // Terminate all the running instances
        do {
			is_AllInstancesTerminate = manager.cleanOperation(ec2,autoScalingClient,cloudWatch);
		} while (!is_AllInstancesTerminate);
        System.out.println("All the instances has been terminated");
        
        // Initialize instance ip list
        instanceNum = 0;
        // Run instance for each user
        for(User user : users){
        	
        	String uid = user.uid;
        	String instanceId = null;
        	String elasticIp = null;
        	String keyName = null;
        	String bucketName = null;
        	
        	Instance instance;
        	String availabilityZone = null;
        	
        	// Output user id
        	System.out.println("\nCurrent is creating instance for user " + uid);
        	// Create key pair for this user
        	keyName = manager.createKeyPair(ec2, uid);
        	
        	// Run an instance
        	instance = manager.createInstance(ec2, keyName, SECURITY_GROUP_STRING, INSTANCE_TYPE_STRING, INIT_IMAGEID);
        	instanceId = instance.getInstanceId();
        	// Check if the instance has started
        	Boolean is_instanceRunning = false;
        	do {
        		Thread.sleep(15000);
				System.out.println("The instance " + instanceId + " is still creating, please waiting");
				if (manager.describeInstanceState(ec2, instanceId).equals("running")) {
					is_instanceRunning = true;
				}
				
			} while (!is_instanceRunning);
        	
        	// Get Zone
        	availabilityZone = instance.getPlacement().getAvailabilityZone();
        	
        	// Create a data volume
            String volumeId = manager.createVolume(ec2, 1, availabilityZone, null);
            System.out.println("The data volume " + volumeId +" has been created");
            
            // Check volume state
            while (!manager.checkVolumeStatus(ec2, volumeId)) {
				
			}
            
            // Set attach
            manager.attachDataVolume(ec2, volumeId, instanceId, DEFAULT_DEVICE_STRING);
            System.out.println("The data volume " + volumeId +" has been attach to instance " + instanceId);
        	
        	// Assign an elastic ipAddress
        	elasticIp = manager.allocateElasticIp(ec2);
        	manager.assignElasticIp(ec2, instanceId, elasticIp);
        	
        	// Create Tag for this instance
        	manager.createTag(ec2, instanceId, "Uid", uid);
        	
        	// Create a bucket
        	bucketName = manager.createBucket(s3, uid);
        	
        	// Add Alarms
        	List<String> instanceList = new ArrayList<String>();
        	instanceList.add(instanceId);
        		// Add upper bound alarm
        		manager.createAlarm(cloudWatch, instanceList, instanceId + "-UpperBound", UPPER_BOUND_THRESHOLD, GREATER_THAN_THRESHOLD);
        		// Add lower bound alarm
        		manager.createAlarm(cloudWatch, instanceList, instanceId + "-LowerBound", LOWER_BOUND_THRESHOLD, LESS_THAN_THRESHOLD);
        		
        	// Store these information 
        	user.bucketList.add(bucketName);
        	user.elasticIpMap.put(elasticIp,instanceId);
        	user.imageMap.put(elasticIp,INIT_IMAGEID);
        	user.keyName = keyName;
        	instanceNum++;
        	
        	// Output informations
        	System.out.println("User " + uid + " has assigned an instance, the instance id is " + user.elasticIpMap.get(elasticIp));
        	System.out.println("User " + uid + "'s key name is " + user.keyName);
        	System.out.println("User " + uid + "'s bucket name is " + user.bucketList.get(0));
        	System.out.println("Public IP address " + elasticIp + " has been assigned to instance " + user.elasticIpMap.get(elasticIp));
        	System.out.println("The image used to create instance " + instanceId + " is " + user.imageMap.get(elasticIp) + "\n");
        	Thread.sleep(10000);
        	// 
        }
        
        
    }
    public static void main(String[] args) throws Exception {

        System.out.println("===========================================");
        System.out.println("Welcome to the AWS Java SDK!");
        System.out.println("===========================================");
        
        
        init();

        /*
         * Amazon EC2
         *
         * The AWS EC2 client allows you to create, delete, and administer
         * instances programmatically.
         *
         * In this sample, we use an EC2 client to get a list of all the
         * availability zones, and all instances sorted by reservation id.
         */
        
        
        try {
        	System.out.println("********************* Sleep for 3 mins and waiting for the VM into idle **********************");
        	// Sleep 3 minutes
        	Thread.sleep(180000);
        	
        	DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        	Date currentDate = new Date();
        	Date releaseDate = new Date();
        	Date restoreDate = new Date();
        	// One minutes later
        	releaseDate.setTime(currentDate.getTime() + 600000);

        	// Ten minutes later after the release date
        	restoreDate.setTime(releaseDate.getTime() + 300000);
        	
    		//Map<String, Integer> instancesRunningLog = new HashMap<String, Integer>();
    		
    		System.out.println("********************** Now entering the monitoring process *********************");
        	while(true){
        		System.out.println("********************** Reread monitor data *********************");
        		// Get current date
        		Date date = new Date();
        		System.out.println("Current time is " + dateFormat.format(date));
        		System.out.println("Day end time is " + dateFormat.format(releaseDate));
        		System.out.println("Day begin time is " + dateFormat.format(restoreDate));
        		System.out.println("Current instance number is " + instanceNum);
        		
        		// Monitor whether reach the release time
        		if(date.getTime() > releaseDate.getTime() && instanceNum > 0){
        			System.out.println("\n********************** Reaching End of A Day *********************");
        			for(User user : users){
        				for(String instanceId : user.scaledInstances){
        					manager.terminateInstance(ec2, instanceId);
        					instanceNum--;
        				}
        				System.out.println("All the scaled instance has been terminated");
        				Set<String> elasticIps = user.elasticIpMap.keySet();
        				for(String elasticIp : elasticIps){
        					//instancesRunningLog.remove(user.elasticIpMap.get(elasticIp));
        					List<String> alarmNames = new ArrayList<String>();
        					String instanceIdString = user.elasticIpMap.get(elasticIp);
        					alarmNames.add(instanceIdString + "-UpperBound");
        					alarmNames.add(instanceIdString + "-LowerBound");  
        					manager.deleteAlarm(cloudWatch, alarmNames);
        					snapshotVMs(ec2, user, elasticIp);
        					
        				}
        			}
        			
        		}
        		// Monitor whether reach the start time
        		if(date.getTime() > restoreDate.getTime()){
        			System.out.println("\nNow restore instances from AMI");
        			for(User user : users){
        				Set<String> elasticIps = user.elasticIpMap.keySet();
        				for(String elasticIp : elasticIps){
        					restoreVMs(ec2, user, elasticIp);
        					String instanceIdString = user.elasticIpMap.get(elasticIp);
        					List<String> instanceIds = new ArrayList<String>();
        					instanceIds.add(instanceIdString);
        					manager.createAlarm(cloudWatch, instanceIds, instanceIdString + "-UpperBound", UPPER_BOUND_THRESHOLD, GREATER_THAN_THRESHOLD);
        					manager.createAlarm(cloudWatch, instanceIds, instanceIdString + "-LowerBound", LOWER_BOUND_THRESHOLD, LESS_THAN_THRESHOLD);
        					//instancesRunningLog.put(user.elasticIpMap.get(elasticIp), 0);
        				}
        			}
        			setNextDay(releaseDate, restoreDate);
        		}
        		// Get Alarm State
        		Map<String, String> instanceWithAlarms = new HashMap<String, String>();
        		instanceWithAlarms = manager.getAlarmStateInstances(cloudWatch);
        		if (!instanceWithAlarms.isEmpty()) {
					Set<String> instances = instanceWithAlarms.keySet();
					for(String instanceIdString : instances){
						String alarmNameString = instanceWithAlarms.get(instanceIdString);
						String[] stringCache = alarmNameString.split("-");
						if (stringCache[2].equals("UpperBound")) {
							System.out.println("Instance " + instanceIdString + " get an alarm for reaching " + stringCache[2]);
							String uid = manager.getInstanceTag(ec2, instanceIdString, "Uid");
							for(User user : users){
								if(user.uid.equals(uid)){
									int scaleCapacity = 2;
									for (int i = 0; i < scaleCapacity; i++) {
										String instanceId = manager.createInstance(ec2, user.keyName, SECURITY_GROUP_STRING, INSTANCE_TYPE_STRING, INIT_IMAGEID).getInstanceId();
										user.scaledInstances.add(instanceId);
										instanceNum++;
										System.out.println("Scale out instance " + instanceId + " for user " + user.uid);
									}
								}
							}
							List<String> alarmNames = new ArrayList<String>();
							alarmNames.add(alarmNameString);
							manager.deleteAlarm(cloudWatch, alarmNames);
						}
						else if (stringCache[2].equals("LowerBound")) {
							System.out.println("Instance " + instanceIdString + " get an alarm for reaching " + stringCache[2]);
							String uid = manager.getInstanceTag(ec2, instanceIdString, "Uid");
							for(User user : users){
								if (user.uid.equals(uid)) {
									if(user.scaledInstances.size() > 0){
										for(String instanceId : user.scaledInstances){
											manager.terminateInstance(ec2, instanceId);
											instanceNum--;
											System.out.println("Scaled instance " + instanceId + " for user " + user.uid + " has been terminated");
										}
									}
									String elasticIp = manager.describeElasticIp(ec2, instanceIdString);
									List<String> alarmNames = new ArrayList<String>();
			        				alarmNames.add(instanceIdString + "-UpperBound");
			        				alarmNames.add(instanceIdString + "-LowerBound");  
			        				manager.deleteAlarm(cloudWatch, alarmNames);
									snapshotVMs(ec2, user, elasticIp);
									System.out.println("Instance " + instanceIdString + "has been snapshot");
								}
							}
							
						}
					}
				}
        		
        		
        		Thread.sleep(30000);
        	}
        	
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
        
       
    }
    
    public static void snapshotVMs(AmazonEC2 ec2, User user, String publicIp) throws InterruptedException {
    	String instanceId = user.elasticIpMap.get(publicIp);
		
		
		if(instanceId == null){
			return;
		}
		
		/*
		if(!originImageId.equals(INIT_IMAGEID)){
			// 0. Clean AMI and snapshot
			List<String> snapshotIdList = new LinkedList<String>();
			DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest();
			describeImagesRequest.withImageIds(originImageId);
			DescribeImagesResult describeImagesResult = ec2.describeImages(describeImagesRequest);
			
			List<Image> images = describeImagesResult.getImages();
			for(Image image : images){
				List<BlockDeviceMapping> blockDeviceMappings = image.getBlockDeviceMappings();
				for(BlockDeviceMapping blockDeviceMapping : blockDeviceMappings){
					EbsBlockDevice ebsBlockDevice = blockDeviceMapping.getEbs();
					snapshotIdList.add(ebsBlockDevice.getSnapshotId());
				}
			}
			manager.deregisterAMI(ec2, originImageId);
			Boolean is_ImageDeregistered = false;
			do {
				if(!manager.describeImageState(ec2, originImageId).equals("available")){
					is_ImageDeregistered = true;
				}
			} while (!is_ImageDeregistered);
			for(String snapshotId : snapshotIdList){
				manager.deleteSnapshot(ec2, snapshotId);
			}
			
		}
		*/
		// 1. Stop VM
		System.out.println("\n********************** Now stopping instance " + instanceId + " **********************");
		manager.stopInstance(ec2, instanceId);
		
		
		
		// 2. Create AMI
		System.out.println("Now creating AMI for instance " + instanceId);
		String imageId = manager.createAmi(ec2, instanceId, publicIp);
		Thread.sleep(15000);
		Boolean is_ImageAvailable = false;
		do {
			if(manager.describeImageState(ec2, imageId).equals("available")){
				is_ImageAvailable = true;
			}
		} while (!is_ImageAvailable);
		System.out.println("The image " + imageId + " is for instance " + instanceId);
		

		// 3. Detach data volume
		System.out.println("Now detach data volume for instance " + instanceId);
		String volumeId = manager.describeVolume(ec2, instanceId);
		manager.detachVolume(ec2, volumeId);
		System.out.println("The volume " + volumeId + " has been detached");
		/*
		// 4. Create snapshot for data volume
		System.out.println("Now creating snapshot for data volume " + volumeId);
		String snapshotId = manager.createSnapshot(ec2, volumeId);
		Boolean is_SnapshotCompleted = false;
		do {
			if(manager.describeSnapshotState(ec2, snapshotId).equals("completed")){
				is_SnapshotCompleted = true;
			}
		} while (!is_SnapshotCompleted);
		System.out.println("The snapshot " + snapshotId + " has been created");
		*/
		
		// 5. Delete data volume
		System.out.println("Now deleting data volume " + volumeId);
		Boolean is_VolumeAvailable = false;
		do {
			if (manager.describeVolumeState(ec2, volumeId).equals("available")) {
				is_VolumeAvailable = true;
			}
		} while (!is_VolumeAvailable);
		manager.deleteVolume(ec2, volumeId);
		System.out.println("The volume " + volumeId + " has been deleted");
	    
		
		
		
		// 6. Terminate instance
		System.out.println("Now terminating instance");
		manager.terminateInstance(ec2, instanceId);
		System.out.println("The instance " + instanceId + " has been released\n");
		
		
		// 7. Update information
		user.elasticIpMap.remove(publicIp);
		user.elasticIpMap.put(publicIp, null);
		
		user.imageMap.remove(publicIp);
		user.imageMap.put(publicIp, imageId);
		
		instanceNum--;
		/*
		user.snapshotMap.remove(publicIp);
		user.snapshotMap.put(publicIp, snapshotId);
		*/
		
	}
    public static void restoreVMs(AmazonEC2 ec2, User user, String elasticIp) throws InterruptedException {
    	String imageId = user.imageMap.get(elasticIp);
		String keyName = user.keyName;
		String instanceId;
		Instance instance;
		
		System.out.println("\n********************** Now restore instance for ip address " + elasticIp + " **********************");
		// 0. Check AMI status
		Boolean is_ImageAvailable = false;
		do {
			if (manager.describeImageState(ec2, imageId).equals("available")) {
				is_ImageAvailable = true;
			}
		} while (!is_ImageAvailable);
		
		// 1. Create instance from AMI
		instance = manager.createInstance(ec2, keyName, SECURITY_GROUP_STRING, INSTANCE_TYPE_STRING, imageId);
		instanceId = instance.getInstanceId();
		
		// Check if the instance has started
    	Boolean is_instanceRunning = false;
    	do {
			System.out.println("The instance " + instanceId + " is still creating, please waiting");
			if (manager.describeInstanceState(ec2, instanceId).equals("running")) {
				is_instanceRunning = true;
			}
			Thread.sleep(10000);
		} while (!is_instanceRunning);
		System.out.println("The instance " + instanceId + " is running");
		/*
		// 2. Attach data volume
		availableZone = instance.getPlacement().getAvailabilityZone();
		snapshotId = user.snapshotMap.get(elasticIp);
		String volumeId = manager.createVolume(ec2, 1, availableZone, snapshotId);
		manager.attachDataVolume(ec2, volumeId, instanceId, DEFAULT_DEVICE_STRING);
		*/
		// 3. Associate elastic IP address with instance
		manager.assignElasticIp(ec2, instanceId, elasticIp);
		System.out.println("The newly created instance " + instanceId + " has been assigned IP " + elasticIp);
		
		// 4. Clean AMI and snapshot
		List<String> snapshotIdList = new LinkedList<String>();
		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest();
		describeImagesRequest.withImageIds(imageId);
		DescribeImagesResult describeImagesResult = ec2.describeImages(describeImagesRequest);
		
		List<Image> images = describeImagesResult.getImages();
		for(Image image : images){
			List<BlockDeviceMapping> blockDeviceMappings = image.getBlockDeviceMappings();
			for(BlockDeviceMapping blockDeviceMapping : blockDeviceMappings){
				EbsBlockDevice ebsBlockDevice = blockDeviceMapping.getEbs();
				snapshotIdList.add(ebsBlockDevice.getSnapshotId());
			}
		}
		manager.deregisterAMI(ec2, imageId);
		Thread.sleep(10000);
		for(String snapshot : snapshotIdList){
			manager.deleteSnapshot(ec2, snapshot);
		}
		
		// Create Tag
		manager.createTag(ec2, instanceId, "Uid", user.uid);
		
		// 5. Update information
				user.elasticIpMap.remove(elasticIp);
				user.elasticIpMap.put(elasticIp, instanceId);
				
				user.imageMap.remove(elasticIp);
				user.imageMap.put(elasticIp, null);
				instanceNum++;
	}
    
	public static void setNextDay(Date releaseDate, Date restoreDate) {
		Date date = new Date();
		releaseDate.setTime(date.getTime() + 300000);
		restoreDate.setTime(releaseDate.getTime() + 300000);
		
	}
	
	public static void setAutoScale(AmazonAutoScalingClient autoScalingClient, AmazonCloudWatchClient cloudWatchClient, User user) throws InterruptedException {
		// Get information from user
		String keyName = user.keyName;
		String scaleOutARN = null;
		String scaleInARN = null;
		
		// Check whether a launch configuration is exist
		DescribeLaunchConfigurationsResult describeLaunchConfigurationsResult = autoScalingClient.describeLaunchConfigurations();
		Boolean is_LaunchConfigurationExist = false;
		List<LaunchConfiguration> launchConfigurations = describeLaunchConfigurationsResult.getLaunchConfigurations();
		for(LaunchConfiguration launchConfiguration : launchConfigurations){
			String nameString = launchConfiguration.getLaunchConfigurationName();
			if (nameString.equals("LaunchConfigurationForItDepartment")) {
				System.out.println("The launch configuration " + nameString + " has existed.");
				is_LaunchConfigurationExist = true;
				break;
			}
		}
		
		if (!is_LaunchConfigurationExist) {			
			// Create new launch configuration
			CreateLaunchConfigurationRequest createLaunchConfigurationRequest = new CreateLaunchConfigurationRequest();
			createLaunchConfigurationRequest.withLaunchConfigurationName("LaunchConfigurationForItDepartment")
											.withImageId(INIT_IMAGEID)
											.withInstanceType(INSTANCE_TYPE_STRING)
											.withKeyName(keyName)
											.withSecurityGroups(SECURITY_GROUP_STRING);
			autoScalingClient.createLaunchConfiguration(createLaunchConfigurationRequest);
		}
		
		DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult = autoScalingClient.describeAutoScalingGroups();
		List<AutoScalingGroup> autoScalingGroups = describeAutoScalingGroupsResult.getAutoScalingGroups();
		Boolean is_AutoScalingGroupExist = false;
		for(AutoScalingGroup autoScalingGroup : autoScalingGroups){
			String name = autoScalingGroup.getAutoScalingGroupName();
			if (name.equals("AutoScale Group For IT-Department")) {
				is_AutoScalingGroupExist = true;
				break;
			}
		}
		if (!is_AutoScalingGroupExist) {
			//Create an Auto Scaling group
			CreateAutoScalingGroupRequest createAutoScalingGroupRequest = new CreateAutoScalingGroupRequest();
			createAutoScalingGroupRequest.withAutoScalingGroupName("AutoScale Group For IT-Department")
										 .withAvailabilityZones(availabilityZones)
										 .withMaxSize(3)
										 .withMinSize(0)
										 .withLaunchConfigurationName("LaunchConfigurationForItDepartment");
			autoScalingClient.createAutoScalingGroup(createAutoScalingGroupRequest);
		}
		
		
		
		DescribePoliciesResult describePoliciesResult = autoScalingClient.describePolicies();
		List<ScalingPolicy> policies = describePoliciesResult.getScalingPolicies();
		Boolean is_ScalingOutPolicyExist = false;
		Boolean is_ScalingInPolicyExist = false;
		for(ScalingPolicy scalingPolicy : policies){
			String name = scalingPolicy.getPolicyName();
			if (name.equals("ScaleOutPolicy")) {
				is_ScalingOutPolicyExist = true;
			}
			if (name.equals("ScaleInPolicy")) {
				is_ScalingInPolicyExist = true;
			}
		}
		if (!is_ScalingOutPolicyExist) {
			//Create two policies, one for scaling out and one for scaling in.
			PutScalingPolicyRequest putScalingPolicyRequest = new PutScalingPolicyRequest();
			putScalingPolicyRequest.withAutoScalingGroupName("AutoScale Group For IT-Department")
								   .withPolicyName("ScaleOutPolicy")
								   .withScalingAdjustment(2)
								   .withAdjustmentType("ChangeInCapacity");
			PutScalingPolicyResult putScalingPolicyResult = autoScalingClient.putScalingPolicy(putScalingPolicyRequest);
			scaleOutARN = putScalingPolicyResult.getPolicyARN();
		}
		if (!is_ScalingInPolicyExist) {
			//Create two policies, one for scaling out and one for scaling in.
			PutScalingPolicyRequest putScalingPolicyRequest = new PutScalingPolicyRequest();
			putScalingPolicyRequest.withAutoScalingGroupName("AutoScale Group For IT-Department")
								   .withPolicyName("ScaleInPolicy")
								   .withScalingAdjustment(-2)
								   .withAdjustmentType("ChangeInCapacity");
			PutScalingPolicyResult putScalingPolicyResult = autoScalingClient.putScalingPolicy(putScalingPolicyRequest);
			scaleInARN = putScalingPolicyResult.getPolicyARN();
		}
		
		// Create Alarms
		DescribeAlarmsResult describeAlarmsResult = cloudWatchClient.describeAlarms();
		List<MetricAlarm> alarms = describeAlarmsResult.getMetricAlarms();
		Boolean is_ScaleOutAlarmExist = false;
		Boolean is_ScaleInAlarmExist = false;
		for(MetricAlarm alarm : alarms){
			String nameString = alarm.getAlarmName();
			if (nameString.equals("AutoScaleOutAlarm")) {
				is_ScaleOutAlarmExist = true;
			}
			if (nameString.equals("AutoScaleInAlarm")) {
				is_ScaleInAlarmExist = true;
			}
		}
		if (!is_ScaleOutAlarmExist) {
			//Create CloudWatch alarms to watch over metrics that Auto Scaling sends to CloudWatch. This walkthrough uses the CPUUtilization metrics.
			Dimension dimension = new Dimension();
			dimension.withName("AutoScalingGroupName").withValue("AutoScale Group For IT-Department");
			PutMetricAlarmRequest putMetricAlarmRequest = new PutMetricAlarmRequest();
			putMetricAlarmRequest.withAlarmName("AutoScaleOutAlarm")
								 .withDimensions(dimension)
								 .withMetricName("CPUUtilization")
								 .withPeriod(60)
								 .withStatistic("Average")
								 .withThreshold(90.0)
								 .withNamespace("AWS/EC2")
								 .withComparisonOperator("GreaterThanThreshold")
								 .withAlarmActions(scaleOutARN)
								 .withEvaluationPeriods(10);
			cloudWatchClient.putMetricAlarm(putMetricAlarmRequest);
		}
		if (!is_ScaleInAlarmExist) {
			//Create CloudWatch alarms to watch over metrics that Auto Scaling sends to CloudWatch. This walkthrough uses the CPUUtilization metrics.
			Dimension dimension = new Dimension();
			dimension.withName("AutoScalingGroupName").withValue("AutoScale Group For IT-Department");
			PutMetricAlarmRequest putMetricAlarmRequest = new PutMetricAlarmRequest();
			putMetricAlarmRequest.withAlarmName("AutoScaleInAlarm")
								 .withDimensions(dimension)
								 .withMetricName("CPUUtilization")
								 .withPeriod(60)
								 .withStatistic("Average")
								 .withThreshold(10.0)
								 .withNamespace("AWS/EC2")
								 .withComparisonOperator("LessThanThreshold")
								 .withAlarmActions(scaleInARN)
								 .withEvaluationPeriods(10);
			cloudWatchClient.putMetricAlarm(putMetricAlarmRequest);
		}
		

	}
	
	public void scaleOut(AmazonEC2 ec2, User user) {
		int scaleUpCapacity = 2;
		for (int i = 0; i < scaleUpCapacity; i++) {
			
		}
	}
	
}
