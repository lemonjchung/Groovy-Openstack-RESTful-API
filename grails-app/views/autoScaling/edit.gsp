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
<%@ page import="com.netflix.asgard.model.AutoScalingProcessType;" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="main"/>
    <title>Edit Auto Scaling Group</title>
</head>
<body>
<div class="body">
    <h1>Edit Auto Scaling Group</h1>
    <g:if test="${flash.message}">
        <div class="message">${flash.message}</div>
    </g:if>
    <g:hasErrors bean="${asgDetail}">
        <div class="errors">
            <g:renderErrors bean="${asgDetail}" as="list"/>
        </div>
    </g:hasErrors>
    <g:form method="post">
        <input type="hidden" id="name" name="name" value="${asgName}"/>
        <input type="hidden" name="id" value="${stack?.id?.toString()}"/>
        <div class="dialog">
            <table>
                <tbody>
                <tr class="prop" title="Changing the name requires a delete and re-create. This will fail if there are instances running.">
                    <td class="name">Name:</td>
                    <td class="value"><g:linkObject type="autoScaling" name="${asgName}"/></td>
                </tr>
                <tr class="prop">
                    <td class="name">stackID
                        Instance<br/>Bounds:
                    </td>
                    <td class="value numbers">
                        <label for="min">Min per Availability Zone:</label> <input type="number" class="number" id="min" name="min" max="99" required="required" value="${minSize}"/>
                    </td>
                </tr>
                <tr class="prop">
                    <td class="name" />
                    <td class="value numbers">
                        <label for="max">Max per Availability Zone:</label> <input type="number" class="number" id="max" name="max" max="99" required="required" value="${maxSize}"/>
                    </td>
                </tr>
                <tr class="prop">
                    <td class="name">
                        Desired<br/>Capacity:
                    </td>
                    <td class="numbers">
                        <div class="manualDesiredCapacityshowManual">
                            <input type="number" class="number" id="desiredCapacity" name="desiredCapacity" max="99" required="required" value="${desiredCapacity}" /> instances per Availability Zone
                        </div>
                    </td>
                </tr>
                <tr class="prop advanced" title="The number of seconds after a scaling activity completes before any further scaling activities can start">
                    <td class="name">
                        <label for="coolDown">Cooldown:</label>
                    </td>
                    <td class="value">
                        <input type="number" class="number" id="coolDown" name="coolDown" required="required" value="${cooldown == null ? '10' : cooldown}"/> seconds
                    </td>
                </tr>
                </tbody>
            </table>
        </div>
        <div class="buttons">
            <g:buttonSubmit class="save" value="Update Auto Scaling Group" action="update"/>
        </div>
    </g:form>
</div>
</body>
</html>
