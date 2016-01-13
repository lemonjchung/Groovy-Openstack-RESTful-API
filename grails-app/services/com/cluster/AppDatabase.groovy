package com.cluster
import com.netflix.asgard.AppRegistration
import com.netflix.asgard.Application
import com.netflix.asgard.ClusterMongodb
import com.netflix.asgard.AutoScalingGroupMongodb
import com.netflix.asgard.model.MonitorBucketType;

interface AppDatabase {
    void createApplication(Application application, MonitorBucketType monitorBucketType, String createTs, String updateTs)
    void updateApplication(Application application, MonitorBucketType monitorBucketType, String updateTs)
    void deleteApplication(String name)

    Application getApplicationDetails(String appName)
    AppRegistration getApplication(String appName)
    AppRegistration[] getAllApplications()
    //Add for cluster page
    void UpdateApplicationCluster(String clustername, String asgname, String appname, String stackid, String stackName, String poolid, Object template)
    ClusterMongodb[] getCluster()

  ClusterMongodb getCluster(String clustername)
    String BuildNewAutoScalingGroupName(String clustername, String asgname, String appname)
    String getPoolid(String asgname)

    AutoScalingGroupMongodb getAutoScalingGroup(String asgname)
    String getStackid(String asgname)
    void DeleteAutoScalingGroup(String asgname)
    void CleanCluster(String clustername)
    void UpdateASGTemplate(String asgname, Object template)

}
