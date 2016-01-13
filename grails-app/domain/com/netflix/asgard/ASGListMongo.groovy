package com.netflix.asgard

class ASGListMongo {
    static mapWith="mongo"
    static mapping = {
        collection "ASGList"
        database "applications"
    }

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

    static constraints = {
        clustername nullable: true
        application nullable: true
        creationTime nullable: true
        updatedTime nullable: true
        status nullable: true
        instancessize nullable: true
        instances nullable: true
        minsize nullable: true
        maxsize nullable: true
        desiredCapacity nullable: true
        asglistupdatetime nullable: true
    }
}


