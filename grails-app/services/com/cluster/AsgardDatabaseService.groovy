package com.cluster
//import grails.transaction.Transactional
import org.springframework.beans.factory.InitializingBean

//@Transactional
class AsgardDatabaseService implements InitializingBean {

  def mongo

  void afterPropertiesSet() {
  }

  def createApplication(Application application) {
    application.validate()
    application.save([failOnError: true, flush: true])
  }

  def getApplications() {

    def applicationList = []
    try {
      applicationList = Application.list()
    } catch (Exception e) {
      // e.printStackTrace()
      log.debug(e.getMessage() + " ~~~~~ " + e.getLocalizedMessage())
    }
    applicationList.each { println it }
    return applicationList
  }
}
