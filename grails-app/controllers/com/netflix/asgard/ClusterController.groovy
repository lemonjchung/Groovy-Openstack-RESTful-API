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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.google.common.annotations.VisibleForTesting
import com.serve.asgard.Cluster
import com.netflix.asgard.model.AutoScalingGroupData
import com.netflix.asgard.model.InstancePriceType
import com.netflix.asgard.push.ClusterData
import com.netflix.asgard.push.InitialTraffic
import com.netflix.grails.contextParam.ContextParam
import com.serve.asgard.AppDatabase
import com.serve.asgard.adaptor.KeyPairAdaptor
import com.serve.asgard.adaptor.SecurityGroupAdaptor
import com.serve.asgard.enums.ResourceType
import com.serve.asgard.exception.RestClientRequestException
import com.serve.asgard.model.HeatStack
import com.serve.asgard.model.LoadBalancer
import com.serve.asgard.model.LoadBalancerPools
import com.serve.asgard.model.Network
import com.serve.asgard.model.hot.sections.resource.OSNeutronLoadBalancerResource
import grails.converters.JSON
import grails.converters.XML
import groovy.transform.PackageScope
import org.openstack4j.api.OSClient
import org.openstack4j.model.heat.Stack
import org.openstack4j.openstack.OSFactory

@ContextParam('region')
class ClusterController {

    static allowedMethods = [createNextGroup: 'POST', resize: 'POST', delete: 'POST', activate: 'POST',
                             deactivate     : 'POST']

    def grailsApplication
    def applicationService
    def awsAutoScalingService
    def awsEc2Service
    def awsLoadBalancerService
    def configService
    def mergedInstanceService
    def pushService
    def spotInstanceRequestService
    def taskService
    def AppDatabaseMongoService
    AppDatabase appDatabase;

    def openStackNetworkService
    def openStackLoadBalancerService
    def openStackAutoScalingService
    def openStackHeatService
    def openStackComputeService
    def openStackRestService
    def instanceTypeService
    def clusterService
  def networkService

    public static List<AutoScalingGroupMongodb> asgnamelist = []
    public static staticclustername = ''
    def openStackLoginService
    def autoScalingService
    def heatService
    def asgardUserContextService
    def graphiteService

    def index() {
         redirect(action: 'list', params: params)
    }

    def list() {
        // 1. get clustername and asgname from mongodb
        // 2. get ASG group from openstack
        // 3. Convert to Cluster
        //initial clustername and asgnamelist
        staticclustername = ''
        asgnamelist = []

        appDatabase = appDatabaseMongoService
        List<ClusterMongodb> ak = appDatabase.getCluster()
      Collection<ClusterData> clusterObjects = []
        ak?.each { ClusterMongodb o ->
            List<AutoScalingGroupData> asglist = []
            log.debug("o.asglist ${o.asglist}")
            o.asglist?.each { eachasg ->
                AutoScalingGroupData a = (
                        AutoScalingGroupData.from(new AutoScalingGroup(autoScalingGroupName: eachasg.asgname,
                                createdTime: new Date(), instances: []), null, null, null, []))
                asglist.add(a)
            }
          ClusterData each1 = new ClusterData(asglist)
            clusterObjects.add(each1)
        }
        withFormat {
            html { [clusters: clusterObjects, appNames: ""] }
            xml { new XML(clusterObjects).render(response) }
            json { new JSON(clusterObjects).render(response) }
        }
    }

    def show() {
        UserContext userContext = UserContext.of(request)
        String name = params.id ?: params.name
      ClusterData cluster = clusterService.getCluster(name)

        if (!cluster) {
            ////If no ASG, go to List page
            Requests.renderNotFound('Cluster', name, this)
            //redirect(action: 'list', params: params)
        } else if (name == cluster.name) {
            try {
            withFormat {
                html {
                    AutoScalingGroupData lastGroup = cluster.last()

                    String stackId = clusterService.getAutoScalingGroupByName(lastGroup.autoScalingGroupName)?.stackid

                    //lastGroup.template = openStackHeatService.getTemplate(stackId)
                  lastGroup.template = heatService.getTemplate(lastGroup.autoScalingGroupName, stackId)

                    String nextGroupName = Relationships.buildNextAutoScalingGroupName(lastGroup.autoScalingGroupName)
                    int clusterMaxGroups = configService.clusterMaxGroups
                    Boolean okayToCreateGroup = cluster.size() < clusterMaxGroups
                    String recommendedNextStep = cluster.size() >= clusterMaxGroups ?
                            'Delete an old group before pushing to a new group.' :
                            cluster.size() <= 1 ? 'Create a new group and switch traffic to it' :
                                    'Switch traffic to the preferred group, then delete legacy group'

                    //List<String> selectedLoadBalancers = lastGroup.loadBalancerNames ?: selectedLoadBalancers
                    LoadBalancerPools loadBalancerPools = openStackLoadBalancerService.getLoadBalancerPools()
                    List<LoadBalancer> loadBalancers = loadBalancerPools?.pools?.sort { it.loadBalancerName.toLowerCase() }

                    OSNeutronLoadBalancerResource loadBalancerResource = lastGroup?.template?.getResource(ResourceType.OS_NEUTRON_LOADBALANCER)
                    String selectedLoadBalancerId
                    if (loadBalancerResource) {
                      selectedLoadBalancerId = loadBalancerResource?.properties?.poolId
                    }
                  LoadBalancer selectedLoadBalancer
                  List<LoadBalancer> selectedLoadBalancers = []
                  try {
                    selectedLoadBalancer = openStackLoadBalancerService.getLoadBalancer(selectedLoadBalancerId)
                    selectedLoadBalancers = [selectedLoadBalancer]
                  } catch (RestClientRequestException ex) {
                    //flash.message = "Load Balancer (${selectedLoadBalancerId}) associated with ASG (${lastGroup.autoScalingGroupName} does not exist in openstack"
                    //ignore this error for now
                  }

                    Collection<String> selectedLoadBalancersValue = [] //lastGroup?.template?.getLoadBalancer()?.properties.poolId
                    selectedLoadBalancers.each { LoadBalancer eachloadbalancer ->
                      selectedLoadBalancersValue.add(eachloadbalancer.id)
                    }

                    //Last ASG Group Sub Resource
                    def lasgGroupResource = lastGroup?.template?.getResource(ResourceType.OS_HEAT_AUTOSCALINGGROUP)
                    def lastGroupOSSubResource = lasgGroupResource?.properties?.resource
                    Collection<String> selectedSecurityGroups = lastGroupOSSubResource?.properties.secGroups
                    log.debug("====== ${lastGroup?.template?.getResource(ResourceType.OS_HEAT_AUTOSCALINGGROUP)}")
                    log.debug("====== ${lastGroupOSSubResource?.properties.image}")

                  //Filter external and internal networks
                  List<Network> networks = networkService.getNetworks()
                   List<Network> intNetworks = []
                    List<Network> extNetworks = []
                  networks?.each { network ->
                    if (network.name.contains('-ext')) {
                      extNetworks.add(network)
                    } else {
					if (network.tenantId.equalsIgnoreCase(asgardUserContextService.currentTenant.id)) {
                      intNetworks.add(network)
                    }
                  }
				  }

				  List<Network> intNetworksSorted = intNetworks.sort { it.name }
                  List<Network> extNetworksSorted = extNetworks.sort { it.name }

                  Map<String, Object> attributes = [
                            appName                     : Relationships.appNameFromGroupName(lastGroup.autoScalingGroupName),
                            name                        : lastGroup.autoScalingGroupName,
                            clusterName                 : Relationships.clusterFromGroupName(lastGroup.autoScalingGroupName),
                            variables                   : Relationships.parts(lastGroup.autoScalingGroupName),
                            allTerminationPolicies      : "", //awsAutoScalingService.terminationPolicyTypes,
                            terminationPolicy           : "",
                            images                      : openStackComputeService.getOpenStackImages()?.sort { it.name.toLowerCase() },
                            imageId                     : lastGroupOSSubResource?.properties.image.toString(),
                            instanceTypes               : instanceTypeService.getInstanceTypes(userContext),
                            instanceType                : lastGroupOSSubResource?.properties.flavor.toString(),
                            internalnetwork             : intNetworksSorted,
                            externalNetwork             : extNetworksSorted,
                            selectedinternalnetwork     : lastGroupOSSubResource?.properties.in_net_id.toString(),
                            selectedExternalNetwork     : lastGroupOSSubResource?.properties.ext_net_id.toString(),
                            cluster                     : cluster,
                            runningTasks                : null, //runningTasks,
                            group                       : null, //lastGroup,
                            minSize                     : lasgGroupResource?.properties?.minSize,
                            maxSize                     : lasgGroupResource?.properties?.maxSize,
                            desiredCapacity             : lasgGroupResource?.properties?.desiredCapacity,
                            nextGroupName               : nextGroupName,
                            okayToCreateGroup           : okayToCreateGroup,
                            recommendedNextStep         : recommendedNextStep,
                            buildServer                 : configService.buildServerUrl,
                            vpcZoneIdentifier           : lastGroup.vpcZoneIdentifier,
                            zonesGroupedByPurpose       : null, //zonesByPurpose,
                            selectedZones               : null,
                            subnets                     : openStackNetworkService.getSubnets(),
                            subnetPurposes              : null, //subnetPurposes,
                            subnetPurpose               : null, //subnetPurpose ?: null,
                            loadBalancers               : selectedLoadBalancers, // This is for Load balacer icon
                            loadBalancersGroupedByVpcId : loadBalancers,
                            selectedLoadBalancers       : selectedLoadBalancersValue,
                            spotUrl                     : null, //configService.spotUrl,
                            pricing                     : null, //params.pricing ?: attributes.pricing,
                            keys                        : KeyPairAdaptor.adapt(openStackComputeService.getKeypairs()),
                            keyName                     : "", //lastGroup?.template?.getLaunchConfiguration().properties.keyName,
                            securityGroupsGroupedByVpcId: SecurityGroupAdaptor.adaptAWSSecurityGroups(openStackComputeService.getSecurityGroups()),
                            selectedSecurityGroups      : selectedSecurityGroups,
                            zoneAvailabilities          : openStackComputeService.getAvailabilityZones()
                    ]
                  attributes

                }
                xml { new XML(cluster).render(response) }
                json { new JSON(cluster).render(response) }

        }
            } catch (Exception ex) {
              flash.message = "getCluster Error: ${ex}"
                redirect(action: 'list', params: params)
            }
    } else {
      params['id'] = cluster.name
      redirect(action: 'show', params: params)
  }
}

def showLastGroup() {
UserContext userContext = UserContext.of(request)
String name = params.id ?: params.name
  ClusterData cluster = awsAutoScalingService.getCluster(userContext, name)
if (!cluster) {
Requests.renderNotFound('Cluster', name, this)
} else {
redirect([controller: 'autoScaling', action: 'show', params: [id: cluster.last().autoScalingGroupName]])
}
}

def result() {
render view: '/common/result'
}

@SuppressWarnings("GroovyAssignabilityCheck")
def createNextGroup() {
    try {
    UserContext userContext = UserContext.of(request)
    String name = params.name
    String appName = Relationships.appNameFromGroupName(name)
      ClusterData cluster = clusterService.getCluster(name) //awsAutoScalingService.getCluster(userContext, name)

    if (!cluster) {
    flash.message1 = "No auto scaling groups exist with cluster name ${name}"
    redirect(action: 'result')
    return
    }

    Boolean okayToCreateGroup = cluster.size() < configService.clusterMaxGroups
    if (okayToCreateGroup) {
    AutoScalingGroupData lastGroup = cluster.last()

    final String nextGroupName = Relationships.buildNextAutoScalingGroupName(lastGroup.autoScalingGroupName)

    InitialTraffic initialTraffic = params.trafficAllowed ? InitialTraffic.ALLOWED : InitialTraffic.PREVENTED

    //          Template lastGroupTemplate = lastGroup.template
    //          AWSAutoScalingLaunchConfigurationResource lastGroupLC = lastGroupTemplate.getLaunchConfiguration()
    //          AWSAutoScalingAutoScalingGroupResource lastGroupASG = lastGroupTemplate.getASG()
    //          OSNeutronLoadBalancerResource lastGroupLB = lastGroupTemplate.getLoadBalancer()

    ASGCreate newASG = new ASGCreate()

    newASG.groupName = name
    newASG.ASGname = nextGroupName
    newASG.description = "${nextGroupName}--template--${new Date().format("yyyyMMddHHmmss")}".toLowerCase()

    // Auto Scaling Group
    newASG.minSize = (params.min ?: 1) as Integer
    newASG.desiredCapacity = (params.desiredCapacity ?: 1) as Integer
    newASG.maxSize = (params.max ?: 3) as Integer
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

    newASG.imageId = params.imageId

    newASG.securityGroups = Requests.ensureList(params.selectedSecurityGroups)

    newASG.instType = params.instanceType

    newASG.netid = params.internalnetwork

      newASG.floatingIpNetwork = params.externalNetwork

    newASG.cloudEnvironmentName = autoScalingService.getCloundEnvironmetName(asgardUserContextService.currentTenant.name)

    //Create ASG
    autoScalingService.CreateASG(newASG, nextGroupName)
    redirect(uri: request.getHeader('referer'))

    }
    }catch (Exception ex) {
        log.error("Failed to create Auto Scaling Group Error: ${ex}")
        flash.message = "Could not create AutoScaling Group: ${ex}"
        redirect(action: 'result')
    }
}

  @VisibleForTesting
  @PackageScope
  int convertToIntOrUseDefault(String value, Integer defaultValue) {
    value?.isInteger() ? value.toInteger() : defaultValue
  }

  private boolean shouldAzRebalanceBeSuspended(String azRebalance, boolean lastRebalanceSuspended) {
    (azRebalance == null) ? lastRebalanceSuspended : (azRebalance == 'disabled')
  }

  private String determineSpotPrice(LaunchConfiguration lastLaunchConfig, UserContext userContext,
                                    String instanceType) {
    String spotPrice = null
    if (!params.pricing) {
      spotPrice = lastLaunchConfig.spotPrice
    } else if (params.pricing == InstancePriceType.SPOT.name()) {
      spotPrice = spotInstanceRequestService.recommendSpotPrice(userContext, instanceType)
    }
    spotPrice
  }

  def checkResizeStatus() {
    UserContext userContext = UserContext.of(request)
    Integer newMin = (params.minSize ?: params.minAndMaxSize) as Integer
    Integer newMax = (params.maxSize ?: params.minAndMaxSize) as Integer
    String asgName = params.name

    Boolean resizeDone = false
    List<InstanceMongodb> instances

    try{
      int resCount = openStackAutoScalingService.ResizeAutoScalingGroupInstance(asgName, newMin, newMax)

      while(!resizeDone)
      {
         def asgObject = openStackAutoScalingService.getAutoScalingGroup(asgName)
        instances = openStackAutoScalingService.GetInstanceswithASG_OS(asgName, asgObject.stack.stackId)
        HeatStack stack = heatService.getStackDetails(asgName, asgObject.stack.stackId)
        //If the asg status is fail then come out of the loop
        if (stack.getStackStatus().equalsIgnoreCase("FAIL"))
        {
          resizeDone = true
          flash.message1 = "$asgName resize failed. ${stack.getStackStatusReason()}"
        } else {
          if (instances.findAll{it.instancestatus == 'ACTIVE'}.size() == (newMax * resCount))
          {
            resizeDone = true
          }
        }
      }
    }catch (Exception ex) {
      log.error("checkResizeStatus Error: ${ex}")
    }
    withFormat {
      json { new JSON(instances).render(response) }
    }
  }

  def popupwindows() {
    List<com.netflix.asgard.ClusterStatus> clusterStatusList = []
    asgnamelist?.each { AutoScalingGroupMongodb eachasg ->
      ClusterStatus asgstatus = new ClusterStatus()
      asgstatus.clustername = staticclustername
      asgstatus.asgname = eachasg.asgname
      //String asgstatusname = com.serve.asgard.openstack.OpenStackHeatService.ASGStatus(eachasg.stackid.toString(), eachasg.asgname.toString())
      //Temp for testing
      String asgstatusname = "Created"
      asgstatus.status = asgstatusname
      clusterStatusList.add(asgstatus)
    }

    OSClient openStackClient = OSFactory.clientFromAccess(openStackLoginService.access);
    List<? extends Stack> statlist = openStackClient.heat().stacks().list();
    List<Stack> clusterList = []
    appDatabase = appDatabaseMongoService
    statlist.each { Stack eachstack ->
      asgnamelist?.each { AutoScalingGroupMongodb eachasg ->
        if(eachasg.stackid == eachstack.id) {
          Stack onestack = eachstack
          clusterList.add(onestack)
        }
      }
    }
    render(template:"statuspopup", model:[clusters:clusterList, clustername:staticclustername])
    //render(template:"statuspopup", model:[clusterstatus:clusterStatusList])
  }

  def delete() {
    UserContext userContext = UserContext.of(request)
    def name = params.name
    String stackid = openStackAutoScalingService.GetAutoScalingGroupStackid(name)
    def checkasg = heatService.getStackDetails(name, stackid)   //openStackAutoScalingService.CheckAutoScalingGroup(name, stackid)

    if (checkasg != null) {
        try {
            openStackAutoScalingService.DeleteAutoScalingGroup(name, stackid)
        } catch (Exception ex) {
             flash.message1 = "Could not delete AutoScaling Group now. Please try again later."
        }
        redirect(uri: request.getHeader('referer'))
    } else {
      Requests.renderNotFound('Auto scaling group', name, this)
    }

//    AutoScalingGroup group = openStackAutoScalingService.getAutoScalingGroup(userContext, name)
//    if (group) {
//     // GroupDeleteOperation operation = pushService.startGroupDelete(userContext, group)
//     //redirectToTask(operation.taskId)
//    } else {
//      Requests.renderNotFound('Auto scaling group', name, this)
//    }
  }

  def activate() {
    try {
    UserContext userContext = UserContext.of(request)
    List<InstanceMongodb> asginstancelist = []
    def asgObject = openStackAutoScalingService.getAutoScalingGroup(params.name)
    asginstancelist = openStackAutoScalingService.GetInstanceswithASG_OS(params.name, asgObject.stack.stackId)
    openStackAutoScalingService.DeactiveOrActiveInstaces(asginstancelist, true, params.name)
      graphiteService.incrementEnableASG()
    } catch (Exception e) {
        flash.message1 = "Could not enable AutoScaling Group as it is in inconsistent state now. Please try again later."
    }
    redirect(uri: request.getHeader('referer'))
  }

  def deactivate() {
    try {
    UserContext userContext = UserContext.of(request)
    List<InstanceMongodb> asginstancelist = []
    def asgObject = openStackAutoScalingService.getAutoScalingGroup(params.name)
    asginstancelist = openStackAutoScalingService.GetInstanceswithASG_OS(params.name, asgObject.stack.stackId)
    openStackAutoScalingService.DeactiveOrActiveInstaces(asginstancelist, false, params.name)
      graphiteService.incrementDisableASG()
    } catch (Exception e) {
      flash.message1 = "Could not disable AutoScaling Group as it is in inconsistent state now. Please try again later."
    }
    redirect(uri: request.getHeader('referer'))
  }

  def anyInstance() {
    UserContext userContext = UserContext.of(request)
    String name = params.id
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
    ClusterData cluster = awsAutoScalingService.getCluster(userContext, name)
    List<String> instanceIds = cluster?.instances*.instanceId
    MergedInstance mergedInstance = mergedInstanceService.findHealthyInstance(userContext, instanceIds)
    String result = mergedInstance?.getFieldValue(field)
    if (!result) {
      response.status = 404
      if (!cluster) {
        result = "No cluster found with name '$name'"
      } else if (!mergedInstance) {
        result = "No instances found for cluster '$name'"
      } else {
        result = "'$field' not found. Valid fields: ${mergedInstance.listFieldNames()}"
      }
    }
    render result
  }

  private void redirectToTask(String taskId) {
    redirect(controller: 'task', action: 'show', params: [id: taskId])
  }
}
