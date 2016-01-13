package com.cluster
import com.serve.asgard.model.FloatingIp
import com.serve.asgard.model.Instance
import com.serve.asgard.model.LoadBalancerPoolMember

class InstanceService {
    def computeService
    // def asyncComputeService
    def networkService

  Instance create(Instance instance) {
    Instance newInstance
    FloatingIp floatingIp

    newInstance = computeService.createInstance(instance)

    if (newInstance && newInstance.instanceId) {
      //def newInstanceId = newInstance.instanceId
      while (!newInstance.networks || !newInstance.getStatus() || !newInstance.getStatus().equalsIgnoreCase('ACTIVE') || !newInstance.getPowerStatus() || !newInstance.getPowerStatus()?.equalsIgnoreCase('Running')) {
        Thread.sleep(3000)
        newInstance = computeService.getInstanceById(newInstance.instanceId)
      }

      newInstance.networks.each { network ->
        if (!network.pool.endsWith('network-ext')) {
          //check if it's not an external network. If yes, convert to ext network since the floating IP has to be associated with ext network
          def floatingIpNetworkName
          def networkToken = network.pool.split('-')
          // last characters after network- needs to be replaced with ext
          networkToken[networkToken.length - 1] = 'ext'
          floatingIpNetworkName = networkToken.join('-')
          floatingIp = computeService.createFloatingIP(floatingIpNetworkName)
          computeService.associateFloatingIp(newInstance.instanceId, floatingIp.floatingIpAddress)
        }
      }

      if (instance.loadBalancerIds) {
        newInstance.networks.each { network ->
          // register external ip with teh load balancer
            instance.loadBalancerIds.each { loadBalancerId ->
              LoadBalancerPoolMember member = new LoadBalancerPoolMember()
              member.poolId = loadBalancerId
              member.address = floatingIp?.floatingIpAddress
              member.protocolPort = '443'
              networkService.associateLoadBalancerMember(member)
            }
          }
      }


      return computeService.getInstanceById(newInstance.instanceId)
    } else {
      return newInstance
    }
    }

}
