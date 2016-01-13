package com.cluster
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.bson.types.ObjectId

/**
 * Created by sdeokar on 9/23/2015.
 */
@ToString(includeNames = true, includeFields = true)
@EqualsAndHashCode
class Cluster {

  ObjectId id
  String name
  List<AutoScalingGroup> autoScalingGroups

  static embedded = ['autoScalingGroups']

  static mapWith = "mongo"

  static constraints = {
    name blank: false, nullable: false
    autoScalingGroups nulable: true
  }
}
