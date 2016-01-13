package com.netflix.asgard

class ASGList {

    String name
    String clustername
    String application
    String creationTime
    String updatedTime
    String status
    String instancessize
    String minsize
    String maxsize
    String desiredCapacity
    ArrayList<InstanceMongodb> instances
    Date asglistupdatetime

}
