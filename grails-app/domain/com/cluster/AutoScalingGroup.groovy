package com.cluster
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.bson.types.ObjectId


@ToString(includeNames = true, includeFields = true)
@EqualsAndHashCode
class AutoScalingGroup {
  ObjectId id
  String name
  Stack stack

  static mapWith = "mongo"

  static constraints = {
    name blank: false, nullable: false
    stack unique: true, nullable: true
  }


}
