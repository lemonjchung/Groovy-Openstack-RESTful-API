package com.cluster
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.ec2.model.Image
import com.netflix.asgard.*
import com.netflix.asgard.model.AutoScalingGroupData
import com.netflix.asgard.push.ClusterData

class ClusterService {

  def openStackComputeService
  def openStackAutoScalingService
  def openStackHeatService
  def ComputeService
  def heatService

  public static List<AutoScalingGroupMongodb> asgnamelist = []
  public static staticclustername = ''

  def appDatabaseMongoService
  AppDatabase appDatabase

  void afterPropertiesSet() {
    appDatabase = appDatabaseMongoService
  }

  AutoScalingGroupMongodb getAutoScalingGroupByName(String name) {
      // TODO: Sachin - This code needs to be refactored. Change all of it to use GORM
      appDatabase.getAutoScalingGroup(name)
  }

  public ClusterData getCluster(String name) {
    // Need to move ClusterService.groovy
    if (!name) {
      return null
    }
    String clusterName = Relationships.clusterFromGroupName(name)
    if (!clusterName) {
      return null
    }
    Collection<AutoScalingGroup> allGroupsSharedCache = []  //getAutoScalingGroups(userContext)
    ClusterData cluster

    try {
      cluster = buildCluster(allGroupsSharedCache, clusterName)
    } catch (Exception ex) {
      log.info("getCluster Error: ${ex}")
    }
    cluster
  }

  ClusterData buildCluster(Collection<AutoScalingGroup> allGroups, String clusterName) {
    // Need to move ClusterService.groovy
    appDatabase = appDatabaseMongoService
    ClusterMongodb ak = appDatabase.getCluster(clusterName)
    List<AutoScalingGroupData> asglist = []
    log.debug("o.asglist ${ak.asglist}")

    List<AutoScalingGroupData> asglistwithbuild = []
    List<Integer> ASGbuildlist = []
    String imagebuildnumber= ""

    List<?> InstanceList = ComputeService.getInstances()

    //add asgname to asgnameliast
    staticclustername = clusterName
    asgnamelist = ak.asglist
    ak.asglist?.each { eachasg ->
      log.debug("eachasg ${eachasg}")
      //// Get Instance from openstack
      List<InstanceMongodb> asginstancelist = []
      asginstancelist = openStackAutoScalingService.GetInstances_ipaddr(eachasg.asgname, InstanceList)

      List<Instance> instancelist = []
      List<MergedInstance> merinstlist = []
      Map<String, Image> imageIdsToImages = [:]

      String lastsmiid = ""
      asginstancelist?.each { InstanceMongodb eachk ->

        Instance newinstance = new Instance().withInstanceId(eachk.instanceid).
                withLifecycleState(eachk.instancestatus)
        MergedInstance mergeint = new MergedInstance(instanceId: eachk.instanceid, amiId: eachk.imageid)

        // get instances
        merinstlist.add(mergeint)
        instancelist.add(newinstance)

        // get instance imageid
        // get netflix friggan build number
        imagebuildnumber = ConvertfriggaBuildNumber(eachk.imagename)
        //This is for nextflix frigga build Application format. for extracting image build number, need to add some dummy string
        String appVersion = "Openstack-1.4.0-1111111.h"+ imagebuildnumber +"/imagebuild-smi"+ imagebuildnumber + "/" + imagebuildnumber
        log.debug("eachk appVersion ${imagebuildnumber}")
        Image instanceimage = new Image(imageId: eachk.imageid, tags: [new com.amazonaws.services.ec2.model.Tag(key: 'appversion', value: appVersion)])
        imageIdsToImages.put(eachk.imageid, instanceimage)

        lastsmiid = eachk.imageid
      }

      AutoScalingGroupData a = (
              AutoScalingGroupData.from(new AutoScalingGroup(autoScalingGroupName: eachasg.asgname,
                createdTime: new Date(), instances: instancelist, status: heatService.getStackDetails(eachasg.asgname, eachasg.stackid).getStackStatus()), null, merinstlist, imageIdsToImages, []))

      if (eachasg.stackid) {
        //a.template = openStackHeatService.getTemplateValue(eachasg.stackid)
        a.smiid = lastsmiid
       String lastimageid = "" // a?.template.find("\"image\":\"[0-9]\"") //d("\"image\":(\\w+)")
      }

      //Check ASG Disable/Enable via Pool memeger
      boolean asgActiveStatus = openStackAutoScalingService.CheckDisableAutoScalingGroup(eachasg.asgname, asginstancelist)
      boolean chk = a.GetASGdisable(asgActiveStatus)
      boolean asbbuild = a.SetASGimagenulbernumber(imagebuildnumber)

      if (asgActiveStatus == false) {
        ASGbuildlist.add(imagebuildnumber)
      }
      asglist.add(a)
    }

    //add Image Build number
    asglist?.each { AutoScalingGroupData eachasg ->
      AutoScalingGroupData asg = eachasg
      String asgcolor = calculateASGbackcolor(ASGbuildlist, eachasg.GetASGImagebuildnumber())

      boolean  eachdisable = eachasg.seemsDisabled()
      //           boolean eachchk = asg.GetASGdisable(eachdisable)
      asg.SetASGColor(asgcolor, eachdisable)
      boolean asbbuild = asg.SetASGimagenulbernumber(eachasg.GetASGImagebuildnumber().toString())
      asglistwithbuild.add(asg)

    }
    return new ClusterData(asglistwithbuild)
  }

  String calculateASGbackcolor(List<Integer> asgbuildlist, Integer currentbuild) {
    String asgcolor = "black"

    if (asgbuildlist.size() > 0 ) {
      try {
        List<Integer> asgbuildnolist = asgbuildlist.sort()
        //asgbuildnolist.min().toInteger() == currentbuild
        if (asgbuildnolist.max().toInteger() == currentbuild && asgbuildlist.size() > 1) {
          asgcolor = "#800000"
        } else {
          asgcolor = "black"
        }
      } catch (Exception ex) {
        asgcolor = "black"
      }
    }
    return asgcolor
  }

  String ConvertfriggaBuildNumber(String sminame) {
    String returnBuildNumber = ""
    try {
      String imagename = sminame.find( /\d.+/ )
      String tempname = ""

      if (imagename.size() > 0 ) {
        imagename =imagename.replace(".", "")
        tempname = imagename.replace("_", "-")
      }
      if (tempname.size() > 0) {
        tempname = tempname.takeWhile { it != '-' }
      }
      returnBuildNumber = tempname
    } catch (Exception ex) {
      log.info("ConvertfriggaBuildNumber Error: ${ex}")
      returnBuildNumber= ""
    }
    return returnBuildNumber
  }


}
