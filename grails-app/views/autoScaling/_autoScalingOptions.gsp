<%--

    Copyright 2012 Netflix, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

--%>
<g:javascript>
    function modifyMax() {
        var min = document.getElementById("min");
        var maximum = document.getElementById("max");
        maximum.setAttribute("min",min.value);
    }
</g:javascript>
<%@ page import="com.netflix.asgard.model.AutoScalingGroupHealthCheckType" %>
<g:if test="${flash.message}">
    <div class="message">${flash.message}</div>
</g:if>
<g:hasErrors bean="${cmd}">
    <div class="errors">
        <g:renderErrors bean="${cmd}" as="list"/>
    </div>
</g:hasErrors>
<tr class="prop">
    <td class="name">
        Instance<br/>Bounds:
    </td>
    <td class="value">
        <label for="min">Min per Availability zone:</label> <input type="number" class="number" id="min" name="min" required="required"
                                                                   value="${minSize == null ? 1 : minSize}" min="1"
                                                                   onchange="modifyMax()" onblur="validateNumber(event)" max="999" />
    </td>
</tr>
<tr>
  <td class="name"></td>
    <td>
        <label for="max">Max per Availability zone:</label> <input type="number" class="number" id="max" name="max" required="required"
                                                                   value="${maxSize == null ? 3 : maxSize}" min="1" max="999" />
    </td>
</tr>
<tr class="prop">
    <td class="name">
        Desired<br/>Capacity per Availability zone:
    </td>
    <td class="value numbers">
        <div class=" desiredCapacityContainer ${manualStaticSizingNeeded ? 'showManual' : ''}">
            <div class="dynamicDesiredCapacity">
                <input type="number" class="number" id="desiredCapacity" name="desiredCapacity" min="1" required="required"
                       value="${desiredCapacity == null ? 1 : desiredCapacity}"/> instances
            </div>
        </div>
    </td>
</tr>
<tr class="prop advanced" title="The number of seconds after a scaling activity completes before any further scaling activities can start">
    <td class="name">
        <label for="defaultCooldown">Cooldown:</label>
    </td>
    <td class="value">
        <input type="number" class="number" id="defaultCooldown" name="defaultCooldown" required="required" min="1"
               value="${group?.template?.getASG()?.properties?.cooldown == null ? '10' : '10'}"/> seconds
    </td>
</tr>
<tr class="prop advanced">
    <td colspan="2">
        <h3>Alarm Action</h3>
    </td>
</tr>
<tr class="prop advanced" title="The number of seconds CPU threshold will be monitored before scaling up.">
    <td class="name">
        <label for="ScaleUpPeriod">Scale Up <br/>Period: </label>
    </td>
    <td class="value">
        <input type="number" class="number" id="ScaleUpPeriod" name="detail" required="required" min="1"
               value='60'/> seconds
    </td>
</tr>
<tr class="prop advanced" title="The percentage of CPU Utilization that has to be achieved before scaling up.">
    <td class="name">
        <label for="ScaleUpThreshold">Scale Up <br/>CPU Threshold: </label>
    </td>
    <td class="value">
        <input type="number" class="number" id="ScaleUpThreshold" name="ScaleUpThreshold" required="required" required="required"
               value='60'/> %
    </td>
</tr>
<tr class="prop advanced" title="The number of instances that has to be added when threshold has reached">
    <td class="name">
        <label for="ScalingUpAdjustment">Scale Up <br/>Adjustment: </label>
    </td>
    <td class="value">
        <input type="number" class="number" id="ScalingUpAdjustment" name="ScalingUpAdjustment" required="required" min="1"
               value='1'/> instance(s)
    </td>
</tr>
<tr class="prop advanced" title="">
    <td class="name">
        <label for="ScaleDownPeriod">Scale Down <br/>Period:</label>
    </td>
    <td class="value">
        <input type="number" class="number" id="ScaleDownPeriod" name="ScaleDownPeriod" required="required" min="1"
               value='600'/> seconds
    </td>
</tr>
<tr class="prop advanced" title="">
    <td class="name">
        <label for="ScaleDownThreshold">Scale Down <br/>CPU Threshold:</label>
    </td>
    <td class="value">
        <input type="number" class="number" id="ScaleDownThreshold" name="ScaleDownThreshold" required="required" min="1"
               value='20'/> %
    </td>
</tr>
<tr class="prop advanced" title="">
    <td class="name">
        <label for="ScalingDownAdjustment">Scale Down <br/>Adjustment:</label>
    </td>
    <td class="value">
        <input type="number" class="number" required="required" id="ScalingDownAdjustment" name="ScalingDownAdjustment" required="required" min="1"
               value='1'/> instance(s)
    </td>
</tr>
<tr class="prop advanced">
    <td colspan="2">
        <h3>Load Balancer</h3>
    </td>
</tr>
%{--
<tr class="prop advanced" title="The method that the group will use to decide when to replace a problematic instance">
  <td class="name">
    <label for="healthCheckType">ASG Health<br/>Check Type:</label>
  </td>
  <td class="value">
    <select id="healthCheckType" name="healthCheckType">
      <g:each in="${AutoScalingGroupHealthCheckType.values()}" var="type">
        <option ${group?.healthCheckType?.toString() == type.name() ? 'selected' : ''} value="${type.name()}">${type.name()} (${type.description})</option>
      </g:each>
    </select>
  </td>
</tr>
<tr class="prop advanced" title="The number of seconds to wait after instance launch before running the health check">
  <td class="name">
    <label for="healthCheckGracePeriod">ASG Health<br/>Check Grace<br/>Period:</label>
  </td>
  <td class="value">
    <input type="text" class="number" id="healthCheckGracePeriod" name="healthCheckGracePeriod" value="${group?.healthCheckGracePeriod == null ? '600' : group?.healthCheckGracePeriod}"/> seconds
  </td>
</tr>
<tr class="prop advanced" title="The algorithm to use when selecting which instance to terminate">
  <td class="name">
    <label for="terminationPolicy">Termination<br/>Policy:</label>
  </td>
  <td>
    <select id="terminationPolicy" name="terminationPolicy">
      <g:each in="${allTerminationPolicies}" var="policy">
        <option ${terminationPolicy == policy ? 'selected' : ''} value="${policy}">${policy}</option>
      </g:each>
    </select>
  </td>
</tr>--}%
%{--<g:if test="${!subnetPurpose && vpcZoneIdentifier}">
  <td class="name">VPC:</td>
  <td class="warning">The subnet is misconfigured without a purpose.</td>
</g:if>
<g:else>
  <g:render template="/common/vpcSelection" model="[awsAction: 'Launch', awsObject: 'instances']"/>
  <g:render template="/common/zoneSelection" />
</g:else>
<tr class="prop advanced">
  <td class="name">
    AZ Rebalancing:
  </td>
  <td>
    <input type="radio" name="azRebalance" value="enabled" id="azRebalanceEnabled" ${group?.zoneRebalancingSuspended ?  '' : 'checked="checked"'}>
    <label for="azRebalanceEnabled" class="choice">Keep Zones Balanced</label><br/>
    <input type="radio" name="azRebalance" value="disabled" id="azRebalanceDisabled" ${group?.zoneRebalancingSuspended ? 'checked="checked"' : ''}>
    <label for="azRebalanceDisabled" class="choice">Don't Rebalance Zones</label>
  </td>--}%
</tr>
