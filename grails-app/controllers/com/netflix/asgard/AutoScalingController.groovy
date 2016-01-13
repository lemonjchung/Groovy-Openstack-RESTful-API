/*
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.asgard

import com.amazonaws.services.autoscaling.model.Activity
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.autoscaling.model.SuspendedProcess
import com.amazonaws.services.ec2.model.AvailabilityZone
import com.netflix.asgard.model.AutoScalingGroupData
import com.netflix.asgard.model.AutoScalingProcessType
import com.netflix.asgard.model.SubnetTarget
import com.netflix.asgard.model.Subnets
import com.netflix.frigga.Names
import com.netflix.grails.contextParam.ContextParam
import com.serve.asgard.AppDatabase
import com.serve.asgard.adaptor.SecurityGroupAdaptor
import com.serve.asgard.enums.ResourceType
import com.serve.asgard.model.LoadBalancer
import com.serve.asgard.model.Network
import grails.converters.JSON
import grails.converters.XML
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.openstack4j.api.OSClient
import org.openstack4j.model.identity.Access
import org.openstack4j.openstack.OSFactory

@ContextParam('region')
class AutoScalingController  {

  def grailsApplication

  def applicationService
  def awsAutoScalingService
  def awsEc2Service
  def awsLoadBalancerService
  def cloudReadyService
  def configService
  def instanceTypeService
  def mergedInstanceService
  def stackService
  private OSClient openStackClient

  static allowedMethods = [save: 'POST', update: 'POST', delete: 'POST', postpone: 'POST', pushStart: 'POST']
  def openStackLoadBalancerService
  def openStackHeatService
  def openStackComputeService
  def AppDatabaseMongoService
  AppDatabase appDatabase

  def openStackAutoScalingService
  def asgardUserContextService
  def networkService
  def autoScalingService
  def computeService
  def heatService

  def openStackRestService
  Caches caches

  static final constASGList = 'ASGList'
  static final constASGListInteger = 300

  def index() {
    redirect(action: 'list', params: params)
  }

  def list() {
//    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
//    // List all stacks
//    List<? extends Stack> stacks = openStackClient.heat().stacks().list();
    List<?> stacksresponse = heatService.getStackList()

    log.debug("Stacks: ${stacksresponse}")

    Map<String, String> stackNamesToAppNames = [:]
    Map<String, String> stackNamesToClusterNames = [:]
    Map<String, List<String>> stackNamesToInstances = [:]
    Map<String, String> minNumberOfInstances = [:]
    Map<String, String> maxNumberOfInstances = [:]
    Map<String, String> desNumberOfInstances = [:]

    List<?> InstanceList = computeService.getInstances()

    appDatabase = appDatabaseMongoService
    stacksresponse?.each { eachstack ->
      stackNamesToAppNames.put(eachstack.stackName, appDatabase.getApplicationID(eachstack.stackName))
      stackNamesToClusterNames.put(eachstack.stackName, appDatabase.getclustername(eachstack.stackName))
      stackNamesToInstances.put(eachstack.stackName, openStackAutoScalingService.GetInstances(eachstack.stackName, InstanceList))
      String stackid = appDatabase.getStackid(eachstack.stackName)

      //Last ASG Group Sub Resource

      if (stackid != "") {
        def asgtemplate = heatService.getTemplate(eachstack.stackName, stackid)
        def ASGResource = asgtemplate?.getResource(ResourceType.OS_HEAT_AUTOSCALINGGROUP)

        if (ASGResource != null) {
            minNumberOfInstances.put(eachstack.stackName, ASGResource?.properties.minSize ?: "")
            maxNumberOfInstances.put(eachstack.stackName, ASGResource?.properties.maxSize ?: "")
            desNumberOfInstances.put(eachstack.stackName, ASGResource?.properties.desiredCapacity ?: "")
        }
      }

	  DateTime createDateTime = new DateTime(eachstack.creationTime,DateTimeZone.UTC).withZone(DateTimeZone.forID( "America/New_York" ));
      eachstack.creationTime = createDateTime ? Time.format(createDateTime) : ''

      if (eachstack.updatedTime != null){
        DateTime updateDateTime = new DateTime(eachstack.updatedTime,DateTimeZone.UTC).withZone(DateTimeZone.forID( "America/New_York" ));
        eachstack.updatedTime = updateDateTime ? Time.format(updateDateTime) : ''
      }
    }

    withFormat {
      html {
        [
                autoScalingGroups          : stacksresponse,
                stackNamesToAppNames       : stackNamesToAppNames,
                stackNamesToClusterNames   : stackNamesToClusterNames,
                stackNamesToInstances      : stackNamesToInstances,
                minNumberOfInstances       : minNumberOfInstances,
                maxNumberOfInstances       : maxNumberOfInstances,
                desNumberOfInstances       : desNumberOfInstances
        ]
      }
      xml { new XML(groups).render(response) }
      json { new JSON(groups).render(response) }
    }
  }


  static final Integer DEFAULT_ACTIVITIES = 20

  def show() {
    Map<String, Object> details = autoScalingService.getAsgDetails(params.id)
    if (details.isEmpty()) {
      Requests.renderNotFound('Auto Scaling Group', params.id, this)
      return
    }
    withFormat {
      html { return details }
      xml { new XML(details).render(response) }
      json { new JSON(details).render(response) }
    }
  }


  def activities() {
    UserContext userContext = UserContext.of(request)
    String groupName = params.id
    Integer count = params.activityCount as Integer
    List<Activity> activities = awsAutoScalingService.getAutoScalingGroupActivities(userContext, groupName, count)
    Map details = [name: groupName, count: activities.size(), activities: activities]
    withFormat {
      html { return details }
      xml { new XML(details).render(response) }
      json { new JSON(details).render(response) }
    }
  }

  def getLatestASGList() {
    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
    appDatabase = appDatabaseMongoService
    Access open4jaccess = asgardUserContextService.currentTenant.openStack4jAccess
    def usercontext = openStackRestService.getUserContext()

    List<ASGList> currentasglist = SaveASGListCache(open4jaccess, appDatabase, openStackAutoScalingService, usercontext)
    Initalcaches(currentasglist)

    redirect(action: 'list')
  }

  def create() {

    UserContext userContext = UserContext.of(request)
    //List<LoadBalancer> loadBalancers = awsLoadBalancerService.getLoadBalancers(userContext).
    //        sort { it.loadBalancerName.toLowerCase() }

    List<SuspendedProcess> processes = []
    if (params.azRebalance == 'disabled') {
      processes << new SuspendedProcess().withProcessName(AutoScalingProcessType.AZRebalance.name())
    }

    List<AvailabilityZone> recommendedZonesOS = openStackComputeService.getAvailabilityZones()
    List<String> selectedZonesOS = []

    Collection<AvailabilityZone> recommendedZones = awsEc2Service.getRecommendedAvailabilityZones(userContext)
    Collection<String> selectedZones = awsEc2Service.preselectedZoneNames(recommendedZones,
      Requests.ensureList(params.selectedZones))

    AutoScalingGroupData group = (
      AutoScalingGroupData.from(new AutoScalingGroup(minSize: tryParse(params.min),
        desiredCapacity: tryParse(params.desiredCapacity),
        maxSize: tryParse(params.max),
        defaultCooldown: tryParse(params.defaultCooldown),
        healthCheckType: params.healthCheckType,
        healthCheckGracePeriod: tryParse(params.healthCheckGracePeriod),
        availabilityZones: selectedZonesOS), [:], [], [:], []))

    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess)

    List<com.serve.asgard.model.SecurityGroup> effectiveGroups = SecurityGroupAdaptor.adaptAWSSecurityGroups(openStackComputeService.getSecurityGroups())


    List<LoadBalancer> loadBalancers = openStackLoadBalancerService.getLoadBalancerPools()?.pools
    if (!loadBalancers.empty) {
      loadBalancers.removeAll {
        it.tenant_id != asgardUserContextService.currentTenant.id
      }
    }
    log.info("ASG loadBalancers1 : ${loadBalancers}")

    //Filter external and internal networks
    List<Network> networks = networkService.getNetworks()
    List<Network> intNetworks = []
    List<Network> extNetworks = []
    networks?.each { network ->
      if (network.name.contains('-ext'))  {
          extNetworks.add(network)
      } else {
        if (network.tenantId.equalsIgnoreCase(asgardUserContextService.currentTenant.id)){
          intNetworks.add(network)
        }
      }
    }

    List<Network> intNetworksSorted = intNetworks.sort { it.name }
    List<Network> extNetworksSorted = extNetworks.sort { it.name }

    Subnets subnets = awsEc2Service.getSubnets(userContext)
    Map<String, String> purposeToVpcId = subnets.mapPurposeToVpcId()
    String subnetPurpose = params.subnetPurpose ?: null
    String vpcId = purposeToVpcId[subnetPurpose]
    Set<String> appsWithClusterOptLevel = []
    [
      applications                : applicationService.getRegisteredApplications(userContext),
      group                       : group,
      stacks                      : stackService.getStacks(userContext),
      allTerminationPolicies      : awsAutoScalingService.terminationPolicyTypes,
      terminationPolicy           : configService.defaultTerminationPolicy,
      //images: awsEc2Service.getAccountImages(userContext).sort { it.imageLocation.toLowerCase() },
      images: openStackComputeService.getOpenStackImages()?.sort { it.name.toLowerCase() },
      defKey                      : awsEc2Service.defaultKeyName,
      //keys: awsEc2Service.getKeys(userContext).sort { it.keyName.toLowerCase() },
      keys: [],
      subnetPurpose               : subnetPurpose,
      subnetPurposes              : subnets.getPurposesForZones(recommendedZonesOS*.name, SubnetTarget.EC2).sort(),
      zonesGroupedByPurpose       : subnets.groupZonesByPurpose(recommendedZonesOS*.name, SubnetTarget.EC2),
      selectedZones               : selectedZonesOS,
      purposeToVpcId              : purposeToVpcId,
      vpcId                       : vpcId,
      loadBalancersGroupedByVpcId : loadBalancers,
      internalnetwork             : intNetworksSorted,
      externalNetwork             : extNetworksSorted,
      selectedLoadBalancers       : Requests.ensureList(params["selectedLoadBalancersForVpcId${vpcId ?: ''}"]),
      securityGroupsGroupedByVpcId: effectiveGroups,
      selectedSecurityGroups      : Requests.ensureList(params.selectedSecurityGroups),
      instanceTypes               : instanceTypeService.getInstanceTypes(userContext),
      iamInstanceProfile          : configService.defaultIamRole,
      spotUrl                     : configService.spotUrl,
      isChaosMonkeyActive         : cloudReadyService.isChaosMonkeyActive(userContext.region),
      appsWithClusterOptLevel     : appsWithClusterOptLevel ?: []
    ]
  }

  private Integer tryParse(String s) {
    s?.isInteger() ? s.toInteger() : null
  }

  def save(GroupCreateCommand cmd) {
    if (cmd.hasErrors()) {
        chain(action: 'create', model: [cmd: cmd], params: params) // Use chain to pass both the errors and params
    } else {
      try {
          ASGCreate newASG = new ASGCreate()

          newASG.groupName = Relationships.buildGroupName(params)
          newASG.description = "${newASG.groupName}--template--${new Date().format("yyyyMMddHHmmss")}".toLowerCase()

          // Auto Scaling Group
          newASG.minSize = (params.min ?: 0) as Integer
          newASG.desiredCapacity = (params.desiredCapacity ?: 0) as Integer
          newASG.maxSize = (params.max ?: 0) as Integer
          newASG.desiredCapacity = Ensure.bounded(newASG.minSize, newASG.desiredCapacity, newASG.maxSize)
          newASG.defaultCooldown = (params.defaultCooldown ?: 10) as Integer

          //Add ASG Alarm parameter
          //// ScaleUp default: CPU threadhold > 60% in 60seconds Create 1 instances
          //// ScaleDown default: CPU threadhold < 20% in 600seconds Terminate 1 instance
          newASG.ScaleUpPeriod = (params.defaultPeriod ?: 60) as Integer
          newASG.ScaleUpThreshold = (params.defaultthreshold ?: 60) as Integer
          newASG.ScalingUpAdjustment = (params.defaultScalingAdjustment ?: 1) as Integer
          newASG.ScaleDownPeriod = (params.defaultPeriod ?: 600) as Integer
          newASG.ScaleDownThreshold = (params.defaultthreshold ?: 20) as Integer
          newASG.ScalingDownAdjustment = (params.defaultScalingAdjustment ?: 1) as Integer

          newASG.loadBalancerIds = Requests.ensureList(params["selectedLoadBalancersForVpcId"]) ?: []
          newASG.netid = params.internalnetwork
        newASG.floatingIpNetwork = params.externalNetwork

          newASG.imageId = params.imageId

          newASG.securityGroups = Requests.ensureList(params.selectedSecurityGroups)
          newASG.instType = params.instanceType
          newASG.cloudEnvironmentName = autoScalingService.getCloundEnvironmetName(asgardUserContextService.currentTenant.name)
          newASG.ASGname = openStackAutoScalingService.GetASGName(newASG.groupName, newASG.groupName, params.appName)
          log.info "Latest ASG name: ${newASG.ASGname}"


          //Create ASG
        autoScalingService.CreateASG(newASG, params.appName)

         redirect(action: 'show', params: [id: newASG.ASGname])
      } catch (Exception e) {
          flash.message = "Could not create AutoScaling Group: ${e}"
          redirect(action: 'list')
      }
    }
  }


  @SuppressWarnings("ReturnsNullInsteadOfEmptyCollection")
  def edit() {
    UserContext userContext = UserContext.of(request)
    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
    String stackName = params.name
    String stackID = params.id

    Map<String, Object> details = autoScalingService.getAsgDetails(params.id)

    if (details.isEmpty()) {
      Requests.renderNotFound('Edit Auto Scaling Group', params.id, this)
      return
    }
    withFormat {
      html { details  }
      xml { new XML(details).render(response) }
      json { new JSON(details).render(response) }
    }
//    return [
//      asgDetail : details
//    ]
  }

  def update() {
    String asgName = params.name
    String asgID = params.id
    UserContext userContext = UserContext.of(request)
    Integer minSize = (params.min ?: 0) as Integer
    Integer desiredCapacity = (params.desiredCapacity ?: 0) as Integer
    Integer maxSize = (params.max ?: 0) as Integer
    desiredCapacity = Ensure.bounded(minSize, desiredCapacity, maxSize)
    Integer coolDown = (params.coolDown ?: 10) as Integer

    try {
      openStackAutoScalingService.updateAutoScalingGroup(asgName, minSize, maxSize, desiredCapacity, coolDown)
      flash.message = "AutoScaling Group '${asgName}' has been updated."
    } catch (Exception e) {
      flash.message = "Could not update AutoScaling Group: ${e}"
      redirect(action: 'edit', params: [id: asgName, groupName: asgName])
    }
    redirect(action: 'show', params: [id: asgName, groupName: asgName])
  }

  def delete() {
    //UserContext userContext = UserContext.of(request)
    String stackName = params.name
    String stackID = params.id
    //openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
    //Stack stack = openStackClient.heat().stacks().getDetails(stackName, stackID)

    Boolean showGroupNext = false
    //if (!stack) {
     //flash.message = "Auto Scaling Group '${stackName}' not found."
    //} else {
      try {
        Map<String, Object> details = autoScalingService.getAsgDetails(params.name)
        openStackAutoScalingService.DeleteAutoScalingGroup(stackName, details?.stack?.id?.toString())
        flash.message = "AutoScaling Group '${stackName}' has been deleted."
      } catch (Exception e) {
        flash.message = "Could not delete Auto Scaling Group: ${e}"
        showGroupNext = true
      }
      /*
           if (group?.instances?.size() <= 0) {
             try {
               awsAutoScalingService.deleteAutoScalingGroup(userContext, name)
               flash.message = "AutoScaling Group '${name}' has been deleted."
             } catch (Exception e) {
               flash.message = "Could not delete Auto Scaling Group: ${e}"
               showGroupNext = true
             }
           } else {
             flash.message = "You cannot delete an auto scaling group that still has instances. " +
               "Set the min and max to 0, wait for the instances to disappear, then try deleting again."
             showGroupNext = true
           }
       */
    //}
    showGroupNext ? redirect(action: 'show', params: [id: stackID, groupName: stackName]) : redirect(action: 'list')
  }

  def postpone() {
    UserContext userContext = UserContext.of(request)
    String name = params.name
    awsAutoScalingService.postponeExpirationTime(userContext, name, Duration.standardDays(1))
    redirect(action: 'show', id: name)
  }

  def generateName() {
    request.withFormat {
      json {
        if (params.appName) {
          try {
            String groupName = Relationships.buildGroupName(params, true)
            List<String> envVars = Relationships.labeledEnvVarsMap(Names.parseName(groupName),
              configService.userDataVarPrefix).collect { k, v -> "${k}=${v}" }
            Map result = [groupName: groupName, envVars: envVars]
            render(result as JSON)
          } catch (Exception e) {
            response.status = 503
            render e.message
          }
        } else {
          response.status = 503
          render '(App is required)'
        }
      }
    }
  }

  def anyInstance() {
    UserContext userContext = UserContext.of(request)
    String name = params.name ?: params.id
    String field = params.field
    if (!name || !field) {
      response.status = 400
      if (!name) {
        render 'name is a required parameter'
      }
      if (!field) {
        render 'field is a required parameter'
      }
      return
    }
    AutoScalingGroup group = awsAutoScalingService.getAutoScalingGroup(userContext, name)
    List<String> instanceIds = group?.instances*.instanceId
    MergedInstance mergedInstance = mergedInstanceService.findHealthyInstance(userContext, instanceIds)
    String result = mergedInstance?.getFieldValue(field)
    if (!result) {
      response.status = 404
      if (!group) {
        result = "No auto scaling group found with name '$name'"
      } else if (!mergedInstance) {
        result = "No instances found for auto scaling group '$name'"
      } else {
        result = "'$field' not found. Valid fields: ${mergedInstance.listFieldNames()}"
      }
    }
    render result
  }

  /**
   * TODO: This endpoint can be removed when ASG tagging is stable and more trusted
   */
  def removeExpirationTime() {
    UserContext userContext = UserContext.of(request)
    String name = params.id ?: params.name
    awsAutoScalingService.removeExpirationTime(userContext, name)
    flash.message = "Removed expiration time from auto scaling group '${name}'"
    redirect(action: 'show', id: name)
  }

  def imageless() {
    UserContext userContext = UserContext.of(request)
    List<AppRegistration> allApps = applicationService.getRegisteredApplications(userContext)
    Collection<AutoScalingGroup> allGroups = awsAutoScalingService.getAutoScalingGroups(userContext)
    Collection<AutoScalingGroup> groupsWithMissingImages = allGroups.findAll { AutoScalingGroup group ->
      String lcName = group.launchConfigurationName
      LaunchConfiguration lc = awsAutoScalingService.getLaunchConfiguration(userContext, lcName, From.CACHE)
      awsEc2Service.getImage(userContext, lc?.imageId, From.CACHE) == null
    }
    groupsWithMissingImages.sort { it.autoScalingGroupName }

    Map<String, AppRegistration> groupNamesToApps = [:]
    groupsWithMissingImages*.autoScalingGroupName.each { String groupName ->
      groupNamesToApps.put(groupName, allApps.find { it.name == Relationships.appNameFromGroupName(groupName) })
    }

    withFormat {
      html {
        [
          autoScalingGroups: groupsWithMissingImages,
          groupNamesToApps : groupNamesToApps
        ]
      }
      xml { new XML(groupsWithMissingImages).render(response) }
      json { new JSON(groupsWithMissingImages).render(response) }
    }
  }

//    def save_AWS(GroupCreateCommand cmd) {
//        if (cmd.hasErrors()) {
//            chain(action: 'create', model: [cmd: cmd], params: params) // Use chain to pass both the errors and params
//        } else {
//            // TODO - Sachin - Fix the logic for Auto Scaling Group name
//            String groupName = Relationships.buildGroupName(params)
//            String launchConfigurationName = Relationships.buildLaunchConfigurationName(groupName).toLowerCase()
//
//            Template template = new Template();
//            //TODO - Sachin - Change this logic
//            template.description = "${groupName}--template--${new Date().format("yyyyMMddHHmmss")}".toLowerCase()
//
//            UserContext userContext = UserContext.of(request)
//            Representer representer = new NonMetaClassRepresenter()
//
//
//            // Subnets subnets = awsEc2Service.getSubnets(userContext)
//            // String subnetPurpose = params.subnetPurpose ?: null
//            // String vpcId = subnets.getVpcIdForSubnetPurpose(subnetPurpose) ?: ''
//
//            // Auto Scaling Group
//            Integer minSize = (params.min ?: 0) as Integer
//            Integer desiredCapacity = (params.desiredCapacity ?: 0) as Integer
//            Integer maxSize = (params.max ?: 0) as Integer
//            desiredCapacity = Ensure.bounded(minSize, desiredCapacity, maxSize)
//            Integer defaultCooldown = (params.defaultCooldown ?: 10) as Integer
//            List<String> availabilityZones = Requests.ensureList(params.selectedZones)
//
//            //Add ASG Alarm parameter
//            //// ScaleUp default: CPU threadhold > 40% in 60seconds Create 2 instances
//            //// ScaleDown default: CPU threadhold < 20% in 600seconds Terminate 1 instance
//            Integer ScaleUpPeriod = (params.defaultPeriod ?: 60) as Integer
//            Integer ScaleUpThreshold = (params.defaultthreshold ?: 40) as Integer
//            Integer ScalingUpAdjustment = (params.defaultScalingAdjustment ?: 2) as Integer
//            Integer ScaleDownPeriod = (params.defaultPeriod ?: 600) as Integer
//            Integer ScaleDownThreshold = (params.defaultthreshold ?: 20) as Integer
//            Integer ScalingDownAdjustment = (params.defaultScalingAdjustment ?: 1) as Integer
//            //Add ASG Alarm parameter
//
//            //String healthCheckType = AutoScalingGroupHealthCheckType.ensureValidType(params.healthCheckType)
//            Integer healthCheckGracePeriod = params.healthCheckGracePeriod as Integer
//            List<String> terminationPolicies = Requests.ensureList(params.terminationPolicy)
//            List<String> loadBalancerIds = Requests.ensureList(params["selectedLoadBalancersForVpcId"]) ?: []
//
//            String imageId = params.imageId
//            //String keyName = params.keyName
//            List<String> securityGroups = Requests.ensureList(params.selectedSecurityGroups)
//            String instType = params.instanceType
//            String kernelId = params.kernelId ?: null
//            String ramdiskId = params.ramdiskId ?: null
//            String iamInstanceProfile = params.iamInstanceProfile ?: configService.defaultIamRole
//            boolean ebsOptimized = params.ebsOptimized?.toBoolean()
//            boolean enableMonitoring = params.enableInstanceMonitoring ? params.enableInstanceMonitoring.toBoolean() :
//                    configService.enableInstanceMonitoring
//            log.debug "loadBalancerIds: ${loadBalancerIds}"
//            List<ResourceReference> loadBalancerRefList = [] //new ResourceReference(loadBalancerPoolId)
//
//            loadBalancerIds.each { loadBalancerPoolId ->
//                log.info("LoadBalancer Pool Id: ${loadBalancerPoolId}")
//                loadBalancerRefList.add(new ResourceReference(loadBalancerPoolId))
//                OSNeutronLoadBalancerResource osNeutronLoadBalancerResource = new OSNeutronLoadBalancerResource()
//                representer.addClassTag(com.serve.asgard.model.hot.sections.resource.OSNeutronLoadBalancerResource, Tag.MAP)
//                osNeutronLoadBalancerResource.properties.poolId = loadBalancerPoolId
//                //TODO: Sachin - Remove this hardcoded value
//                osNeutronLoadBalancerResource.properties.protocolPort = 443
//                template.resources.put(loadBalancerPoolId, osNeutronLoadBalancerResource)
//            }
//
//            OSNovaServerNetwork oSNovaServerNetwork = new OSNovaServerNetwork()
//            //TODO: Sachin - Remove this hardcoded value - Add Network selection drop down
//            oSNovaServerNetwork.network = 'private'
//
//            AWSAutoScalingAutoScalingGroupResource awsAutoScalingGroupResource = new AWSAutoScalingAutoScalingGroupResource()
//            representer.addClassTag(AWSAutoScalingAutoScalingGroupResource, Tag.MAP)
//            //TODO: Sachin - Remove this hardcoded value - Add Availability Zones drop down
//            awsAutoScalingGroupResource.properties.availabilityZones = availabilityZones ?: ['nova']
//            awsAutoScalingGroupResource.properties.loadBalancerNames = loadBalancerRefList
//            awsAutoScalingGroupResource.properties.launchConfigurationName = new ResourceReference(launchConfigurationName)
//            awsAutoScalingGroupResource.properties.cooldown = defaultCooldown
//            awsAutoScalingGroupResource.properties.minSize = minSize
//            awsAutoScalingGroupResource.properties.maxSize = maxSize
//            awsAutoScalingGroupResource.properties.desiredCapacity = desiredCapacity ?: 0
//
//            // Setup subnet_id from selected load balancers, load balancers could be multiple //
//            loadBalancerIds.each { loadBalancerPoolId ->
//                LoadBalancer loadBalancer = openStackLoadBalancerService.getLoadBalancer(loadBalancerPoolId)
//                awsAutoScalingGroupResource.properties.vpcZoneIdentifier.add(loadBalancer.subnet_id);
//            };
//
//            //Change public subnet id(net04_subnet) by Joanne
//
//            //Add ASG Tags for Alarm by Joanne 4/1/2015
//            def OSStackid = "OS::stack_id"
//            List<AWSAutoScalingGroupTags> asgtaglist = []
//            AWSAutoScalingGroupTags asgtag = new AWSAutoScalingGroupTags()
//            asgtag.key = "metering.stack"
//            asgtag.value = new ResourceReference(OSStackid)
//            asgtaglist.add(asgtag)
//            awsAutoScalingGroupResource.properties.tags = asgtaglist
//            //Add ASG Tags for Alarm by Joanne 4/1/2015
//
//            AWSAutoScalingLaunchConfigurationResource autoScalingLaunchConfigurationResource = new AWSAutoScalingLaunchConfigurationResource()
//            representer.addClassTag(com.serve.asgard.model.hot.sections.resource.AWSAutoScalingLaunchConfigurationResource, Tag.MAP)
//            if (securityGroups) {
//                autoScalingLaunchConfigurationResource.properties.securityGroups = securityGroups
//            }
//            autoScalingLaunchConfigurationResource.properties.imageId = imageId
//            autoScalingLaunchConfigurationResource.properties.instanceType = instType
//            //autoScalingLaunchConfigurationResource.properties.keyName = keyName
//
//            def latestasgname = openStackAutoScalingService.GetASGName(groupName, groupName, params.appName)
//            log.info "Latest ASG name: ${latestasgname}"
//
//            template.resources.put(latestasgname, awsAutoScalingGroupResource)
//            template.resources.put(launchConfigurationName, autoScalingLaunchConfigurationResource)
//
//            /// Added Ceilometer Alarm by Joanne 03/05/2015
//            String scalingupPolicyname = "WebServerScaleUpPolicy"
//            AWSAutoScalingScalingPolicyResource ScalingUPPolicyResource = new AWSAutoScalingScalingPolicyResource()
//            representer.addClassTag(com.serve.asgard.model.hot.sections.resource.AWSAutoScalingScalingPolicyResource, Tag.MAP)
//            ScalingUPPolicyResource.properties.adjustmenttype = "ChangeInCapacity"
//            ScalingUPPolicyResource.properties.autoscalinggroupname = new ResourceReference(latestasgname)
//            ScalingUPPolicyResource.properties.cooldown = 30
//            ScalingUPPolicyResource.properties.scalinadjustment = ScalingUpAdjustment        //1
//            template.resources.put(scalingupPolicyname, ScalingUPPolicyResource)
//            log.info ("ScalingUPPolicyResourceScalingUPPolicyResource ${ScalingUPPolicyResource}")
//
//            String CeilometerAlarmHighname = "IncomingAlarmHigh"
//            List<ResourceReference> scalingupRefList = []
//            scalingupRefList.add(new ResourceReference(scalingupPolicyname))
//
//            OSCeilometerAlarmResource CeilometerAlarmHigh = new OSCeilometerAlarmResource()
//            representer.addClassTag(com.serve.asgard.model.hot.sections.resource.OSCeilometerAlarmResource, Tag.MAP)
//            CeilometerAlarmHigh.properties.meter_name = "cpu_util"
//            CeilometerAlarmHigh.properties.description = "Scale-up if the average CPU > 40% for 1 minute"
//            CeilometerAlarmHigh.properties.statistic = "avg"
//            CeilometerAlarmHigh.properties.period = ScaleUpPeriod         //60
//            CeilometerAlarmHigh.properties.evaluation_periods = 1
//            CeilometerAlarmHigh.properties.threshold =ScaleUpThreshold    //40
//            CeilometerAlarmHigh.properties.alarm_actions = scalingupRefList
//            CeilometerAlarmHigh.properties.matching_metadata = ["metadata.user_metadata.stack": new ResourceReference(OSStackid)]
//            CeilometerAlarmHigh.properties.comparison_operator = "gt"
//            template.resources.put(CeilometerAlarmHighname, CeilometerAlarmHigh)
//
//            log.info("CeilometerAlarmHigh ${CeilometerAlarmHigh}")
//            /// Added Ceilometer Alarm by Joanne 03/05/2015
//
//            /// Added Scale Down with Alarm by Joanne 04/08/2015
//            String scalingDownPolicyname = "WebServerScaleDownPolicy"
//            AWSAutoScalingScalingPolicyResource ScalingDownPolicyResource = new AWSAutoScalingScalingPolicyResource()
//            //representer.addClassTag(com.serve.asgard.model.hot.sections.resource.AWSAutoScalingScalingPolicyResource, Tag.MAP)
//            ScalingDownPolicyResource.properties.adjustmenttype = "ChangeInCapacity"
//            ScalingDownPolicyResource.properties.autoscalinggroupname = new ResourceReference(latestasgname)
//            ScalingDownPolicyResource.properties.cooldown = 30
//            ScalingDownPolicyResource.properties.scalinadjustment = -ScalingDownAdjustment        //-1
//            template.resources.put(scalingDownPolicyname, ScalingDownPolicyResource)
//            log.info ("ScalingDownPolicyResource ${ScalingDownPolicyResource}")
//
//            String CeilometerAlarmLowName = "IncomingAlarmLow"
//            List<ResourceReference> scalingDownRefList = []
//            scalingDownRefList.add(new ResourceReference(scalingDownPolicyname))
//            OSCeilometerAlarmResource CeilometerAlarmLow = new OSCeilometerAlarmResource()
//            //representer.addClassTag(com.serve.asgard.model.hot.sections.resource.OSCeilometerAlarmResource, Tag.MAP)
//            CeilometerAlarmLow.properties.meter_name = "cpu_util"
//            CeilometerAlarmLow.properties.description = "Scale-up if the average CPU < 20% for 10 minute"
//            CeilometerAlarmLow.properties.statistic = "avg"
//            CeilometerAlarmLow.properties.period = ScaleDownPeriod         //600
//            CeilometerAlarmLow.properties.evaluation_periods = 1
//            CeilometerAlarmLow.properties.threshold =ScaleDownThreshold    //20
//            CeilometerAlarmLow.properties.alarm_actions = scalingDownRefList
//            CeilometerAlarmLow.properties.matching_metadata = ["metadata.user_metadata.stack": new ResourceReference(OSStackid)]
//            CeilometerAlarmLow.properties.comparison_operator = "lt"
//            template.resources.put(CeilometerAlarmLowName, CeilometerAlarmLow)
//            log.info("CeilometerAlarmLow ${CeilometerAlarmLow}")
//            /// Added Scale Down with Alarm by Joanne 04/08/2015
//
//            // TODO: Sachin - Below code is a better way to handle the stack creation, but, getting error "Missing required credential: X-Auth-User". Fix: Upgrade the version of Openstack. till then have a workaround to bypass openstack4j and send request directly via rest client
//            //openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess)
//            //validateAndExecuteTemplate(openStackClient, yamlTemplate)
//
//            //TODO: Sachin - Workaround for the above issue
//            def response = openStackHeatService.createStack(template, latestasgname)
//            log.info "Stack Created. ID: ${response?.stack?.id}"
//
//            //// Need to change multiple poolid : after confirm by Joanne
//            String lbpoolid = ""
//            if (loadBalancerIds.size() > 0 ) {
//                lbpoolid = loadBalancerIds.get(0)
//            }
//            //// Need to change multiple poolid : after confirm by Joanne
//
//            //def jsontemplate = openStackHeatService.buildTemplateRequest(template, latestasgname)
//            openStackAutoScalingService.SavetoMongodb(groupName, latestasgname, params.appName, response?.stack?.id, response?.stack?.stackName, lbpoolid, null)
//
//            redirect(action: 'show', params: [id: latestasgname])
//        }
//    }

}
