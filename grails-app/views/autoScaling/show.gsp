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
<%@ page import="com.netflix.asgard.AwsAutoScalingService; java.text.NumberFormat" %>
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <meta name="layout" content="main"/>
  <title>${asgName} Auto Scaling Group</title>
</head>
<body>
  <div class="body">
    <h1>Auto Scaling Group Details</h1>
    <g:if test="${flash.message}">
      <div class="message">${flash.message}</div>
    </g:if>
    <g:set var="autoScalingGroupName" value="${asgName}"/>
    <g:set var="autoScalingGroupID" value="${asgName}"/>
    <g:form>
      <div class="buttons">
        <input type="hidden" name="name" value="${autoScalingGroupName}"/>
        <input type="hidden" name="id" value="${autoScalingGroupID}"/>
         <g:link class="edit" action="edit" params="[id:autoScalingGroupID, name:autoScalingGroupName]">Edit Auto Scaling Group</g:link>
         <g:buttonSubmit class="delete" data-warning="Really delete Auto Scaling Group '${autoScalingGroupName}'?"
                         action="delete">Delete Auto Scaling Group</g:buttonSubmit>
      </div>
    </g:form>
    <div>
      <table>
        <tbody>
        <tr class="prop">
          <td class="name">Auto Scaling Group:</td>
          <td class="value">${autoScalingGroupName}</td>
        </tr>
        <tr class="prop">
          <td class="name">ID:</td>
          <td class="value">${autoScalingGroupID}</td>
        </tr>
        <tr class="prop">
          <td class="name">Description:</td>
          <td class="value">${stack.description}</td>
        </tr>
        <tr class="prop">
          <td class="name">Status:</td>
          <td class="value">${stack.stackStatus}</td>
        </tr>
        <tr class="prop">
          <td class="name">Status Reason:</td>
          <td class="value">${stack.stackStatusReason}</td>
        </tr>
        <tr class="prop">
          <td class="name">Cluster:</td>
          <td class="value"><g:linkObject type="cluster" name="${clusterName}"/></td>
        </tr>
        <tr class="prop">
          <td class="name">Application:</td>
          <td class="value"><g:linkObject type="application" name="${applicationName}"/></td>
        </tr>
        <tr class="prop">
          <td class="name">Instance Bounds:</td>
          <td class="value subProperties"><label>Min</label> ${minSize} <label>Max</label> ${maxSize}</td>
        </tr>
        <tr class="prop">
          <td class="name">Desired Size:</td>
          <td class="value subProperties">${desiredCapacity} <label>instance${desiredCapacity == 1 ? '' : 's'}</label>
          </td>
        </tr>
        <tr class="prop" title="The number of seconds after a scaling activity completes before any further scaling activities can start">
          <td class="name">Cooldown:</td>
            <td class="value">${cooldown} second${cooldown == 1 ? '' : 's'}</td>
        </tr>
        <tr class="prop">
          <td class="name">Image:</td>
          <td class="value">
            <g:if test="${image}">
              <g:linkObject type="image" name="${image.name}" id="${image.id}"/>
            </g:if>
          </td>
        </tr>
        %{--<tr class="prop">
          <td class="name">SSH Key:</td>
          <td class="value">${asgDetail.sshKey}</td>
        </tr>--}%

        <td class="name">Security Groups:</td>
        <td class="value">
          <g:set var="secGroupCount" value="${0}"/>
          <g:each var="securityGroup" in="${securityGroups}">
          %{--<div><g:securityGroup group="${securityGroup}"/></div>--}%
            <g:if test="${secGroupCount > 0}">,</g:if>
            ${securityGroup.groupName}
            <g:set var="secGroupCount" value="${secGroupCount + 1}"/>
          </g:each>
        </td>
        </tr>
        <tr class="prop">
          <td class="name">Created Time:</td>
          <td class="value">${createdTime}</td>
        </tr>
        <tr class="prop">
          <td class="name">Last Updated Time:</td>
          <td class="value">${lastUpdatedTime}</td>
        </tr>
        <tr class="prop">
          <td class="name">Load Balancer:</td>
          <td class="value">
            <table>
                <tr class="prop">
                  <td><g:linkObject type="loadBalancer" name="${loadBalancer.name}" id="${loadBalancer.id}"/></td>
                </tr>
            </table>
          </td>
        </tr>
        <tr class="prop">
          <td class="name">Availability Zones:</td>
          <td class="value">
            <table>
              <g:each var="zone" in="${asgInstancesPerAZ}">
                <tr class="prop">
                  <td class="name"><b>${zone.key}</b></td>
                <tr class="prop">
                  <td class="name">Instances:</td>
                  <td class="value">
                    <table>
                      <g:each var="instance" in="${zone.value}">
                        <tr class="prop">
                          <td><g:linkObject type="instance" name="${instance.name}" id="${instance.instanceId}" params="[relatedasgname:autoScalingGroupID, relatedasgID:autoScalingGroupName]" /></td>
                        </tr>
                      </g:each>
                    </table>
                  </td>
                </tr>
                </tr>
              </g:each>
            </table>
          </td>
        </tr>

        <tr class="prop advanced">
            <td colspan="2">
                <h3>Alarm Action</h3>
            </td>
        </tr>
        <tr class="alarm" title="">
            <td class="name">Meter Name: </td>
            <td class="value"> ${metername}</td>
        </tr>
        <tr class="alarm" title="">
            <td class="name">Scale Up <br/>Period: </td>
            <td class="value"> ${scaleupperiod} seconds</td>
        </tr>
        <tr class="alarm" title="">
            <td class="name">Scale Up <br/>CPU Threshold: </td>
            <td class="value"> ${scaleupthreshold} %</td>
        </tr>
        <tr class="alarm" title="">
            <td class="name"><label >Scale Up <br/>Adjustment: </td>
            <td class="value"> ${scalingUpadjustment} instance(s)</td>
        </tr>
        <tr class="alarm" title="">
            <td class="name">Scale Down <br/>Period:/td>
            <td class="value"> ${scaledownperiod} seconds </td>
        </tr>
        <tr class="alarm" title="">
            <td class="name">Scale Down <br/>CPU Threshold:</td>
            <td class="value"> ${scaledownthreshold}% </td>
        </tr>
        <tr class="alarm" title="">
            <td class="name">Scale Down <br/>Adjustment:</td>
            <td class="value"> ${scalingDownadjustment} instance(s)</td>
        </tr>

        </tbody>
       </table>
  </div>
</body>
</html>
