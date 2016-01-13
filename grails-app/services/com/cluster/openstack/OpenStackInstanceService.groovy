package com.serve.asgard.openstack

import org.springframework.beans.factory.InitializingBean

class OpenStackInstanceService implements InitializingBean {
  def asgardUserContextService
  def openStackRestService
  String openStackHeatCreateStackEndpoint

  @Override
  void afterPropertiesSet() throws Exception {
  }

  public String getASGNamefromInstance(String instanceid, Map<String, String> InstanceMeta ) {
      String asgnm = ""
      try {
          def asggroupname = InstanceMeta.find { it.key == "metering.AutoScalingGroupName" }?.value
        if (asggroupname && asggroupname?.size() > 0) {
            if (asggroupname.indexOf("_", 0) > 0) {
              Number asgint = asggroupname.lastIndexOf("_")
              asgnm = asggroupname.substring(0, asgint)
            }
          }
      } catch (Exception ex) {
          log.warn("getASGNamefromInstance Error: ${ex}")
      }
      finally {
          return asgnm
      }
  }
}
