package com.cluster
import com.netflix.asgard.ASGCreate
import com.netflix.asgard.Time
import com.serve.asgard.enums.ResourceType
import com.serve.asgard.model.*
import com.serve.asgard.model.hot.sections.resource.OSCeilometerAlarmResource
import com.serve.asgard.model.hot.sections.resource.OSHeatAutoScalingGroupResource
import com.serve.asgard.model.hot.sections.resource.OSNeutronLoadBalancerResource
import com.serve.asgard.model.hot.sections.resource.OSHeatScalingPolicyResource
import org.springframework.beans.factory.InitializingBean

/**
 * Created by bselv2 on 7/9/2015.
 */
class AutoScalingService implements InitializingBean {

  def openStackAutoScalingService
  def networkService
  def configService
  def openStackLoadBalancerService
  def openStackHeatService
  def heatService
  def computeService
  def glanceService
  def openStackComputeService
  def appDatabaseMongoService
  AppDatabase appDatabase
  def asgardUserContextService
  def graphiteService

    void afterPropertiesSet() {
    appDatabase = appDatabaseMongoService
  }

  void CreateASG(ASGCreate newASG, String appName) {
    List<AvailabilityZone> recommendedZonesOS = openStackComputeService.getAvailabilityZones()
    newASG.availabilityZones = recommendedZonesOS*.name

    // Get Max AZ size from Config file
    Integer MaxAZ = (newASG.availabilityZones) ? newASG.availabilityZones.size() : 0

    String asgresource = new File(configService.asgardHome, 'OSASGResourcewithScaleup.json').text
    String alarmResource = new File(configService.asgardHome, 'OSAlarmAttribute.json').text
    log.info("ASGResource File: ${asgresource}")
    log.info("alarmresource File: ${alarmResource}")

    //Need to check multiple property???
    newASG.loadBalancerIds.each { loadBalancerPoolId ->
      LoadBalancer loadBalancer = openStackLoadBalancerService.getLoadBalancer(loadBalancerPoolId)
      newASG.poolid = loadBalancer.id;
      newASG.subnetid = loadBalancer.subnet_id;
      newASG.networkport = loadBalancer.protocol_port ?: 443;
    };

    //Get Image metedata for instance name
    def imagedetail = glanceService.getImage(newASG.imageId)
	//// Defect #DE1835 ASG Instances Machine Names missing P or D
    String DevPlatform = getCloundEnvironmetName(asgardUserContextService.currentTenant.name) == "PROD" ? "P" : "D"
    String OSPlatform = imagedetail.platform ?: "D"
    String region = imagedetail.region ?: "TPA"
    String platform = DevPlatform + OSPlatform
    String tenant = imagedetail.component ?: "N-A"
    String app = imagedetail.app ?: "NA"

    newASG.multisecuritygroup = '"' + newASG.securityGroups.join('","') + '"'


    for (int asgNo = 0; asgNo < MaxAZ; asgNo++) {
      String osasg = asgresource.replaceAll("#smiid", newASG.imageId)
        .replaceAll("#asgname", newASG.ASGname)
        .replaceAll("#minsize", newASG.minSize?.toString())
        .replaceAll("#maxsize", newASG.maxSize?.toString())
        .replaceAll("#cooldown", newASG.defaultCooldown?.toString())
        .replaceAll("#desiredcapacity", newASG.desiredCapacity?.toString())
        .replaceAll("#flavor", newASG.instType?.toString())
        .replaceAll("#zone", newASG.availabilityZones[asgNo].toString())
        .replaceAll("#secgroups", newASG.multisecuritygroup)
        .replaceAll("#subnetid", newASG.subnetid)
        .replaceAll("#poolid", newASG.poolid)
        .replaceAll("#CLOUD_ENVIRONMENT", newASG.cloudEnvironmentName)
        .replaceAll("#netid", newASG.netid)
        .replaceAll("#externalNetwork", newASG.floatingIpNetwork)
        .replaceAll("#region", region)
        .replaceAll("#platform", platform)
        .replaceAll("#tenant", tenant)
        .replaceAll("#app", app)
        .replaceAll("#scaleupadjustment", newASG.ScalingUpAdjustment?.toString())
        .replaceAll("#scaledownadjustment", newASG.ScalingDownAdjustment?.toString())
        .replaceAll("#asgNo", asgNo?.toString())
      newASG.TotalASG += osasg
    }

    for (int alarmNo = 0; alarmNo < MaxAZ; alarmNo++) {
      String osAlarm = alarmResource.replaceAll("#asgNo", alarmNo?.toString())
      newASG.TotalAlarmUp += osAlarm
    }
    if (newASG.TotalAlarmUp.endsWith(',')) {
      newASG.TotalAlarmUp = newASG.TotalAlarmUp.substring(0, newASG.TotalAlarmUp.length() - 1)
    }
    newASG.TotalAlarmDown = newASG.TotalAlarmUp.replaceAll("up", "down")
    log.info("TotalAlarmUp: ${newASG.TotalAlarmUp}")
    log.info("TotalAlarmDown: ${newASG.TotalAlarmDown}")

    //Final Template File
    String fileContents = new File(configService.asgardHome, 'OSHeatwithScaleup.json').text
    log.info("OSTemplate File: ${fileContents}")

    String ostemplate = fileContents.replaceAll("#asgname", "$newASG.ASGname")
      .replaceAll("#OSASGResource", newASG.TotalASG)
      .replaceAll("#OSAlarmUpAttribute", newASG.TotalAlarmUp)
      .replaceAll("#OSAlarmDownAttribute", newASG.TotalAlarmDown)
      .replaceAll("#alarmupperiod", newASG.ScaleUpPeriod?.toString())
      .replaceAll("#alarmupthreshold", newASG.ScaleUpThreshold?.toString())
      .replaceAll("#alarmdownperiod", newASG.ScaleDownPeriod?.toString())
      .replaceAll("#alarmdownthreshold", newASG.ScaleDownThreshold?.toString())
      .replaceAll("#alarmdownthreshold", newASG.ScaleDownThreshold?.toString())
      .replaceAll("#poolid", newASG.poolid?.toString())
      .replaceAll("#networkport", newASG.networkport?.toString())
      .replaceAll("#description", newASG.description?.toString())
      .replaceAll("#url", configService.getURLforOSTemplate())

    //def response = openStackHeatService.createStackWithHeatTemplate(ostemplate, newASG.ASGname)
    def response = heatService.createStackWithHeatTemplate(ostemplate)

    log.info("Stack Created. ID: ${response?.stack?.id}")
    graphiteService.incrementCreateASG()

    if (response?.stack?.id != null) {
      openStackAutoScalingService.SavetoMongodb(newASG.groupName, newASG.ASGname, appName, response?.stack?.id, response?.stack?.stackName, newASG.poolid, null)
    } else {
      log.error("Failed to create Auto Scaling Group.")
      throw new RuntimeException("Failed to create Auto Scaling Group.")

    }

  }


  public Map<String, Object> getAsgDetails(String asgName) {

    Map<String, Object> details = [:]
    HeatStack stack
    Map<String, List<Instance>> asgInstancesPerAZ
    TreeSet<SecurityGroup> securityGroups = []
    //String sshKey = ''
    ImageV2 image

    AutoScalingGroup autoScalingGroup = openStackAutoScalingService.getAutoScalingGroup(asgName)

    if (autoScalingGroup?.stack?.stackId) {

      stack = heatService.getStackDetails(autoScalingGroup.stack.stackName, autoScalingGroup.stack.stackId)
      if (stack) {
        stack.template = heatService.getTemplate(autoScalingGroup.stack.stackName, autoScalingGroup.stack.stackId)
      }
      if (stack) {
        List<Instance> asgInstances = []
        //Get List of all instances from openstack
        List<Instance> instanceList = computeService.getInstanceList()
        // now get the instance that belong to this ASG
        instanceList?.each { instance ->
          //check if the asgname is listed in the metadata
          instance.metadata?.each { key, value ->
            if ('metering.stack'.equalsIgnoreCase(key) && autoScalingGroup.stack.stackId.equalsIgnoreCase(value)) {
              asgInstances.add(instance)
              if (instance.securityGroups) {
              securityGroups.addAll(instance.securityGroups)
              }
              // sshKey = instance.keyName
              if (!image) {
                image = glanceService.getImage(instance.image.id)
              }
            }
          }
        }
        // now split the instances per the availability zone
        asgInstancesPerAZ = splitInstancesPerAZ(asgInstances)

        // get the minSize,maxSize and desiredCapacity of the asg
        OSHeatAutoScalingGroupResource templateAsgResource = stack.template.getResource(ResourceType.OS_HEAT_AUTOSCALINGGROUP)

        //get the load balancer id from the template and then the details
        OSNeutronLoadBalancerResource templateLoadBalancerResource = stack.template.getOsNeutronLoadBalancerResource()
        String loadBalancerPoolId = templateLoadBalancerResource.properties.poolId
        LoadBalancer loadBalancer = networkService.getLoadBalancer(loadBalancerPoolId)

        //get Alarm high and low properties
        OSCeilometerAlarmResource templateCeilometerHigh = stack.template.getCeilometer(true)
        OSCeilometerAlarmResource templateCeilometerLow = stack.template.getCeilometer(false)

        //get Scale Up and down properties
        OSHeatScalingPolicyResource templateScaleUp = stack.template.getScalePolicy(true)
        OSHeatScalingPolicyResource templateScaleDown = stack.template.getScalePolicy(false)

        String updatedTime
        if (stack.updatedTime) {
          updatedTime = Time.format(Time.parse(stack.updatedTime))
        }

        details = [
          //'asgDetail': asgDetail,
          'applicationName'  : appDatabase.getApplicationID(autoScalingGroup.stack.stackName),
          'clusterName'      : appDatabase.getclustername(autoScalingGroup.stack.stackName),
          'asgName'          : asgName,
          'asgInstancesPerAZ': asgInstancesPerAZ,
          'stack'            : stack,
          'minSize'          : templateAsgResource.properties.minSize,
          'maxSize'          : templateAsgResource.properties.maxSize,
          'desiredCapacity'  : templateAsgResource.properties.desiredCapacity,
          'cooldown'         : templateAsgResource.properties.cooldown,
          'metername'        : templateCeilometerHigh.properties.meter_name,
          'scaleupthreshold'  : templateCeilometerHigh.properties.threshold,
          'scaleupperiod'     : templateCeilometerHigh.properties.period,
          'scalingUpadjustment' : templateScaleUp.properties.scalingadjustment,
          'scaledownthreshold': templateCeilometerLow.properties.threshold,
          'scaledownperiod'   : templateCeilometerLow.properties.period,
          'scalingDownadjustment' : templateScaleDown.properties.scalingadjustment,
          'createdTime'      : Time.format(Time.parse(stack.creationTime)),
          'lastUpdatedTime'  : updatedTime,
          'loadBalancer'     : loadBalancer,
          'securityGroups'   : securityGroups,
          // 'sshKey'              : sshKey,
          'image'            : image
        ]
      }
    }
    return details
  }

  Map<String, List<Instance>> splitInstancesPerAZ(List<Instance> instances) {
    TreeMap<String, List<Instance>> asgInstancesPerAZ = [:]
    instances?.each { instance ->
      asgInstancesPerAZ[instance.availabilityZone] = [instance]
    }
    return asgInstancesPerAZ
  }

    String getCloundEnvironmetName(String tenantName){
        try{
          if (!tenantName) {
            return ""
          }

            if(tenantName.startsWith("PROD_"))
                return "PROD";
            else
                return "TEST"

        }catch (Exception ex){
            log.error("getCloundEnvironmetName: Failed to find Clound Envirnment (TEST/PROD)")
        }
    }
}
