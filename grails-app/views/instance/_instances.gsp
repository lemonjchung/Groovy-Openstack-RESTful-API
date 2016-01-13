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
<div class="list">
    ${body()}
    <table class="sortable">
        <thead>
        <tr>
            <!--<th>&thinsp;x</th> -->
            <g:if test="${discoveryExists}">
                <th>Application</th>
            </g:if>
            <th>Auto Scaling Group</th>
            <th>Instance Name</th>
            <th><g:if test="${discoveryExists}">VIP & </g:if>Image Name</th>
            <th>IP Address</th>
            <th>Size</th>
            <th>Status</th>
            <th>Availability Zone</th>
            <th>Task</th>
            <th>Power State</th>
            <th>Launch Time</th>
        </tr>
        </thead>
        <tbody>
        <g:each var="mi" in="${instanceList}" status="i">
            <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
                <!--<td><g:if test="${mi.id}"><g:checkBox name="selectedInstances" value="${mi.id}" checked="0" class="requireLogin"/></g:if></td>-->
                <g:if test="${discoveryExists}">
                    <td class="app"><g:linkObject type="application" name="${appNames}"/></td>
                </g:if>
                <td class="autoScaling">
                    <g:if test="${asgnamelist[mi.id]}">
                        <g:linkObject type="autoScaling" id="${asgnamelist[mi.id]}" name="${asgnamelist[mi.id]}"/>
                    </g:if>
                    <g:else>
                        No ASG
                    </g:else>
                </td>

          <td>
          <g:if test="${mi.get("OS-EXT-STS:task_state")?.toUpperCase().equals("DELETING")}">
            <b>${mi.name}</b>
          </g:if>

          <g:else>
            <g:linkObject type="instance" name="${mi.name}" id="${mi.id}"
                          params="[relatedasgname: asgnamelist[mi.id], relatedasgID: asgIDlist[mi.id]]"/></td>
          </g:else>
          </td>
                <td>
                    <g:if test="${mi.image}">
                        <g:linkObject type="image" name="${imagenamelist[mi.image.id]}" id="${mi.image.id}" /><br/>
                    </g:if>
                </td>
                <td>
                    <g:each var="tag" in="${mi.addresses}">
                        <g:each var="tag2" in="${tag.value}">
                            ${tag2.addr} <br>
                        </g:each>
                    </g:each>
                </td>
                <td>
                    <g:if test="${mi.flavor}">
                        <g:each var="tag" in="${instnacetypelist.find { it.id == mi.flavor.id}}">
                            ${tag.name} | ${tag.memory}MB RAM | ${tag.vcpu} VCPUS | ${tag.disk}GB DISK<br/>
                        </g:each>
                    </g:if>
                </td>
                <td>${mi.get("OS-EXT-STS:vm_state").toUpperCase()} </td>
                <td>
                    ${mi.get("OS-EXT-AZ:availability_zone")}
                </td>
          <td>${mi.get("OS-EXT-STS:task_state")?.toUpperCase()}</td>
                <td>
                   <g:if test="${mi.get("OS-EXT-STS:power_state") == 1 }" >
                     Running
                   </g:if>
                   <g:else>
                     No State
                   </g:else>
                </td>
                <td>${mi.created}</td>
            </td>
        </tr>
        </g:each>
        </tbody>
    </table>
</div>
