package com.netflix.asgard

/**
 * Created by bselv2 on 7/9/2015.
 */
class ASGCreate {

    String ASGname
    String groupName
    String description

    // Auto Scaling Group
    Integer minSize
    Integer desiredCapacity
    Integer maxSize
    Integer defaultCooldown

    //Scale Up and Down
    Integer ScaleUpPeriod
    Integer ScaleUpThreshold
    Integer ScalingUpAdjustment
    Integer ScaleDownPeriod
    Integer ScaleDownThreshold
    Integer ScalingDownAdjustment
    List<String> loadBalancerIds

    //Frame AZ list based on image selection
    List<String> availabilityZones
    String imageId

    List<String> securityGroups
    String instType

    String TotalAlarmUp = ""
    String TotalAlarmDown = ""
    String TotalASG = ""
    String poolid
    String subnetid
    String netid
    String networkport
  String floatingIpNetwork
    String multisecuritygroup

    //Image ID and Image Name
    /*String smiNameandID = ""*/
    String cloudEnvironmentName;
}

