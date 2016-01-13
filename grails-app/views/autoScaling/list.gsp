<%@ page import="com.serve.asgard.openstack.OpenStackAutoScalingService" %>
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
    <script>

    </script>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="main"/>
    <title>Auto Scaling Groups</title>
</head>
<body>
<div class="body">
    <h1>Auto Scaling Groups in ${region.description}${appNames ? ' for ' + appNames : ''}</h1>
    <g:if test="${flash.message}">
        <div class="message">${flash.message}</div>
    </g:if>
    <g:form method="post">
        <div class="list">
            <div class="buttons">
                <g:link class="create" action="create">Create New Auto Scaling Group</g:link>
            </div>
            <table class="sortable">
                <thead>
                <tr>
                    <th>Group Name</th>
                    <th>Cluster</th>
                    <th>Application Name</th>
                    <th>Created Time</th>
                    <th>Min</th>
                    <th>Max</th>
                    <th>Des</th>
                    <th>Status</th>
                    <th>Instances</th>
                </tr>
                </thead>
                <tbody>
                <g:each in="${autoScalingGroups}" status="i" var="group">
                    <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
                        <td class="autoScaling"><g:linkObject type="autoScaling" id="${group.stackName}" params="['groupName': group.stackName]" name="${group.stackName}"/></td>
                        <g:set var="clusterName" value="${stackNamesToClusterNames[group.stackName]}"/>
                        <g:if test="${clusterName?.length() > 0}">
                            <td class="cluster"><g:linkObject type="cluster" name="${clusterName}"/></td>
                        </g:if>
                        <g:else>
                            <td></td>
                        </g:else>
                        <g:set var="appName" value="${stackNamesToAppNames[group.stackName]}"/>
                        <g:if test="${appName?.length() > 0}">
                            <td class="app"><g:linkObject type="application" name="${appName}"/></td>
                        </g:if>
                        <g:else>
                            <td></td>
                        </g:else>
                        <td>${group.creationTime}</td>
                        <td>${minNumberOfInstances[group.stackName]}</td>
                        <td>${maxNumberOfInstances[group.stackName]}</td>
                        <td>${desNumberOfInstances[group.stackName]}</td>
                        <td>${group.stackStatus}</td>
                        <td class="countAndList hideAdvancedItems">
                            <g:set var="instances" value="${stackNamesToInstances[group.stackName]}"/>
                            <span class="toggle fakeLink">${instances.size()}</span>
                            <div class="advancedItems tiny">
                                <g:if test="${instances.size() > 0}">
                                    <g:each var="ins" in="${instances}">
                                        <g:if test="${ins}">
                                            <g:linkObject type="instance" name="${ins.instacename}" id="${ins.instanceid}"/><br/>
                                        </g:if>
                                    </g:each>
                                </g:if>
                            </div>
                        </td>
                    </tr>
                </g:each>
                </tbody>
            </table>
        </div>
        <footer/>
    </g:form>
</div>
</body>
</html>
