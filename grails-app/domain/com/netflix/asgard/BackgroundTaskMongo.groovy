package com.netflix.asgard

class BackgroundTaskMongo {
    static mapWith="mongo"
    static mapping = {
        collection "backgroundtask"
        database "applications"
    }

    String name
    Date updatetime

    static constraints = {
        name nullable: true
        updatetime nullable: true
    }
}


