package com.cluster
import org.bson.types.ObjectId

/**
 * Created by sdeokar on 3/20/2015.
 */
class Stack {
    ObjectId id
    String stackId
    String stackName

    static constraints = {
        stackId blank: false, nullable: false
        stackName blank: false, nullable: false
    }
}
