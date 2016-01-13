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
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="main"/>
    <title>${appName} ${instance?.id} Instance</title>
</head>
<body>
<div class="body">
    <h1>Instance Details</h1>
    <g:if test="${flash.message}">
        <div class="message">${flash.message}</div>
    </g:if>
    <g:form>
    <input type="hidden" name="id" value="${instance?.id}"/>
    <input type="hidden" name="asgName" value="${RelatedASGname}"/>
    <input type="hidden" name="asgID" value="${RelatedASGID}"/>
    <g:if test="${RelatedASGname == ''}">
        <div class="buttons">
            <h3>Operating System:</h3>
            <g:buttonSubmit class="stop" action="terminate" value="Terminate Instance" title="Shut down and delete this instance." />
            <g:buttonSubmit class="shutdown" action="reboot" value="Reboot Instance" title="Restart the OS of the instance." />
        </div>
    </g:if>
    <g:else>
        <div class="buttons">
            <h3>Auto Scaling Group:</h3>
            <g:buttonSubmit class="stop" action="terminateAndShrinkGroup" value="Configure ASG" title="Configure ASG." />
        </div>
        <div class="buttons">
            <h3>Operating System:</h3>
            <g:buttonSubmit class="shutdown" action="reboot" value="Reboot Instance" title="Restart the OS of the instance." />
        </div>
    </g:else>

    <g:hasErrors bean="${cmd}">
        <div class="errors">
            <g:renderErrors bean="${cmd}" as="list"/>
        </div>
    </g:hasErrors>
    <div class="dialog">
        <tbody>
        <h3>Info</h3>
        <table>
            <tr class="prop">
                <td class="name" style='font-weight:bold;'>Name:</td>
                <td class="value">${instance.name}</td>
            </tr>
            <tr class="prop">
                <td class="name" style='font-weight:bold;'>ID:</td>
                <td class="value">${instance.id}</td>
            </tr>
            <tr class="prop">
                <td class="name" style='font-weight:bold;'>Status:</td>
                <td class="value">${instance.status}</td>
            </tr>
            <tr class="prop">
                <td class="name" style='font-weight:bold;'>Availability Zone:</td>
                <td class="value">${instance.getAvailabilityZone()}</td>
            </tr>
            <tr class="prop">
                <td class="name" style='font-weight:bold;'>Created:</td>
                <td class="value">${instance.getCreated()}</td>
            </tr>
            <tr class="prop">
                <td class="name" style='font-weight:bold;'>Updated:</td>
                <td class="value">${instance.getUpdated()}</td>
            </tr>

        </table>

        <h3>Spec</h3>
        <table>
            <g:if test="${instance.flavor}">
                <g:each var="tag" in="${instance.flavor}">
                    <tr class="prop">
                        <td class="name" style='font-weight:bold;'>Flavor:</td>
                        <td class="value"> ${tag.name}</td>
                    </tr>
                    <tr class="prop">
                        <td class="name" style='font-weight:bold;'>RAM:</td>
                        <td class="value">${tag.ram}MB RAM</td>
                    </tr>
                    <tr class="prop">
                        <td class="name" style='font-weight:bold;'>VCPUs:</td>
                        <td class="value">${tag.vcpus} VCPUS</td>
                    </tr>
                    <tr class="prop">
                        <td class="name" style='font-weight:bold;'>Disk:</td>
                        <td class="value">${tag.disk}GB DISK</td>
                    </tr>

                </g:each>
            </g:if>
        </table>

        <h3>IP Address</h3>
        <table>
            <g:each var="tag" in="${instance.getAddresses()}">
                <tr class="prop">
                    <g:each var="tag2" in="${tag.getAddresses()}">
                        <td class="name" style='font-weight:bold;'>${tag2.key}:</td>
                        <td class="value">
                        <g:each var="tag1" in="${tag.getAddresses(tag2.key)}">
                            <g:if test="${tag1.getType() == "floating"}">
                                <input type="hidden" name="floatingip" value="${tag1.getAddr()}"/>
                            </g:if>
                             ${tag1.getAddr()}
                        </g:each>
                        </td>
                    </g:each>

                </tr>
            </g:each>
        </table>

        <h3>Security Groups</h3>
        <table>
            <tr class="prop">
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
        </table>

        <h3>Meta</h3>
        <table>
            <tr class="prop">
                <td class="name" style='font-weight:bold;'>Key Name:</td>
                <td class="value">${instance.getKeyName()}</td>
            </tr>

            <tr class="prop">
                <g:if test="${instance.image}">
                    <g:each var="tag" in="${instance.image}">
                        <td class="name" style='font-weight:bold;'>Image Name:</td>
                        <td class="value">
                            <g:linkObject type="image" name="${tag.name}" id="${tag.id}"  /></td>
                    </g:each>
                </g:if>
            </tr>
            <g:each var="tag" in="${instance.getMetadata()}">
                <tr class="prop">
                    <td class="name" style='font-weight:bold;'>${tag.key}: </td>
                    <td class="value">${tag.value}</td>
                </tr>
            </g:each>

        </table>

        </tbody>
    </div>
</div>

    </g:form>
</body>
</html>
