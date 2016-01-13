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

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription

//import com.amazonaws.services.ec2.model.Instance
import com.netflix.asgard.model.ApplicationInstance
import com.netflix.grails.contextParam.ContextParam
import com.serve.asgard.model.Instance
import com.serve.asgard.model.SecurityGroup
import grails.converters.JSON
import grails.converters.XML
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.openstack4j.api.OSClient
import org.openstack4j.openstack.OSFactory

@ContextParam('region')
class InstanceController {

  final static allowedMethods = ['terminate', 'terminateAndShrinkGroup', 'reboot', 'deregister', 'register',
                                 'associateDo', 'takeOutOfService', 'putInService', 'addTag', 'removeTag'].collectEntries {
    [(it): 'POST']
  }

  static editActions = ['associate']

  def awsAutoScalingService
  def awsEc2Service
  def awsLoadBalancerService
  def configService
  def discoveryService
  def mergedInstanceGroupingService
  def openStackComputeService

  private OSClient openStackClient;
  def asgardUserContextService

  def ComputeService
  def NetworkService
  def OpenStackAutoScalingService
  def OpenStackInstanceService
  def GlanceService

  /**
   * The special marker for looking for items that do not have an application.
   */
  static final String NO_APP_ID = '_noapp'

  def index() {
    redirect(action: 'list', params: params)
  }

  def apps() {
    UserContext userContext = UserContext.of(request)
    List<MergedInstance> allInstances = mergedInstanceGroupingService.getMergedInstances(userContext)
    List<String> appNames = allInstances.findResults { it.appName?.toLowerCase() ?: null } as List<String>
    Map result = [appNames: appNames.unique().sort(), noAppId: NO_APP_ID]
    withFormat {
      html { result }
      xml { new XML(result).render(response) }
      json { new JSON(result).render(response) }
    }
  }

  def list() {
      //  List<? extends Server> servers = openStackComputeService.getInstances()
      List<?> servers = ComputeService.getInstances()
      List<?> imagelist = GlanceService.getImages()
      Map<String, String> ImageidtoName = [:]
      imagelist?.each { eachimage ->
          ImageidtoName.put(eachimage.id, eachimage.name)
      }
      List<?> instancetypelist = ComputeService.getInstanceTypes()

     log.debug("Servers: ${servers}")
      Set<String> appNames = Requests.ensureList(params.id).collect { it.split(',') }.flatten() as Set<String>
      servers = servers.sort { it.name ? it.name.toLowerCase() : "~${it.id}".toString() }
      //Add ASG link
      Map<String, String> InstanceIdToASGname = [:]
      Map<String, String> InstanceIdToASGID = [:]
        servers?.each { eachserver ->

          InstanceIdToASGname.put(eachserver.id, OpenStackInstanceService.getASGNamefromInstance(eachserver.id, eachserver.metadata))
          InstanceIdToASGID.put(eachserver.id,eachserver.metadata.find { it.key == "metering.stack" }?.value)
          DateTime updateDateTime = new DateTime(eachserver.get("OS-SRV-USG:launched_at"),DateTimeZone.UTC).withZone(DateTimeZone.forID( "America/New_York" ));
          eachserver.created = updateDateTime ? Time.format(updateDateTime) : ''

      }
      withFormat {
          html { ['instanceList': servers, 'appNames': appNames, 'asgnamelist': InstanceIdToASGname, 'asgIDlist':InstanceIdToASGID, 'imagenamelist' : ImageidtoName, 'instnacetypelist' : instancetypelist] }
          xml { new XML(servers).render(response) }
          json { new JSON(servers).render(response) }
      }

  }

  def find() {
    UserContext userContext = UserContext.of(request)
    String fieldName = params.by
    List<String> fieldValues = Requests.ensureList(params.value).collect { it.split(',') }.flatten()
    List<MergedInstance> matchingMergedInstances =
      mergedInstanceGroupingService.findByFieldValue(userContext, fieldName, fieldValues)
    if (!matchingMergedInstances) {
      flash.message = 'No results. For field names and values look at ' +
        "${Requests.getBaseUrl(request)}/instance/find.json?by=appName&value=helloworld,cloudmonkey"
    }
    withFormat {
      html { render(view: 'list', model: ['instanceList': matchingMergedInstances]) }
      xml { new XML(matchingMergedInstances).render(response) }
      json { new JSON(matchingMergedInstances).render(response) }
    }
  }

  def audit() {
    UserContext userContext = UserContext.of(request)
    String filter = params.id
    List<MergedInstance> instances = mergedInstanceGroupingService.getMergedInstances(userContext, '')
    List<MergedInstance> taggedInstances = instances.findAll { it.ec2Instance?.tags }
    Map<String, List<MergedInstance>> ownersToInstanceLists = new TreeMap<String, List<MergedInstance>>()
    taggedInstances.each { MergedInstance mergedInstance ->
      Tag ownerTag = mergedInstance.ec2Instance.tags.find { it.key == 'owner' }
      if (ownerTag) {
        String owner = ownerTag.value
        if (!filter || filter == owner) {
          if (!ownersToInstanceLists[owner]) {
            ownersToInstanceLists[owner] = []
          }
          List<MergedInstance> ownedInstances = ownersToInstanceLists[owner]
          ownedInstances << mergedInstance
        }
      }
    }
    ownersToInstanceLists.values().each { it.sort { it.launchTime } }
    withFormat {
      html { ['ownersToInstanceLists': ownersToInstanceLists] }
      xml { new XML(ownersToInstanceLists).render(response) }
      json { new JSON(ownersToInstanceLists).render(response) }
    }
  }

  def diagnose() {
    UserContext userContext = UserContext.of(request)
    String instanceId = EntityType.instance.ensurePrefix(params.instanceId ?: params.id)
    ApplicationInstance appInst = discoveryService.getAppInstance(userContext, instanceId)
    Map details = [
      'discInstance': appInst,
      'healthCheck' : runHealthCheck(appInst)
    ]
    withFormat {
      html { return details }
      xml { new XML(details).render(response) }
      json { new JSON(details).render(response) }
    }
  }

  /* can show instance info given: instanceId, appName+instanceId, appName+hostName */

  def show() {
    UserContext userContext = UserContext.of(request)
    // String instanceId = EntityType.instance.ensurePrefix(params.instanceId ?: params.id)
    String instanceId = EntityType.instance.ensurePrefix(params.instanceId ?: params.id)
    if (!instanceId) {
      flash.error = "Instance does not exist."
      redirect(action: 'list')
    } else {
      instanceId = instanceId.substring(instanceId?.indexOf('-') + 1)
      String appName
      log.debug "instanceId ${instanceId}"
      String RelatedASGname = params.relatedasgname ?: ""
      String RelatedASGID = params.relatedasgID ?: ""
      TreeSet<SecurityGroup> securityGroups = []

      ////After unit test Instance page, need to connect LoginService.
      //OpenstackOS =  asgardUserContextService.openStackLogin

      //Reservation instRsrv = awsEc2Service.getInstanceReservation(userContext, instanceId)
      log.debug "openStackClient ${openStackClient}"
      openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
      org.openstack4j.model.compute.Server instance = openStackClient.compute().servers().get("${instanceId}")
      com.serve.asgard.model.Instance instanceRest
      try {
        instanceRest = ComputeService.getInstanceById("${instanceId}")
      }
      catch (Exception e) {

      }

      if (instanceRest != null) {
        if (instanceRest && instanceRest.securityGroups) {
          securityGroups.addAll(instanceRest.securityGroups)
        }
        log.debug "instance ${instance}"
//
        Map details = [
          instance      : instance,
          instanceRest  : instanceRest,
          securityGroups: securityGroups,
          RelatedASGname: RelatedASGname,
          RelatedASGID  : RelatedASGID
        ]
        withFormat {
          html { return details }
          xml { new XML(details).render(response) }
          json { new JSON(details).render(response) }
        }
      }
      else {
        flash.error = "Instance not found in mongo database."
        redirect(action: 'list')
      }
    }
  }

  def terminate() {
      String instanceId = params.id
      UserContext userContext = UserContext.of(request)
      if (instanceId) {
          try {
              ComputeService.terminateInstance(instanceId)
              flash.message = "Instance has been terminated."

              String floatingip = ""
              floatingip = params.floatingip
              log.debug "instance ${floatingip}"

              //// Delete unassigned Floating IP
              NetworkService.deleteFloatingIps(floatingip)
              flash.message = "Terminate instance(release Floating IP) successfully."

          } catch (Exception e) {
            flash.error = "Could not terminate instance:${instanceId} Error: ${e}"
          }
      }
      else {
        flash.error = "Count not find instance ID. "
      }
    redirect(action: 'list')
  }

  def terminateAndShrinkGroup() {
    String asgName = params.asgName
    String asgID = params.asgID
    redirect(controller:'autoScaling' , action:'edit', params: [id: asgID, name: asgName])
    /*
      String instanceId = params.id
      UserContext userContext = UserContext.of(request)
      if (instanceId) {
          try {
              ComputeService.terminateInstance(instanceId)
              flash.message = "Instance has been terminated."

              OpenStackAutoScalingService.UpdateASGwithInstanceID(instanceId)
              flash.message = "Terminate instance and Config ASG successfully."

              redirect(action: 'list')
          } catch (Exception e) {
              flash.message = "Could not terminate instance and update ASG: ${e}"
              redirect(action: 'list')
          }
      }
      else {
          flash.message= "Count not find instance ID. "
          redirect(action: 'list')
      }
      */
  }

  def reboot() {
    String instanceId = params.id
    UserContext userContext = UserContext.of(request)

     if (instanceId) {
          try {
              ComputeService.reboogInstance(instanceId)
              flash.message = "Rebooting instance '${instanceId}'."

              redirect(action: 'show', params: [instanceId: instanceId])
          } catch (Exception e) {
              redirect(action: 'show', params: [instanceId: instanceId])
              flash.message = "Could not reboot instance instance ${instanceId} Error ${e}"
          }
      }
      else {
          flash.message= "Count not find instance ID. "
          redirect(action: 'list')
      }
  }


  @SuppressWarnings("ReturnsNullInsteadOfEmptyCollection")
  protected def raw() {
    UserContext userContext = UserContext.of(request)
    String instanceId = EntityType.instance.ensurePrefix(params.instanceId ?: params.id)
    try {
      String consoleOutput = awsEc2Service.getConsoleOutput(userContext, instanceId)
      return ['instanceId': instanceId, 'consoleOutput': consoleOutput, 'now': new Date()]
    } catch (AmazonServiceException ase) {
      Requests.renderNotFound('Instance', instanceId, this, ase.toString())
      return
    }
  }

  private void chooseRedirect(String autoScalingGroupName, List<String> instanceIds, String appName = null) {
    Map destination = [action: 'list']
    if (autoScalingGroupName) {
      destination = [controller: 'autoScaling', action: 'show', params: [id             : autoScalingGroupName,
                                                                         runHealthChecks: true]]
    } else if (instanceIds.size() == 1) {
      destination = [action: 'show', params: [id: instanceIds[0]]]
    } else if (appName) {
      destination = [action: 'list', params: [id: appName]]
    }
    redirect destination
  }

  def deregister() {
    UserContext userContext = UserContext.of(request)
    List<String> instanceIds = Requests.ensureList(params.instanceId)
    String autoScalingGroupName = params.autoScalingGroupName

    Set<String> lbNames = new TreeSet<String>()
    instanceIds.each { id ->
      List<LoadBalancerDescription> loadBalancers = awsLoadBalancerService.getLoadBalancersFor(userContext, id)
      lbNames.addAll(loadBalancers.collect { it.loadBalancerName })
    }

    if (lbNames.isEmpty()) {
      flash.message = "No load balancers found for instance${instanceIds.size() > 1 ? "s" : ""} '${instanceIds}'"
    } else {
      lbNames.each { lbName -> awsLoadBalancerService.removeInstances(userContext, lbName, instanceIds) }
      flash.message = "Deregistered instance${instanceIds.size() > 1 ? "s" : ""} '${instanceIds}' from " +
        "load balancer${lbNames.size() > 1 ? "s" : ""} '${lbNames}'."
    }
    chooseRedirect(autoScalingGroupName, instanceIds)
  }

  def register() {
    UserContext userContext = UserContext.of(request)
    List<String> instanceIds = Requests.ensureList(params.instanceId)
    String autoScalingGroupName = params.autoScalingGroupName

    if (instanceIds) {
      AutoScalingGroup group = awsAutoScalingService.getAutoScalingGroup(userContext, autoScalingGroupName)
      if (!group) {
        group = awsAutoScalingService.getAutoScalingGroupFor(userContext, instanceIds[0])
      }

      // Ensure all instances are in the same group
      List<String> groupInstanceIds = group?.instances?.collect { it.instanceId } ?: []
      if (instanceIds.every { groupInstanceIds.contains(it) }) {
        List<String> elbNames = group.loadBalancerNames
        if (elbNames.isEmpty()) {
          flash.message = "There are no load balancers on group '${group.autoScalingGroupName}'"
        } else {
          elbNames.each { elbName -> awsLoadBalancerService.addInstances(userContext, elbName, instanceIds) }
          flash.message = "Registered instance${instanceIds.size() == 1 ? '' : 's'} ${instanceIds} with " +
            "load balancer${elbNames.size() == 1 ? '' : 's'} ${elbNames}"
        }
      } else {
        String groupName = group?.autoScalingGroupName
        flash.message = "Error: Not all instances '${instanceIds}' are in group '${groupName}'"
      }
    }

    chooseRedirect(autoScalingGroupName, instanceIds)
  }

  def associate() {
    UserContext userContext = UserContext.of(request)
    Instance instance = awsEc2Service.getInstance(userContext, EntityType.instance.ensurePrefix(params.instanceId))
    if (!instance) {
      flash.message = "EC2 Instance ${params.instanceId} not found."
      redirect(action: 'list')
      return []
    } else {
      Map<String, String> publicIps = awsEc2Service.describeAddresses(userContext)
      log.debug "describeAddresses: ${publicIps}"
      return [
        instance       : instance,
        publicIps      : publicIps,
        eipUsageMessage: grailsApplication.config.cloud.eipUsageMessage ?: null
      ]
    }
  }

  def associateDo() {
    log.debug "associateDo: ${params}"
    String publicIp = params.publicIp
    String instanceId = EntityType.instance.ensurePrefix(params.instanceId)
    UserContext userContext = UserContext.of(request)
    try {
      awsEc2Service.associateAddress(userContext, publicIp, instanceId)
      flash.message = "Elastic IP '${publicIp}' has been associated with '${instanceId}'."
    } catch (Exception e) {
      flash.message = "Could not associate Elastic IP '${publicIp}' with '${instanceId}': ${e}"
    }
    redirect(action: 'show', params: [instanceId: instanceId])
  }

  def takeOutOfService() {
    UserContext userContext = UserContext.of(request)
    String autoScalingGroupName = params.autoScalingGroupName
    List<String> instanceIds = Requests.ensureList(params.instanceId)
    discoveryService.disableAppInstances(userContext, params.appName, instanceIds)
    flash.message = "Instances of app '${params.appName}' taken out of service in discovery: '${instanceIds}'"
    chooseRedirect(autoScalingGroupName, instanceIds)
  }

  def putInService() {
    UserContext userContext = UserContext.of(request)
    String autoScalingGroupName = params.autoScalingGroupName
    List<String> instanceIds = Requests.ensureList(params.instanceId)
    discoveryService.enableAppInstances(userContext, params.appName, instanceIds)
    flash.message = "Instances of app '${params.appName}' put in service in discovery: '${instanceIds}'"
    chooseRedirect(autoScalingGroupName, instanceIds)
  }

  def addTag() {
    String instanceId = EntityType.instance.ensurePrefix(params.instanceId)
    UserContext userContext = UserContext.of(request)
    awsEc2Service.createInstanceTag(userContext, [instanceId], params.name, params.value)
    redirect(action: 'show', params: [instanceId: instanceId])
  }

  def removeTag() {
    String instanceId = EntityType.instance.ensurePrefix(params.instanceId)
    UserContext userContext = UserContext.of(request)
    awsEc2Service.deleteInstanceTag(userContext, instanceId, params.name)
    redirect(action: 'show', params: [instanceId: instanceId])
  }

  def userData() {
    UserContext userContext = UserContext.of(request)
    String instanceId = EntityType.instance.ensurePrefix(params.id ?: params.instanceId)
    render awsEc2Service.getUserDataForInstance(userContext, instanceId)
  }

  def userDataHtml() {
    UserContext userContext = UserContext.of(request)
    String instanceId = EntityType.instance.ensurePrefix(params.id ?: params.instanceId)
    render "<pre>${awsEc2Service.getUserDataForInstance(userContext, instanceId).encodeAsHTML()}</pre>"
  }

  private String runHealthCheck(ApplicationInstance appInst) {
    appInst?.healthCheckUrl ? (awsEc2Service.checkHostHealth(appInst?.healthCheckUrl) ? 'pass' : 'fail') : 'NA'
  }
}
