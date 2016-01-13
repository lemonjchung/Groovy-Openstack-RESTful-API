
<!--
<div id="myform">
   <div>${now}</div>
</div>
-->


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
    <h1 color="#ff658e">Cluster Name: ${clustername}</h1>

    <table class="sortable">
        <thead>
        <tr>
            <th>ASG Name</th>
            <th>Status</th>
            <th>Create Time</th>
            <th>Update Time</th>
            <th>Description</th>
            <th>Create Time</th>

        </tr>
        </thead>
        <tbody>
        <g:each var="mi" in="${clusters}" status="i">
            <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
                <td>${mi.name}</td>
                <td>${mi.status}</td>
                <td>${mi.creationTime}</td>
                <td>${mi.updatedTime}</td>
                <td>${mi.description}</td>
                <td>${mi.stackStatusReason}</td>
            </tr>
         </g:each>
        </tbody>
    </table>
</div>
