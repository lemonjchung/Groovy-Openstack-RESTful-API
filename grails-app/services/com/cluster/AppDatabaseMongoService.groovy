package com.cluster
import com.mongodb.*
import com.netflix.asgard.*
import com.netflix.asgard.model.MonitorBucketType
import com.serve.asgard.model.HeatStack
import org.springframework.beans.factory.InitializingBean
import com.netflix.asgard.Application

class AppDatabaseMongoService implements AppDatabase, InitializingBean {
  def configService
  DBCollection applications
  def grailsApplication
  def AutoScalingGroupMongodb
  def openStackHeatService
  def asgardUserContextService
  def heatService

  void afterPropertiesSet() {
    String connString = grailsApplication.config.appDatabase.mongo.connectionString
    def uri = new MongoClientURI(connString)
    MongoClient mongoClient = new MongoClient(uri)
    DB db = mongoClient.getDB('applications')
    applications = db.getCollection('applications')
  }

  @Override
  public void createApplication(Application application, MonitorBucketType monitorBucketType, String createTs, String updateTs) {
    BasicDBObject doc = new BasicDBObject("name", application.name)
      .append('group', application.group)
      .append('type', application.type)
      .append('description', application.description)
      .append('owner', application.owner)
      .append('email', application.email)
      .append('tags', application.tags)
      .append('monitorBucketType', monitorBucketType.name())
      .append('createTs', createTs)
      .append('updateTs', updateTs)
      .append('tenantId', application.tenantId)
          .append('loadBalancer', application.selectedLoadBalancersForVpcId)
          .append('internalNetwork', application.internalnetwork)
          .append('externalNetwork', application.externalNetwork)
          .append('instanceType', application.instanceType)
          .append('securityGroups', application.selectedSecurityGroups)
          .append('componentName', application.component)
          .append('appShortName', application.app)


    applications.insert(doc)
  }

  @Override
  public void updateApplication(Application application, MonitorBucketType monitorBucketType, String updateTs) {
    BasicDBObject query = new BasicDBObject()
    query.put("name", application.name)
    query.put("tenantId", application.tenantId)

    DBCursor cursor = applications.find(query);

    if (cursor.hasNext()) {
      BasicDBObject doc = cursor.next();
      applications.update(doc, new BasicDBObject('$set', new BasicDBObject(
        'group', application.group)
        .append('type', application.type)
        .append('description', application.description)
        .append('owner', application.owner)
        .append('email', application.email)
        .append('tags', application.tags)
        .append('monitorBucketType', monitorBucketType.name())
        .append('updateTs', updateTs)
                                .append('tenantId', application.tenantId)
                                .append('loadBalancer',application.selectedLoadBalancersForVpcId)
                                .append('internalNetwork',application.internalnetwork)
                                .append('externalNetwork',application.externalNetwork)
                                .append('instanceType',application.instanceType)
                                .append('securityGroups',application.selectedSecurityGroups)
                                .append('componentName',application.component)
                                .append('appShortName',application.app)))

    }
  }

  @Override
  public void deleteApplication(String name) {
    BasicDBObject doc = new BasicDBObject()
    doc.put("name", name)
    doc.put("tenantId", asgardUserContextService.currentTenant.access.token.tenant.id)

    applications.findAndRemove(doc)
  }

  @Override
  public com.netflix.asgard.Application getApplicationDetails(String appName) {
    BasicDBObject doc = new BasicDBObject()
    doc.put("name", appName)
    doc.put("tenantId", asgardUserContextService.currentTenant.access.token.tenant.id)

    DBCursor cursor = applications.find(doc)
    Application app = new Application()
    if (cursor.hasNext()) {
      BasicDBObject resp = cursor.next()
      app.setName(resp.get('name'));
      app.setGroup(resp.get('group'));
      app.setType(resp.get('type'));
      app.setDescription(resp.get('description'));
      app.setOwner(resp.get('owner'));
      app.setEmail(resp.get('email'));
      app.setTenantId(resp.get('tenantId'));
      app.setTags(resp.get('tags'));
      app.setCreateTs(resp.get('createTs'));
      app.setUpdateTs(resp.get('updateTs'));
            app.setSelectedLoadBalancersForVpcId(resp.get('loadBalancer'));
            app.setInternalnetwork(resp.get('internalNetwork'));
            app.setExternalNetwork(resp.get('externalNetwork'));
            app.setInstanceType(resp.get('instanceType'));
            app.setSelectedSecurityGroups(resp.get('securityGroups'));
            app.setComponent(resp.get('componentName'));
            app.setApp(resp.get('appShortName'));

      BasicDBList clustersDbList = (BasicDBList) resp.get("clusters");
      clustersDbList.each {
        DBObject clusterDbObj = (DBObject) it;
        ClusterMongodb cluster = new ClusterMongodb()
        cluster.setClustername(clusterDbObj.get('clustername'))
        BasicDBList asgDbList = (BasicDBList) clusterDbObj.get("asg");
        asgDbList.each {
          com.netflix.asgard.AutoScalingGroupMongodb asg = new AutoScalingGroupMongodb()
          DBObject asgDbObj = (DBObject) it;
          asg.setAsgname(asgDbObj.get('asgname'))
          asg.setStackid(asgDbObj.get('stackid'))
          asg.setStackName(asgDbObj.get('stackname'))
          cluster.asglist.add(asg)
        }
        app.clusters.add(cluster)
      }
    }
    app
  }

  @Override
  public AppRegistration getApplication(String appName) {
    BasicDBObject doc = new BasicDBObject()
    doc.put("name", appName)
    doc.put("tenantId", asgardUserContextService.currentTenant.access.token.tenant.id)

    DBCursor cursor = applications.find(doc)
    AppRegistration app
    if (cursor.hasNext()) {
      BasicDBObject resp = cursor.next()
      app = convertDocToAppRegistration(resp);
    }
    app
  }

  @Override
  public AppRegistration[] getAllApplications() {
    DBCursor cursor = applications.find()
    List<AppRegistration> regs = []
    while (cursor.hasNext()) {
      BasicDBObject doc = cursor.next()
      regs.add(convertDocToAppRegistration(doc))
    }
    regs.toArray()
  }

  private convertDocToAppRegistration(BasicDBObject doc) {
        Map<String,String> additionalAttributes =
          ['selectedLoadBalancersForVpcId': doc.getString('loadBalancer'),
           'internalnetwork' : doc.getString('internalNetwork'),
           'externalNetwork' : doc.getString('externalNetwork'),
           'instanceType' : doc.getString('instanceType'),
           'component' : doc.getString('componentName'),
           'app': doc.getString('appShortName')
          ]

    AppRegistration reg = new AppRegistration(
      doc.getString('name'),
      doc.getString('group'),
      doc.getString('type'),
      doc.getString('description'),
      doc.getString('owner'),
      doc.getString('email'),
      doc.getString('tags'),
      doc.getString('monitorBucketType'),
      doc.getString('createTs'),
      doc.getString('updateTs'),
      doc.getString('tenantId'),
                additionalAttributes,
                doc.get('securityGroups')
    )
    reg
  }

  // Add Cluster
  @Override
  public void UpdateApplicationCluster(String clustername, String asgname, String appname, String stackid, String stackName, String poolid, Object template) {
    // Add cluster
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.clustername", clustername)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)
    DBCursor cursor = applications.find(query);

    if (cursor.hasNext()) {
      BasicDBObject doc = cursor.next();

      def currentclustername = doc.get("clusters")
      Number clusterindex = 0
      currentclustername?.each { eachk ->
        String eachclname = eachk.clustername
        if (eachclname == clustername) {
          applications.update(doc, new BasicDBObject('$addToSet', new BasicDBObject(
            "clusters.${clusterindex}.asg", new BasicDBObject('asgname', asgname)
            .append('stackid', stackid)
            .append('poolid', poolid)
            .append('template', template))))
        }
        clusterindex = clusterindex + 1
      }
    } else {
      ////db.applications.update({_id:"abc"}, {$set: {clusters: [{clustername:"abc", asg:[{asgname:"abc", stackid:"1234"}, {asgname:"abc-v000", stackid:"2333"}]}]}})
      BasicDBObject query1 = new BasicDBObject()
      query1.put("name", appname)
      query1.put("tenantId", asgardUserContextService.currentTenant.access.token.tenant.id)
      BasicDBObject asg = new BasicDBObject('asgname', asgname)
        .append('stackid', stackid)
        .append('stackName', stackName)
        .append('poolid', poolid)
        .append('template', template)


      List<BasicDBObject> newasglist = []
      newasglist.add(asg)
      applications.update(query1, new BasicDBObject('$addToSet', new BasicDBObject(
        'clusters', new BasicDBObject('clustername', clustername)
        .append('asg', newasglist))))
    }

  }

  // Get NewAutoScalingGroupName via Mongodb
  @Override
  public String BuildNewAutoScalingGroupName(String clustername, String asgname, String appname) {
    // add cluster
    String NewASGName = asgname

    try {
      BasicDBObject query = new BasicDBObject()
      query.put("clusters.clustername", clustername)
      query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

      BasicDBList cllist = applications.distinct("clusters", query)
      //  Number cnt = cursor.count()
      for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
        BasicDBObject dbo = it.next();
        def currnetasgnamelist = dbo.get("asg")

        //Get Last ASG name
        String lastasgname = ''
        currnetasgnamelist?.each {eachk ->
          lastasgname = eachk.get("asgname")
        }
        if (lastasgname == '') {
          NewASGName = asgname
        } else {
          NewASGName = Relationships.buildNextAutoScalingGroupName(lastasgname)
        }
      }

      return NewASGName
    } catch (Exception ex) {
      log.info("BuildNewAutoScalingGroupName Error ${ex}")
      return asgname
    }
  }

  // Get cluster List
  @Override
  public ClusterMongodb[] getCluster() {
    //Getting Cluster name and AutoScalingGroup name from Mongodb
    List<ClusterMongodb> clusternames = []
    BasicDBObject queryForCurrentTenant = new BasicDBObject("tenantId", asgardUserContextService.currentTenant.access.token.tenant.id)
    BasicDBList cllist = applications.distinct("clusters", queryForCurrentTenant)

    for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
      BasicDBObject dbo = it.next();
      def currnetasgnamelist = dbo.get("asg")
      List<AutoScalingGroupMongodb> listst = []
      currnetasgnamelist?.each {  eachk ->
        listst.add(eachk)
      }
      def eachstack = new ClusterMongodb(clustername: dbo.getString('clustername'), asglist: listst)
      clusternames.add(eachstack)
      log.debug("eachstack ${eachstack.asglist}")
    }
    clusternames.toArray()
  }

  // Get One Cluster
  @Override
  public ClusterMongodb getCluster(String name) {
    //Getting Cluster name and AutoScalingGroup name from Mongodb
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.clustername", name)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

    BasicDBList cllist = applications.distinct("clusters", query)

    String clname  = ""
    List<AutoScalingGroupMongodb> listst = []

    for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
      BasicDBObject dbo = it.next();
      clname = dbo.getString('clustername')
      if (clname == name) {
        def currnetasgnamelist = dbo.get("asg")
        currnetasgnamelist?.each {eachk ->
          listst.add(eachk)
        }
      }
    }
    ClusterMongodb eachstack = new ClusterMongodb(clustername: clname, asglist: listst)
    return eachstack
  }

  // Check CheckAutoScalingGroup
  @Override
  public Boolean CheckAutoScalingGroup(String asgname) {
    //Getting Cluster name and AutoScalingGroup name from Mongodb
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.asgname", asgname)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)
    BasicDBList cllist = applications.distinct("clusters", query)

    for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
      BasicDBObject dbo = it.next();
      def currnetasgnamelist = dbo.get("asgname")
      List<String> listst = []
      currnetasgnamelist?.each { String eachk ->
        listst.add(eachk)
      }
      ClusterMongodb eachstack = new ClusterMongodb(clustername: dbo.getString('clustername'), asggroupnames: currnetasgnamelist)
      return eachstack
    }
  }


  // Get Poolid
  @Override
  public String getPoolid(String asgname) {
    String poolid = ""
    //Getting poolid from Mongodb
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.asg.asgname", asgname)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

    BasicDBList cllist = applications.distinct("clusters", query)

    for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
      BasicDBObject dbo = it.next();
      def currnetasgnamelist = dbo.get("asg")
      currnetasgnamelist?.each {eachk ->
        //poolid = eachk.get("poolid")
        if (eachk.get("asgname") == asgname) {
          poolid = eachk.get("poolid")
        }
      }
    }
    return poolid
  }

  // Get ApplicationName
  @Override
  public String getApplicationID(String asgname) {
    String appID = ""
    //Getting appID from Mongodb
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.asg.asgname", asgname)
    query.put("tenantId", asgardUserContextService.currentTenant.access.token.tenant.id)

    BasicDBList cllist = applications.distinct("name", query)
    if (cllist.size() > 0)
      appID = cllist[0].toString()

    return appID
  }


  // Get AutoScalingGroup from Mongodb
  @Override
  public AutoScalingGroupMongodb getAutoScalingGroup(String asgName) {
    AutoScalingGroupMongodb asg = new AutoScalingGroupMongodb()
    //Getting template from Mongodb
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.asg.asgname", asgName)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

    BasicDBList cllist = applications.distinct("clusters", query)
    List<HeatStack> stackList = []

    for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
      BasicDBObject dbo = it.next();
      def asgList = dbo.get("asg")
      asgList?.each {
        if (asgName.equalsIgnoreCase(it.get("asgname"))) {
          asg.template = it.get("template")
          asg.stackid = it.get("stackid")
          // TODO:Sachin - workaround till we migrate the code to use GORM
          if (it.get("stackName")) {
            asg.stackName = it.get("stackName")
          } else {
            if (asg.stackid != null) {
              if (stackList.isEmpty()) {
                stackList = heatService.getStackList()
              }
              stackList?.each { stack ->
                if ((asg.stackid).equalsIgnoreCase(stack.id)) {
                  asg.stackName = stack.stackName
                }
              }
            }
          }
          asg.poolid = it.get("poolid")
          asg.asgname = it.get("asgname")
        }
      }
    }
    return asg
  }


  @Override
  public String getStackid(String asgname) {
    String stackid = ""
    //Getting stackid from Mongodb
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.asg.asgname", asgname)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

    BasicDBList cllist = applications.distinct("clusters", query)

    for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
      BasicDBObject dbo = it.next();
      def currnetasgnamelist = dbo.get("asg")
      currnetasgnamelist?.each {eachk ->
        if (eachk.get("asgname") == asgname) {
          stackid = eachk.get("stackid")
        }
      }
    }
    return stackid
  }

  public String getclustername(String asgname) {
    String clname = ""
    //Getting stackid from Mongodb
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.asg.asgname", asgname)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

    BasicDBList cllist = applications.distinct("clusters", query)

    for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
      BasicDBObject dbo = it.next();
      def currnetasgnamelist = dbo.get("asg")
      currnetasgnamelist?.each {eachk ->
        if (eachk.get("asgname") == asgname) {
          clname = dbo.get("clustername")
        }
      }
    }
    return clname
  }

  @Override
  public void DeleteAutoScalingGroup(String asgname) {
    // Delete ASG
    String clustername= getclustername(asgname)
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.asg.asgname", asgname)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

    DBCursor cursor = applications.find(query);

    if (cursor.hasNext()) {
      BasicDBObject doc = cursor.next();

      def currentclustername = doc.get("clusters")

      Number clusterindex = 0
      currentclustername?.each {eachk ->
        String eachclname = eachk.clustername
        if (eachclname == clustername) {
          applications.update(doc, new BasicDBObject('$pull', new BasicDBObject(
            "clusters.${clusterindex}.asg", new BasicDBObject('asgname', asgname))))
          ///Implement Business logic: If a last ASG page is deleted from an application, then it should delete it's corresponding cluster
          CleanCluster(clustername)
        }
        clusterindex = clusterindex + 1
      }
    }
  }

  @Override
  public void CleanCluster(String clustername) {
    //Implement Business logic: If a last ASG page is deleted from an application, then it should delete it's corresponding cluster
    BasicDBObject query = new BasicDBObject()
    query.put("clusters.clustername", clustername)
    query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

    DBCursor cursor = applications.find(query);

    if (cursor.hasNext()) {
      BasicDBObject doc = cursor.next();

      def currentclustername = doc.get("clusters")
      currentclustername?.each {eachk ->
        String eachclname = eachk.clustername
        if (eachclname == clustername) {
          def asg = eachk.asg
          boolean  emptyasg = true
          asg?.each {eachasg ->
            emptyasg = false
          }
          if (emptyasg == true) {
            applications.update(doc, new BasicDBObject('$pull', new BasicDBObject(
              "clusters", new BasicDBObject('clustername', clustername))))
          }
        }
      }
    }
  }

  @Override
  public void UpdateASGTemplate( String asgname, Object template) {
    try {
      BasicDBObject query = new BasicDBObject()
      query.put("clusters.asg.asgname", asgname)
      query.put("tenantId",asgardUserContextService.currentTenant.access.token.tenant.id)

      DBCursor cursor = applications.find(query);

      if (cursor.hasNext()) {
        BasicDBObject doc = cursor.next();
        def asglist = doc.get("clusters")
        Number clusterindex = 0
        asglist?.each { eachk ->
          def eachasg = eachk.asg
          Number asgindex = 0
          eachasg?.each {eachasgvalue ->
            if (eachasgvalue.asgname == asgname) {
              //applications.update(doc, new BasicDBObject('$set', new BasicDBObject("asg.template", template)))
              applications.update(doc, new BasicDBObject('$set', new BasicDBObject(
                "clusters.${clusterindex}.asg.${asgindex}.template", template)))
            }
            asgindex = asgindex + 1
          }
          clusterindex = clusterindex + 1
        }
      }
    } catch (Exception ex) {
      log.info("Could not UpdateASGTemplate. Error: ${ex}")
    }


  }

//    // Get One Cluster
//    @Override
//    public void DeleteAutoScalingGroup(String asgname) {
//        //Getting Cluster name and AutoScalingGroup name from Mongodb
//        BasicDBObject query = new BasicDBObject("clusters.asgname", asgname)
//        BasicDBList cllist = applications.distinct("clusters", query)
//
//        for (Iterator<Object> it = cllist.iterator(); it.hasNext();) {
//            BasicDBObject dbo = it.next();
//            def currnetasgnamelist = dbo.get("asgname")
//            List<String> listst = []
//            currnetasgnamelist?.each { String eachk ->
//                listst.add(eachk)
//            }
//            ClusterMongodb eachstack = new ClusterMongodb(clustername: dbo.getString('clustername'), asggroupnames: currnetasgnamelist)
//            return eachstack
//        }
//    }
}
