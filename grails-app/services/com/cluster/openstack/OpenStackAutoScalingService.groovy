package com.serve.asgard.openstack

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.asgard.AutoScalingGroupMongodb
import com.netflix.asgard.InstanceMongodb
import com.serve.asgard.AppDatabase
import com.serve.asgard.AutoScalingGroup
import com.serve.asgard.Stack
import com.serve.asgard.model.Image
import com.serve.asgard.model.LoadBalancer
import com.serve.asgard.model.LoadBalancerPoolMember
import com.serve.asgard.model.SecurityGroup
import org.openstack4j.api.OSClient
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.openstack.OSFactory
import org.springframework.beans.factory.InitializingBean

class OpenStackAutoScalingService implements InitializingBean {
  def grailsApplication
  private OSClient openStackClient;
  def openStackLoginService
  def openStackRestService
  def AppDatabaseMongoService
  AppDatabase appDatabase;
  def LoadBalancerPoolMembers
  def OpenStackHeatService
  def openStackLoadBalancerService
  def asgardUserContextService
  def networkService
  def ComputeService
  def HeatService
  def graphiteService

  @Override
  void afterPropertiesSet() throws Exception {
    appDatabase = appDatabaseMongoService
  }

  //TODO: Remove the mongodb reference from here. Use GORM instead
  public AutoScalingGroup getAutoScalingGroup(String name) {
    AutoScalingGroup autoScalingGroup = new AutoScalingGroup()
    AutoScalingGroupMongodb dbObject = appDatabase.getAutoScalingGroup(name)
    if (dbObject) {
      autoScalingGroup.name = dbObject.asgname
      Stack stack = new Stack()
      stack.stackId = dbObject.stackid
      stack.stackName = dbObject.stackName
      autoScalingGroup.stack = stack
    }
    autoScalingGroup
  }


  public void SavetoMongodb(String clname, String asgname, String appname, String stackid, String stackName, String poolid, Object template) {
    //It will be moved to ClusterServer.groovy
    appDatabase = appDatabaseMongoService

//        //Get New AutoScalingGroup name
//        String newASGname = appDatabase.BuildNewAutoScalingGroupName(clname, asgname, appname)
    appDatabase.UpdateApplicationCluster(clname, asgname, appname, stackid, stackName, poolid, template)
  }

  public String GetASGName(String clname, String asgname, String appname) {
    //It will be moved to ClusterServer.groovy

    appDatabase = appDatabaseMongoService

    //Get New AutoScalingGroup name
    String newASGname = asgname
    try {
      newASGname = appDatabase.BuildNewAutoScalingGroupName(clname, asgname, appname)
    } catch (Exception ex) {
      log.info("GetASGName Error: ${ex}")
    } finally {
      return newASGname
    }
  }

  public void DeleteAutoScalingGroup(String stackname, String stackid) {
    try {
        def deletestack = HeatService.deleteStack(stackname, stackid)
      appDatabase.DeleteAutoScalingGroup(stackname)
        graphiteService.incrementDeleteASG()
//      if (deletestack.status == 204) {
//        log.info("Stack Deleted Stack Name: ${stackname}")
//        //Delete asg from mongodb
//        appDatabase.DeleteAutoScalingGroup(stackname)
//      } else {
//        log.info("Status : ${deletestack.status}")
//      }

    } catch (Exception ex) {
      log.error("Could not Delete AutoScaling Group: ${ex}")
    }
  }

  public InstanceMongodb[] GetInstanceswithASG_OS(String stackname, String stackid) {
    List<InstanceMongodb> asginstancelist = []
    try {
      def allinstance = ComputeService.getInstances()
      if (allinstance != null) {
        def instanceList = allinstance

        for (HashMap eachinstance : instanceList) {
          String instancenm = eachinstance.get("name").toString()
          String instanceid = eachinstance.get("id").toString()
          def instancemetadata = eachinstance.get("metadata")
          def instanceaddr = eachinstance.get("addresses")
          String instancestatus = eachinstance.get("status").toString()
          def imagelist = eachinstance.get("image")
          String imageid = ""
          imagelist?.each { eachimage ->
            if (eachimage.key == "id") {
              imageid = eachimage.value
            }
          }

          String asggroupname = ""
          instancemetadata?.each { eachmeta ->
            if (eachmeta.key == "metering.stack") {

              asggroupname = eachmeta.value
              //////////// Find Related instance

              if (asggroupname == stackid) {
                InstanceMongodb asginstnace = new InstanceMongodb()
                asginstnace.instacename = instancenm
                asginstnace.instanceid = instanceid
                asginstnace.autoscalinggroupname = asggroupname

                //Pass Image image name
                String imagename = ""
                imagelist?.each { eachimage ->
                  if (eachimage.key == "id") {
                    imageid = eachimage.value
                    com.serve.asgard.model.Image eachimagename = getImageName(imageid)
                    imagename = eachimagename.name
                  }
                }
                asginstnace.imageid = imageid
                asginstnace.imagename = imagename
                asginstnace.instancestatus = instancestatus

                String instanceip = ""
                String privateip = ""
                // tpadev-esf-network-int=[{OS-EXT-IPS:type=fixed, addr=192.168.4.248, OS-EXT-IPS-MAC:mac_addr=fa:16:3e:9f:54:8a, version=4}, {OS-EXT-IPS:type=floating, addr=10.114.5.29, OS-EXT-IPS-MAC:mac_addr=fa:16:3e:9f:54:8a, version=4}]
                instanceaddr.each { eachaddr ->
                  List<?> eachaddrlist = eachaddr.value
                  if (eachaddrlist != null && eachaddrlist.size() > 0) {
                    //Try to get floating IP address
                    eachaddrlist?.each {  eachips ->
                      if (eachips.find{ it.key == "OS-EXT-IPS:type"}?.value == "floating") {
                        instanceip = eachips.find{ it.key == "addr"}?.value
                      }
                      if (eachips.find{ it.key == "OS-EXT-IPS:type"}?.value == "fixed") {
                        privateip = eachips.find{ it.key == "addr"}?.value
                      }
                    }
                    //If no floating IP, try to get Internal IP(private IP)
                    if (instanceip == "") {
                      instanceip = privateip
                    }
                  }
                }
                asginstnace.instanceipaddress = instanceip
                asginstancelist.add(asginstnace)
              }
            }
          }

        }
      }

    } catch (Exception ex) {
      log.error("GetInstanceswithASG Error: ${ex}")
      ex.printStackTrace()
    }
    finally {
      return asginstancelist
    }
  }



  public void DeactiveOrActiveInstaces(List<InstanceMongodb> instancelist, Boolean isactive, String asgname) {
    try {
      instancelist.each { InstanceMongodb eachinstance ->

        if (eachinstance.instanceipaddress != null) {
          if (isactive == true) {
            //Add pool member
            if (eachinstance.instanceipaddress != "") {
              appDatabase = appDatabaseMongoService
              String poolid = appDatabase.getPoolid(asgname)
              AddLBmember(eachinstance.instanceipaddress, poolid, "443")
              // change 8080 to 443 by Joanne
            } else {
              //log.info("No Instance Ipaddress : ${eachinstance.instacename}")
            }
          } else {
            //Remove pool member
            def LBmember = networkService.getLoadBalancerMember(eachinstance.instanceipaddress)
            String memberid = LBmember ? LBmember?.id : ''
            if (memberid != "") {
              DeleteLBmember(memberid)
            }
          }
        }
      }
    } catch (Exception ex) {
      log.error("Deactiveinstaces Error: ${ex}")
      ex.printStackTrace()
    }
  }

  public void DeleteLBmember(String lbmemberid) {
    try {
      log.info("In delete LB member")
      networkService.disassociateLoadBalancerMember(lbmemberid)
    } catch (Exception ex) {
      log.error("Could not Delete LB Member. Error: ${ex}")
    }
  }

  public void AddLBmember(String lbIpaddress, String poolid, String portnumber) {
    try {
      LoadBalancerPoolMember member = new LoadBalancerPoolMember()
      member.poolId = poolid
      member.address = lbIpaddress
      member.protocolPort = '443'
      networkService.associateLoadBalancerMember(member)
    } catch (Exception ex) {
      log.error("Could not Add LB Member IP address ${lbIpaddress}. Error: ${ex}")
    }
  }

  public void updateAutoScalingGroup(String asgName, Integer minSize, Integer maxSize, Integer desiredCapacity, Integer coolDown) {
    appDatabase = appDatabaseMongoService

    //Get StackID
    AutoScalingGroupMongodb asg = appDatabase.getAutoScalingGroup(asgName)
      def jsonTemplate = HeatService.getTemplateAsJson(asg.asgname, asg.stackid)
      // OpenStackHeatService.getStackTemplateAsJson(asg.stackid)
    //Change ASG min size and max size
    String responseJson
    if (jsonTemplate) {
      responseJson = (new ObjectMapper()).writeValueAsString(jsonTemplate)
    }
    //Change ASG min size and max size
    responseJson = responseJson.replaceAll("\"min_size\":[1-9]", "\"min_size\":$minSize")
      .replaceAll("\"max_size\":[1-9]", "\"max_size\":$maxSize")
      .replaceAll("\"desired_capacity\":[1-9]", "\"desired_capacity\":$desiredCapacity")
      .replaceAll("\"cooldown\":[1-100][1-100]", "\"cooldown\":$coolDown")

    //Update ASG template
    def resp = HeatService.updateStack(responseJson, asgName, asg.stackid) //openStackHeatService.updateStack(jsonTemplate, asgname, asg.stackid)

  }


  public int ResizeAutoScalingGroupInstance(String asgname, Integer minsize, Integer maxsize) {
    appDatabase = appDatabaseMongoService
    int resCount
    try {
      //Get StackID
      AutoScalingGroupMongodb asg = appDatabase.getAutoScalingGroup(asgname)
        def jsonTemplate = HeatService.getTemplateAsJson(asg.asgname, asg.stackid)
        // OpenStackHeatService.getStackTemplateAsJson(asg.stackid)
      log.debug("Perform Resize of ASG: ${asgname}")
      log.debug("Template before the update for resize: ${jsonTemplate}")
      //Change ASG min size and max size
      String responseJson
      if (jsonTemplate) {
        responseJson = (new ObjectMapper()).writeValueAsString(jsonTemplate)
      }
      responseJson = responseJson.replaceAll("\"min_size\":[1-9]", "\"min_size\":$minsize")
              .replaceAll("\"max_size\":[1-9]", "\"max_size\":$maxsize")
              .replaceAll("\"desired_capacity\":[1-9]", "\"desired_capacity\":$minsize")
      log.debug("Template after the update for resize: ${responseJson}")
      //Update ASG template
      def resp = HeatService.updateStack(responseJson, asgname, asg.stackid) //openStackHeatService.updateStack(jsonTemplate, asgname, asg.stackid)
      resCount = responseJson.findAll("OS::Heat::AutoScalingGroup").size
    } catch (Exception ex) {
      log.info("Could not ResizeAutoScalingGroup ASGname: ${asgname}. Error: ${ex}")
    }
    return resCount
  }

  public String GetAutoScalingGroupStackid(String stackname) {
    // get stackid from mongodb
    String stackid = ""
    appDatabase = appDatabaseMongoService

    try {
      stackid = appDatabase.getStackid(stackname)

    } catch (Exception ex) {
      log.info("Could not get AutoScaling Group. Error: ${ex}")
      stackid = ""
    }
    return stackid
  }

  public boolean CheckDisableAutoScalingGroup(String asgname, List<InstanceMongodb> instancelist) {
    boolean returnvalue = false
    try {
      instancelist.any { InstanceMongodb eachinstance ->
        if (eachinstance.instanceipaddress != null) {
          //getmemberid pool member
          def LBmember = networkService.getLoadBalancerMember(eachinstance.instanceipaddress)
          String memberid = LBmember ? LBmember?.id : ''
          if (memberid == "") {
            //No pool member (disable ASG)
            returnvalue = true
            return
          }
        }
      }
    } catch (Exception ex) {
      log.error("CheckDisableAutoScalingGroup Error: ${ex}")
    } finally {
      return returnvalue
    }
  }

  /**
   * Outputs AutoScalingGroup details of on ASG based on StackName and StackID
   *
   */

  public ArrayList<String> getLoadBalancerNames(ArrayList<String> loadBalancerIDs) {
    List<LoadBalancer> loadBalancers = openStackLoadBalancerService.getLoadBalancerPools()?.pools
    def loadBalancerNames = []
    for (loadBalancerID in loadBalancerIDs) {
      log.info(loadBalancerID)
      for (loadBalancer in loadBalancers) {
        if (loadBalancer.id == loadBalancerID.get("Ref")) {
          loadBalancerNames.add(loadBalancer)
        }
      }
    }
    loadBalancerNames
  }

  public com.serve.asgard.model.Image getImageName(String imageID) {
    com.serve.asgard.model.Image finalImage
    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
    org.openstack4j.model.image.Image osImage = openStackClient.images().get(imageID);
    if (osImage) {
      finalImage = new Image()
      finalImage.id = osImage.id
      finalImage.name = osImage.name
    } else {
      finalImage = new Image()
      finalImage.id = imageID
      finalImage.name = imageID
    }

    finalImage
  }

  public ArrayList<SecurityGroup> getSecGroupNames(ArrayList<String> secGroupIDs) {
    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
    def secGroupNames = []
    for (secGroupID in secGroupIDs) {
      SecGroupExtension group = openStackClient.compute().securityGroups().get(secGroupID);
      if (group) {
        SecurityGroup secGroup = new SecurityGroup()
        secGroup.groupId = group.id
        secGroup.groupName = group.name
        secGroup.description = group.description
        secGroupNames.add(secGroup)
      }
    }
    secGroupNames
  }

  org.openstack4j.model.heat.Stack getStackDetailsById(String id) {
    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess)
    org.openstack4j.model.heat.Stack stack
    List<org.openstack4j.model.heat.Stack> stackList = openStackClient.heat().stacks().list()
    stackList?.each { osStack ->
      if (id.equalsIgnoreCase(osStack.getId())) {
        stack = osStack
      }
    }
    stack
  }

  public void UpdateASGwithInstanceID(String InstanceID) {
    String stackid = ""
    Server instancedata
    //Need to convert from openstack4j to Rest API
    openStackClient = OSFactory.clientFromAccess(asgardUserContextService.currentTenant.openStack4jAccess);
    instancedata = openStackClient.compute().servers().get(InstanceID)
    Map<String, String> InstanceMeta = instancedata.getMetadata();
    String asgnm
    if (InstanceMeta.size() > 0) {
      stackid = InstanceMeta.find { it.key == "metering.stack" }?.value

      def asggroupname = InstanceMeta.find { it.key == "metering.AutoScalingGroupName" }?.value
      if (asggroupname.size() > 0) {
        Number asgint = asggroupname.lastIndexOf("-")
        asggroupname = asggroupname.substring(0, asgint)

        if (asggroupname.indexOf("-v", 0) > 0 ) {
          //myapplication-v003-myapplication-v003
          asgnm = asggroupname.substring(0, asggroupname.indexOf("-", asggroupname.indexOf("-v", 0)+1))
        }
        else {
          //myapplication-myapplication
          asgnm = asggroupname.substring(0, asggroupname.indexOf("-",0))
        }
        log.debug("Extact ASGName: ${asgnm}")
      }
    }
    if (stackid != "") {
      def jsonTemplate = OpenStackHeatService.getStackTemplateAsJson(stackid)
      //Change ASG min size
      def Minsize = jsonTemplate.find("\"MinSize\":[1-9]")
      def currentminsize = Minsize.find( /\d+/ )
      Integer tominsize = (currentminsize ?: 0) as Integer
      tominsize = tominsize -1
      log.debug("Resize to Instance : ${tominsize}")
      jsonTemplate = jsonTemplate.replaceAll("\"MinSize\":[1-9]", "\"MinSize\":$tominsize")
      //Update ASG template
      def resp = openStackHeatService.updateStack(jsonTemplate, asgnm, stackid)
    }
  }

  public InstanceMongodb[] GetInstances(String stackname, List<?> instances) {
    List<InstanceMongodb> asginstancelist = []

    try {
        instances.each { eachinstance ->
          String instanceid = eachinstance.get("id").toString()
          String instancename = eachinstance.get("name").toString()
          def instancemetadata = eachinstance.get("metadata")

          String asggroupname = ""
          instancemetadata?.each { eachmeta ->
            if (eachmeta.key == "metering.AutoScalingGroupName") {
              asggroupname = eachmeta.value
              if (asggroupname.size() > 0) {
                if (asggroupname.lastIndexOf('_') > 0 ) {
                  asggroupname =asggroupname.substring(0, asggroupname.lastIndexOf('_'))
                }
              }

              if (stackname == asggroupname) {
                InstanceMongodb asginstnace = new InstanceMongodb()
                asginstnace.instanceid = instanceid
                asginstnace.instacename = instancename
                asginstnace.autoscalinggroupname = asggroupname


                asginstancelist.add(asginstnace)
              }
            }

          }
        }

      } catch (Exception ex ) {
        log.info("GetInstances Error: ${ex}")
        ex.printStackTrace()
      }
      finally {
        return asginstancelist
      }
    }


  public InstanceMongodb[] GetInstances_ipaddr(String stackname, List<?> instances) {
    List<InstanceMongodb> asginstancelist = []

    try {
      instances.each { eachinstance ->
        String instanceid = eachinstance.get("id").toString()
        def instancemetadata = eachinstance.get("metadata")
        def instanceaddr = eachinstance.get("addresses")
        String instancestatus = eachinstance.get("status").toString()
        def imagelist = eachinstance.get("image")
        String imageid = ""
        imagelist?.each { eachimage ->
          if (eachimage.key == "id") {
            imageid = eachimage.value
          }
        }

        String asggroupname = ""
        instancemetadata?.each { eachmeta ->
          if (eachmeta.key == "metering.AutoScalingGroupName") {
            asggroupname = eachmeta.value
            if (asggroupname.size() > 0) {
              if (asggroupname.lastIndexOf('_') > 0 ) {
                asggroupname =asggroupname.substring(0, asggroupname.lastIndexOf('_'))
              }
            }

            if (stackname == asggroupname) {
              InstanceMongodb asginstnace = new InstanceMongodb()
              asginstnace.instanceid = instanceid
              asginstnace.autoscalinggroupname = asggroupname

              //Pass Image image name
              String imagename = ""
              imagelist?.each { eachimage ->
                if (eachimage.key == "id") {
                  asginstnace.imageid = eachimage.value
                  com.serve.asgard.model.Image eachimagename = getImageName(imageid)
                  asginstnace.imagename = eachimagename.name
                }
              }
              asginstnace.instancestatus = instancestatus

              String instanceip = ""
              String privateip = ""
              // tpadev-esf-network-int=[{OS-EXT-IPS:type=fixed, addr=192.168.4.248, OS-EXT-IPS-MAC:mac_addr=fa:16:3e:9f:54:8a, version=4}, {OS-EXT-IPS:type=floating, addr=10.114.5.29, OS-EXT-IPS-MAC:mac_addr=fa:16:3e:9f:54:8a, version=4}]
              instanceaddr.each { eachaddr ->
                List<?> eachaddrlist = eachaddr.value
                if (eachaddrlist != null && eachaddrlist.size() > 0) {
                  //Try to get floating IP address
                  eachaddrlist?.each {  eachips ->
                    if (eachips.find{ it.key == "OS-EXT-IPS:type"}?.value == "floating") {
                      instanceip = eachips.find{ it.key == "addr"}?.value
                    }
                    if (eachips.find{ it.key == "OS-EXT-IPS:type"}?.value == "fixed") {
                      privateip = eachips.find{ it.key == "addr"}?.value
                    }
                  }
                  //If no floating IP, try to get Internal IP(private IP)
                  if (instanceip == "") {
                    instanceip = privateip
                  }
                }
              }
              asginstnace.instanceipaddress = instanceip
              asginstancelist.add(asginstnace)
            }
          }

        }
      }

    } catch (Exception ex ) {
      log.error("GetInstances_ipaddr Error: ${ex}")
      ex.printStackTrace()
    }
    finally {
      return asginstancelist
    }
  }

}
