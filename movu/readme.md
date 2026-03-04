The specifications and information regarding the products in this manual are subject to change without notice. All statements,
information, and recommendations in this manual are believed to be accurate but are presented without warranty of any kind,
express or implied. Users are responsible for the application of the products
Your use of this document and any associated product is subject to the terms and conditions of the agreement that is in place
between you and Movu Robotics or any of its affiliates. Should there be no agreement in place, then you are not authorized to use
this document or the associated product.
The Movu name together with any associated logo are a registered trademark. You may not make use of them without the specific
permission of Movu Robotics NV or its affiliates. Movu Robotics NV and its affiliates reserve all its rights in respect to the use of its
branding.
2023 Movu Robotics NV. All rights reserved.

Movu Robotics NV
Registered office:
Brandstraat 30
9160 Lokeren, Belgium

Principal place of business:
Brandstraat 30
9160 Lokeren, Belgium
Tel +32 56 48 11 11
Fax +32 56 48 63 70

info@stow-group.com
www.stow-group.com
www.movu-robotics.com
www.movu-robotics.com/en/engineering
For service, interventions and spare parts:
Service Department
Contact number:
Tel +32 56 960 990
aftersales@stow-group.com

2
Introduction
Movu provides an Application Programming Interface (API) that enables a Host Warehouse Management System (WMS) or
Warehouse Control System (WCS) to integrate Movu’s line of automated storage and transportation solutions.
API Release notes
API
Version Date Concepts
1.0.0 07/07/2023 Initial version - routes listen to v0.1
1.0.1 10/08/2023

StayAtTarget for Escala was added.
Routes are now updated to listen to v1.0
1.1.0 28/09/2023 Vehicle interface is now available
1.2.0 19/10/2023

Webhook notifications are now camelcased, CORS support is added, fixed double feedback
message bug

3
Technical setup
Communication types
All communication across the interface can be categorized as follows:
Query: Interrogating the system without mutating state.
Command: Instructing the system to perform an action (and hence mutating state).
Notifications: Notifying changes in the system. Queries and Commands are initiated by the Host, Notifications are received
by the Host.
Architecture
The interface is composed of a Level 2 RESTful API, as defined by the _Richardson Maturity Model.
It relies on HTTP(S) as the communication protocol.
URIs break down the service endpoint into smaller scopes of resources.
Verbs and Status Codes are standardized, reducing unnecessary variation.
To receive the notification stream on a specific resource, a webhook with a callback URI needs to be defined. This allows the Host to
subscribe to only those notifications it is interested in. The Host only needs to subscribe once, the subscription details will be
persisted.
The receiving endpoint is expected to always be available, but if it is not, events will be buffered and transmitted in batch as soon as
the connection can be re-established.
Generic HTTP Status Codes
Category Status Code Description
Success 200 OK

The request has succeeded. The information returned with the response is dependent on
the method used in the request.

201 Created The request has been fulfilled and resulted in a new resource being created.
202 Accepted

The request has been accepted for processing, but the processing has not been
completed. The request might or might not eventually be acted upon, as it might be
disallowed when processing takes place.

204 No Content The request has been successfully processed but is explicitly not returning any content.

Client
Error

400 Bad Request

The request could not be understood due to malformed syntax. The client should not
repeat the request without modifications.

401
Unauthorized

The request requires user authentication.

403 Forbidden The request was a legal request, but the server is refusing to respond to it.
404 Not Found The requested resource could not be found but may be available again in the future.
409 Conflict The request cannot be completed due to a conflict with the current state of the resource.

Server
Error

500 Internal
Server Error

A generic error message, given when no more specific message is suitable.

4

501 Not
Implemented

The server either does not recognize the request method, or it lacks the ability to fulfill
the request.

Specification
The Interface description is available as an OpenAPI specification, as provided by the Swagger toolset. Swagger adds online technical
documentation, exploration and code generation possibilities.
See https://editor.swagger.io/ for a non-related sample specification.

5
Mission Interface
Basic Concepts
The Mission Interface focuses on tailored transportation and gives the Host control over specific load movements. Responsibility for
storage strategy, priority definition, sequencing and high-level throughput optimization logic remains with the Host.
The following resources are involved
Load: the basic unit that is stored or transported in the warehouse.
Location: a space in the warehouse, where Loads can be placed for storage or transferal.
Mission: an abstraction representing the single transport movement of a Load from a Source Location to a Destination
Location.
Vehicle: the machine used to execute the Mission. (i.e. a shuttle)
Location
Each source or destination location (or node) consists of 5 properties.
Level: Like story or floor
Lane: Number of the target lane in main lane direction
Section: Consists of either a main lane or a group of concatenated storage locations
Index: Index within a section starting from the front of the warehouse (Only to be used in EScala warehouses)
Exit or Approach: Side of the warehouse to leave or enter a location with the load. Either front or back
Mission States
A Mission can go through several states during its lifetime:
State Substate Description
Pending Created The Mission is ready to be executed, but a Vehicle has not been assigned to it yet.
Assigning The selection of a Vehicle has started, but it is not executing the Mission yet.

Retrieving Assigned

The Mission is being executed, but the Load has not been retrieved from the source
Location yet.

Awaiting Entrance
Confirmation

Optional. Waiting for source Location confirmation.

Storing Picked

The Mission is being executed, the Load has been retrieved from the source Location,
but has not been stored on the destination Location yet.

Awaiting Exit
Confirmation

Optional. Waiting confirmation before leaving the source location.

Awaiting Entrance
Confirmation

Optional. Waiting for destination Location confirmation.

Finished Dropped

The Mission has been completed successfully. The Load has been retrieved from the
source Location and stored in the destination Location
Cancelled The Mission has been cancelled gracefully.

6

Aborted

The Mission has been aborted, corrective actions might be necessary to continue
automatic operation

Mission Queries
Mission Status
The Mission Status query returns current status information of a specific mission, or of all current missions.
Mission Commands
Create Mission
The Create Mission command will create a new mission, ready to be executed.
The Mission Id must be unique among all missions.
The minimal information needed to perform a mission is the destination location and a source. The mission source can either be a
source location, and in some cases the vehicle that is currently carrying the load or the load id.
The Vehicle id can be specified if you want a specific vehicle to execute the mission. This is the only case when a mission can have a
vehicle in its created substate.
The Load id is only used for information and temporary tracking. We recommend specifying a unique load id in the create mission
request.
The system keeps track of all loads in transit, in certain situations, it may be possible to infer the vehicle id from the load id alone.
This may be useful in case of error handling and missions that could not be completed.
The new mission will be in the Created substate.
If a vehicle is available to handle the mission, the mission will quickly go from substate Created to Assigning. If no vehicle is
immediately available, the mission will stay in Pending until a vehicle is assigned.
Creating a mission can be done by a POST-request to /api/v1.0/missions , examples can be found in the Mission Use Case
chapter
Update Mission
The Update Mission command allows the Host to update some properties of the mission.
Depending on the current state of the mission, certain properties may not be allowed to change.
Source
The source can be changed if the mission has not yet picked the load.
Updating a mission source can be achieved by a PUT-request to /api/v1.0/missions/{missionId}/source
Priority
Priority 1 is the highest and 10 is the lowest. The priority of a mission can be changed if the mission is not yet in state Retrieving.
Updating a mission priority can be achieved by a PUT-request to /api/v1.0/missions/{missionId}/priority
Destination
The destination of a mission can be changed if the vehicle has not yet dropped its load.

7
Updating a mission destination be achieved by a PUT-request to /api/v1.0/missions/{missionId}/destination
Vehicle
The vehicle of a Mission can be changed if the mission is not yet in state Retrieving.
Updating a mission vehicle can be achieved by a PUT-request to /api/v1.0/missions/{missionId}/vehicle
Cancel Mission
The Cancel Mission command allows to cancel a mission when the load has not been retrieved from the source location. Trying to
cancel a mission when a load has already been retrieved is not allowed.
The cancelled mission will be in the Cancelled state. Cancelling a mission can be done by a POST-request to
/api/v1.0/missions/{missionId}/cancel
Abort Mission
The Abort Mission command allows to force mission completion, indicating that the mission should be terminated. Manual actions
may need to be performed to continue automatic operation. Aborting a mission when a load was picked will result in the shuttle not
being available for any future missions. Manual actions are needed to release the shuttle for future executions.
This command is intended as error handling and should not be needed for standard operation.
The aborted mission will be in the Aborted state.
If a mission is aborted when the load is already retrieved the shuttle will stop its execution. A new mission will need to be created to
get the shuttle with load moving again. This new mission request does not need a source location but a shuttle or load id. Either of
those two will work. This new mission request can have a totally different destination and priority.
Aborting a mission can be done by a POST-request to /api/v1.0/missions/{missionId}/cancel
Mission Webhooks
The Host can be notified every time a mission is created or updated. The notifications are sent over a webhook, also known as http
callback.
The contract with exact payload to be implemented can be found in the swagger documentation of the API. Code generation tools
should be used to generate the consumer side of the webhooks including the webhook notification contract.
Registering a webhook is done by specifying which address and context you are interested in.
Each webhook notification contains a unique Notification id, context type (e.g., Mission), notification type (e.g., MissionCreated) and
timestamp in ISO-8601 format when the event occurred. The payload of the notification depends on the notification type.
Example notifications
Example notifications expected to be received by a webhook consumer in case of a successful Mission.
MissionCreated, MissionAssigning, MissionAssigned, MissionPicked, MissionDropped
Configuration
It is possible to configure the webhook subscriber, this configuration is shared between all webhook registrations. It isn’t possible to
configure an individual webhook registration.
Connection timeout, discard timeout and max batch size can all be configured.

8
Vehicle Interface
The Vehicle Interface exposes information about the vehicles within the system.
Vehicle queries
Single Vehicle status
By requesting a specific vehicle status, the details of it's current state is returned. The details contain:
The mode the Vehicle is in
The battery level
The current position
The load the shuttle is carrying
The error messages if the Vehicle is currently in error
The query is available via a GET -request to /api/v1.0/vehicles/{vehicleId}
Status of all vehicles
This query returns the details as mentioned above for all the available vehicles within the system.
The query is available via a GET -request to `/api/v1.0/vehicles
Shuttle States
value state
0 Idle
1 Info
2 LoadPLt
3 PickPLt
4 Stop
5 Shuffle
6 Count
7 Demo
8 Manual
9 ChangeHome
10 Homing
11 Pincode
12 Pincode_OK
13 Error
14 Low_Power

9
15 Charge
16 Startup
17 Drive2Sct
18 Unknown
19 StorePlt
20 RetrievePlt
22 Dock
23 Undock

Vehicle Webhooks
Over time the API will send feedback messages about the states of the shuttles. It is possible to register on these messages through
the webhook interface and register for the context 'Vehicle'.
State Description
VehicleBatteryLevelChanged State of Charge of the shuttle

10
Task Interface
The task Interface exposes the possibility to perform shuttle roaming.
Task commands
Docking
This command is used to send a shuttle within a vertical conveyer and secure it to be able to move the shuttle to a different level.
The WCS needs to indicate which shuttle and where the shuttle needs to go. The command can be executed via a POST -request to
`/api/v1.0/tasks/dock
An example can be found in the Task use case chapter.
Undocking
The undocking command is used to release the docked shuttle from a vertical conveyer.
The command can be executed via a POST -request to `/api/v1.0/tasks/undock
An example can be found in the Task use case chapter.

11
Health Check Interface
Health checks are a fast and simple way of reporting the health of the application and application infrastructure components. Health
checks are informational and are not required for normal operation of the system.
Liveness probe
The Liveness probe can be used to verify that the application is running and active.
Request: GET /api/v1.0/health/live Response:
200 OK
408 Request Timeout
503 Service Unavailable
Readiness probe
In some cases, the application can be running and responsive, but temporarily unable to serve requests, for example during start-up
or when a dependency is unhealthy. A readiness probe can be used to detect these situations and will respond with all unhealthy or
degraded dependencies.
Request: GET /api/v1.0/health/ready Response:
200 OK
408 Request Timeout
503 Service Unavailable

12
Mission use cases
Way of work
Storing a load in a storage lane is preferably done by giving the requested storage distance within the mission. The shuttle will then
try to store the load at the requested distance. The shuttle will use its sensors to finetune the storage distance in relation to the last
load already present in the lane. If the shuttle is able to store the load within a predefined tolerance window, the real storage
distance will be sent in the ‘MissionDropped’ feedback. This distance should be stored in a way it can added in the mission to pick
this load again. The distance is always given relative to the ‘FRONT’ of the warehouse.

Atlas® 2D Load movement from location to storage lane
Messages
Nr. HOST -> ASTC ASTC -> HOST
1 Create Mission
2 MissionCreated
3 MissionAssigning
4 MissionAssigned

13
5 MissionPicked
6 MissionDropped
Mission Creation
Creation of a mission is done by sending the json in a POST-request to /api/v1.0/missions/ the HTTP request will return a
201 response with the mission payload.
{
"id": 21,
"source": {
"level": 1,
"lane": 2,
"section": 11,
"distance": 600,
"exit": "Front"
},
"destination": {
"level": 1,
"lane": 2,
"section": 4,
"distance": 1500,
"approach": "Back"
}
}
Webhook feedback: aSTC is assigning the mission
{
"notificationType": "MissionAssigning",
"payload": {
"missionId": "21",
"vehicleId": "1"
},
"notificationId": "e9e1eb98-31de-42fe-9826-bc8d1332868e",
"timeStamp": "2023-06-29T12:12:55.9760617Z",
"contextType": "Mission"
}
Webhook feedback: aSTC is assigned the mission
{
"notificationType": "MissionAssigned",
"payload": {
"missionId": "21",
"vehicleId": "1"
},
"notificationId": "df76d11a-692b-4c6d-9068-aa515d464f51",
"timeStamp": "2023-06-29T12:12:57.8787298Z",

14
"contextType": "Mission"
}
Webhook feedback: the shuttle picked the load
{
"notificationType": "MissionPicked",
"payload": {
"missionId": "21",
"vehicleId": "1",
"source": {
"exit": "Front",
"level": 1,
"lane": 2,
"section": 11,
"distance": 600
},
"loadId": "load-9-9ijcvoeoa00lrqmkq4ohr"
},
"notificationId": "cdef1010-52b9-42a4-bcc7-472bf9bd2590",
"timeStamp": "2023-06-29T12:13:10.2863457Z",
"contextType": "Mission"
}
Webhook feedback: the shuttle dropped the load at the destination
{
"notificationType": "MissionDropped",
"payload": {
"missionId": "21",
"vehicleId": "1",
"destination": {
"approach": "Back",
"level": 1,
"lane": 2,
"section": 4,
"distance": 1500
},
"loadId": "load-9-9ijcvoeoa00lrqmkq4ohr"
},
"notificationId": "da8c577f-db4b-441b-a4a1-3ac95bd172e0",
"timeStamp": "2023-06-29T12:13:22.3825944Z",
"contextType": "Mission"
}
Remark: the Distance returned in this payload is the real feedback coming from the shuttle. This distance should be given into the
mission when this load needs to be retrieved again.

15
Atlas® 2D Load movement from storage lane to location
Mission Creation
{
"id": 22,
"source": {
"level": 1,
"lane": 2,
"section": 4,
"distance": 1500,
"exit": "Back"
},
"destination": {
"level": 1,
"lane": 2,
"section": 11,
"index": 1,
"approach": "Front"
}
}
Remark: the source distance is the real feedback that came from the shuttle during load storage. It is important for the reliability of
the system to specify it correctly into the mission creation message.
Atlas® 2D Load movements with confirmation requests
Late decision at destination
Typically, if more than one shuttle is sent towards a storage lane, it cannot be predicted in which sequence they will arrive. Therefor
the principle of “late decision” is introduced. With this principle it becomes possible to update the mission at the moment that the
shuttle is positioned in front of the lane. The ASTC will ask “confirmation” at the right moment in such a case.
The illustration shows a typical example of the use case: the situation of the destination lane has changed during the shuttle
movement from source to destination. Initially ‘Distance X’ was expected but finally ‘Distance Y’ should be sent.

16
Message flow of the use case:

Mission creation
{
"id": 105,
"source": {

17

"level": 1,
"lane": 13,
"section": 1,
"distance": 600,
"exit": "Back"
},
"destination": {
"level": 1,
"lane": 15,
"section": 1,
"distance": 2000,
"approach": "Back",
"entranceConfirmationRequired": true
}
}
We request the load to be dropped at a distance of 2000. Since there are multiple tasks ongoing, we want to confirm this drop. We
can ask aSTC to do this by adding the entranceConfirmationRequired property.
Feedback MissionAwaitingDestinationEntryConfirmation
{
"notificationType": "MissionAwaitingDestinationEntryConfirmation",
"payload": {
"missionId": "105",
"vehicleId": "1",
"destination": {
"approach": "Back",
"level": 1,
"lane": 15,
"section": 1,
"distance": 2000
},
"loadId": "load-6-8o5pemlcd2vqj4i3zu8v4"
},
"notificationId": "32b1a5ce-c411-4438-8ce1-8f5d9ed8087a",
"timeStamp": "2023-06-30T14:14:27.8523623Z",
"contextType": "Mission"
}
The shuttle is ready to pick at distance 2000, but the composition of the lane changed. An extra load was stored. Hence we ask the
shuttle to pick the last one in the lane.
Update the Mission
{
"destination": {
"level": 1,
"lane": 15,
"section": 1,
"distance": 2500,

18

"approach": "Back"
}
}
We sent the new distance (2500) where the latest added load should be.
Feedback MissionDestinationChanged
{
"notificationType": "MissionDestinationChanged",
"payload": {
"missionId": "105",
"destination": {
"approach": "Back",
"level": 1,
"lane": 15,
"section": 1,
"index": 0,
"distance": 2500
}
},
"notificationId": "f0f3c9a1-fe41-40f5-bfc4-4b3600117f6f",
"timeStamp": "2023-07-12T15:35:08.6601516Z",
"contextType": "Mission"
}
The mechanism notifies the registered listeners that the shuttle will pick on distance 2500.
Confirm the Mission
This change needs to be acknowledged by the WMS or WCS. This can be done by calling the following endpoint:
/api/v1.0/missions/{missionId}/confirmenterdestination
Where {missionId} is the id of the mission that you want to confirm (in this example it is 105).
Responses:
202 Accepted: The request to confirm source entry has been accepted.
400 Bad Request: When the request is not accepted. For example when the mission is already finished.
Late decision at source
Comparable with the late decision at destination, the same principle can be used at the source location. The reason to use this
feature is the same as using it at the destination: the situation at the source location can be different between the moment of
mission generation and the moment of arriving in front of the source location.
Mission Creation
We request the load to be picked at a distance of 600. Since there are multiple tasks ongoing, we want to confirm this pick up. We
can ask aSTC to do this by adding the entranceConfirmationRequired property.
Late decision at source mission creation:
{
"id": 106,
"source": {
"level": 1,
"lane": 13,
"section": 1,
"distance": 600,
"exit": "Back",
"entranceConfirmationRequired": true
},
"destination": {
"level": 1,
"lane": 15,
"section": 1,
"distance": 2000,
"approach": "Back"
}
}
Source entry confirmation feedback when shuttle arrives at source:
{
"notificationType": "MissionAwaitingSourceEntryConfirmation",

20
"payload": {
"missionId": "106",
"vehicleId": "1",
"source": {
"exit": "Back",
"level": 1,
"lane": 13,
"section": 1,
"distance": 600
},
"loadId": "load-1-cexcu9mysupsqa78g9mo5"
},
"notificationId": "a43e1f96-ca11-41fb-9b64-3c88bc6aeb3f",
"timeStamp": "2023-07-04T15:20:49.2687826Z",
"contextType": "Mission"
}
The shuttle is ready to pick at distance 600 in lane 13, but the composition of the lane changed. There is no load left. Hence we ask
the shuttle to pick a load in the lane next to it.
Update the source of the Mission
{
"source": {
"level": 1,
"lane": 14,
"section": 1,
"exit": "Back",
"distance": 600
}
}
We sent the shuttle to another lane, 14, on distance 600 where a load is available.
Feedback MissionSourceChanged
{
"notificationType": "MissionSourceChanged",
"payload": {
"missionId": "106",
"source": {
"exit": "Back",
"level": 1,
"lane": 14,
"section": 1,
"index": 0,
"distance": 600
}
},
"notificationId": "6dd89281-815e-4ad6-8e34-3a9640218139",
"timeStamp": "2023-07-12T14:39:21.8732471Z",

21
"contextType": "Mission"
}
The mechanism notifies the registered listeners that the shuttle will pick on lane 14.
Confirm the Mission
This needs to be acknowledged by the WMS or WCS. This can be done by calling the following endpoint:
/api/v1.0/missions/{missionId}/confirmentersource
Where {missionId} is the id of the mission that you want to confirm (in this example it is 106).
Responses:
202 Accepted: The request to confirm source entry has been accepted.
400 Bad Request: When the request is not accepted. For example when the mission is already finished.

22
Atlas® 2D Shuttle roaming with a load
In this use case the steps are described to a shuttle with a load to another level.
Note: This can only be done if the shuttle can move onto the lift.
Picking and docking
The payload is similar to a default movement. Except this time we have to set the dockAtDestination-flag to true. It is also required to
pass the loadId. If this is not done, the aSTC will lose track of which load is on the shuttle that is docked and the software will throw
an error on the undocking-mission.
Note: The destination distance is 800 here, which is the calculated center of the lift.
Payload
{
"id": 204,
"source": {
"level": 1,
"lane": 3,
"section": 3,
"distance": 600,
"exit": "Front"
},
"dockedAtSource": false,
"destination": {
"level": 1,
"lane": 20,
"section": 6,
"distance": 800,
"approach": "Front"
},
"dockAtDestination": true,
"Load" : {
"Id": "LOAD-1"
}
}
The feedback is similar to the previous use cases. The task goes through the assigning, assigned, Picked and Dropped state.
Undocking and dropping
In this use case the lift was moved to level 7 by the WCS.
Payload
In this scenario the shuttle is still docked under the load. Hence we cannot send another shuttle to the load and we need to pass the
Vehicle-Id of the shuttle under the load in the mission. On top of that we need to let the software know the shuttle is still docked, so
we flag dockedAtSource to true. The last thing we need to define is the Load-Id that was specified in the docking mission for
verification purposes.

23
{
"id": 205,
"source": {
"level": 7,
"lane": 20,
"section": 6,
"distance": 800,
"exit": "Front"
},
"dockedAtSource": true,
"destination": {
"level": 7,
"lane": 10,
"section": 5,
"distance": 600,
"approach": "Back"
},
"dockAtDestination": false,
"vehicle": {"id": 1 },
"Load" : {
"Id": "LOAD-1"
}
}
The feedback is similar to the previous use cases. The task goes through the assigning, assigned, Picked and Dropped state.
Atlas® 2D Lane full error handling
In this use case the error "Lane full" is triggered. This error is thrown when a load is moved to a position where another load already
resides. To simplify the example, no confirmation flags are used.
Note: This is an unhappy flow, it should be clarified on why the pallet was send to an occupied location. The problem could reside
on each layer, hence a deep dive into the context is advised.
Create Initial Mission
The mission is send as a JSON in a POST-request to /api/v1.0/missions/
{
"id": 400,
"source": {
"level": 1,
"lane": 3,
"section": 3,
"distance": 700,
"exit": "Front"
},
"destination": {
"level": 1,
"lane": 4,
"section": 3,
"distance": 700,

24
"approach": "Front"
}
}

Mission feedback
In this example the normal feedback is received: MissionCreated, MissionAssigning, MissionAssigned,
MissionAwaitingSourceEntryConfirmation, MissionPicked, MissionAwaitingDestinationEntryConfirmation until we receive the
MissionError we are looking for in this example:
{
"notificationType": "MissionError",
"payload": {
"missionId": "400",
"errors": [
{
"id": 0,
"message": "1-2006-103: WMS / TASKUPD / Lane is full, no loads can be added",
"severity": "Warning"
}
]
},
"notificationId": "7e36e4a0-535a-4bfb-9f33-140ddb2a45b0",
"timeStamp": "2023-11-20T09:29:54.1793466Z",
"contextType": "Mission"
}
In the message we can see that the lane is full and the pallet can not go to the original destination. At this point the error needs to
be analyzed.
Abort Mission
Since this mission failed, we can no longer use it to continue. Hence we abort the message by sending a post to
/api/v1.0/missions/{missingId}/abort . In this way we clear the task from the shuttle and it can move again.
Create Mission To Move Shuttle With Load
The pallet is still on the shuttle, so we need to store it on another location. Since the load does not need to be picked up, the
"source" parameter can be omitted. Since we want the mission to be assigned to the shuttle we also want to pass the vehicle
parameter. It is also important to send the load-Id, it is used for internal tracking within the aSTC.
{
"id": 401,
"destination": {
"level": 1,
"lane": 5,
"section": 3,
"distance": 700,
"approach": "Front"
},
"vehicle": {
"Id": 1

25
},
"Load": {
"id": "load-1-er935g2wwynia823pzqx3"
}
}
Mission feedback
For the new mission the normal feedback is returned that is the same as a simple 'MoveLoad'-operation, with exclusion of
''MissionAwaitingSourceEntryConfirmation" since the load is already on the shuttle.

26
Task use cases
Atlas® 2D Roaming a shuttle to another level
When a new shuttle is added, or there is a lot of work on another level, it could be possible that a shuttle needs to move to another
level. For this the WCS can perform shuttle roaming by the use of 2 tasks docking and undocking.
Note: These commands are only valid for a shuttle without a load, in other cases the missions interface should be used (see previous
chapter).
Dock Shuttle
For this a docking node is required. A docking node could be a vertical conveyer (used in this example). Docking means the shuttle
needs to apply his brakes when on this node.
Payload
We want to send shuttle with id 1 to the lift (destination). This is a POST to {{host}}/api/v1.0/tasks/dock
{
"id": 3,
"destination": {
"level": 7,
"lane": 20,
"section": 6,
"index": 1,
"distance": 600,
"approach": "Front",
"entranceConfirmationRequired": false,
"entranceConfirmed": true
},
"vehicle": {
"id": 1
},
"priority": 10
}
Task feedback
The task was received:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "NotAssigned"
},
"notificationId": "3e83f8bf-ed98-44b2-851b-84957e07c347",
"timeStamp": "2023-07-31T14:09:04.7837543Z",
"contextType": "Task"
}

27
The task was assigned to the shuttle:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "Assigned"
},
"notificationId": "da19109f-102f-4a95-a822-37e7d74919bf",
"timeStamp": "2023-07-31T14:09:04.9426063Z",
"contextType": "Task"
}
The task is executing, the shuttle is moving:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "MovingShuttle"
},
"notificationId": "5bdec871-159e-43d6-9cc9-513829ea0aff",
"timeStamp": "2023-07-31T14:09:06.778574Z",
"contextType": "Task"
}
Waiting for handover:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "WaitForHandover"
},
"notificationId": "3c31c6ba-302e-4fd4-937c-330c5690cc05",
"timeStamp": "2023-07-31T14:09:15.7906275Z",
"contextType": "Task"
}
Task is done, the shuttle is now docked:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "Done"
},
"notificationId": "443f2351-daaa-42c8-b96c-b9a1ef604fc4",
"timeStamp": "2023-07-31T14:09:28.7749684Z",

28
"contextType": "Task"
}
Undock shuttle
For this use case the lift was moved to level 7.
Payload
The destination property is optional. If the WCS does not specify a destination property, the aSTC will move the shuttle automatically
to a parking spot. This is a POST to {{host}}/api/v1.0/tasks/undock
{
"id": 201,
"source": {
"level": 7,
"lane": 20,
"section": 6,
"index": 1,
"distance": 600,
"exit": "Front",
"entranceConfirmationRequired": false,
"entranceConfirmed": true,
"exitConfirmationRequired": false,
"exitConfirmed": true
},
"vehicle": {
"id": 1
},
"priority": 10
}
Feedback
Undocking:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "201",
"state": "Undocking"
},
"notificationId": "84882fca-ec9c-46a7-88fc-a0f3a3b31c8f",
"timeStamp": "2023-08-10T13:54:20.8256322Z",
"contextType": "Task"
Preface
This document serves as a guide on how to determine the maximum pallets in a given lane and to calculate index <-> mm positions
inside a lane based on minimum parameters.

3
Introduction
In this document different proposals of pallets positions are described. In this section, there is a summary of some important
parameters and equations to define the pallet positions.
The parameters are sketched in the figure below. This figure contains a lane surrounded by two mains. The same parameters are also
valid when there is only one main at a certain side and an endstop at the other side. Here is a summary of the definitions of each
parameter:
Length: the total length of the lane.
DistanceOffsetLoadBack: This is the distance between the main and the last load dropped from the back.
MaxLoadLength: This is the maximum distance of the load. This is the sum of the pallet length and two times the overload
(see figure below).
Gap: Distance between the two loads.
DistanceOffsetLoadFront: This is the distance between the main and the last load dropped from the front.
PltPos1, PltPos2, PltPos3: This is the distance between the main at the front side and the center of a certain pallet.
MaxLoads: The maximum number of loads that can go in one lane.

The equation from where we start is:

For the pallet positions next equation is valid:

4
Some parameters are known:
Length, this is the length of the lane
The minimal DistanceOffsetLoadBack and DistanceOffsetLoadFront can be found by adding 50mm to the
DisEoLnPlateMn found in the ConfigLevel tab of the warehouse file.
MaxLoadLength, this is the maximum length of a pallet plus maximum overload that can be found in the lane.
MaxLoads, this is the maximum number of pallets found in a lane. These can be found in the warehouse file under the “PI”
and “PE” parameters in a given node or can be calculated with the equation found further below.
Other parameters are not known and need to be determined:
Gap
PltPos1, PltPos2 and PltPos3.
In the next sections different proposals are introduced. It all depends on where you add the margin. You can give the margin to:
DistanceOffsetLoadFront or DistanceOffsetLoadBack
The Gap

5
Dynamic gap
In this section the Margin is given to the gaps. The gaps stay constant in one lane. The Gap can be calculated when MaxLoads is
higher than one (otherwise there is no gap). The equation for this is:

Visual:
Example:
Length = 11250 mm
DistanceOffsetLoadFront = DistanceOffsetLoadBack = 150 mm
MaxLoadLength = 1100 mm
Gap = 50 mm
MaxLoads = 9

6
Gap = (11250 – 150 – 1100*9 – 150)/(9 – 1) = 131,25 mm
This will be rounded down to 131 mm.
PltPos3 = 150 + (3 -1)*(1100 + 131) + 1100/2 = 3162 mm

7
General remarks
To pick and drop a euro pallet with an IND shuttle in one time, the sum of the gap and two times the overload must be at
least 150 mm.
For picking a pallet it is advised that the WCS gives the PltPos instead of the Index/PltNr. There is no guarantee that the
shuttle can perfectly drop a pallet on a certain position. The position where the pallet has been dropped is given from the
shuttle -> STC -> WCS. The performance and robustness will improve if the WCS stores this position and gives it when the
pallet needs to be picked up again. This is crucial for asymmetrical proposals.
For dropping a pallet either the PltPos or the Index/PltNr can be given. If Index/PltNr is given, we can calculate the PltPos
depending on the proposal that has been agreed.
In section 3 a gap of 131,25 mm is calculated. This is just a theoretical value, in practice this will be rounded down to 131
mm. An extra margin is obtained when rounding down. This margin is small and can be neglected.

8
Equations
MaxLoads in lane

Calculate margin

Gap between pallets in lane

For the pallet positions next equation is valid

Minimum gaps to take into account
When using repositioning a minimum of 50mm between pallets needs to be considered.
When picking pallets in one go, which is better for throughput, a distinction needs to be made based on shuttle type. For an
industrial type shuttle e.g. Following distinction needs to be made:
Industrial shuttle picking industrial pallet: min 50mm between pallets.
Industrial shuttle picking euro pallet: min 150mm between pallets.
DistanceOffsetLoad Back & Front
The minimal DistanceOffsetLoadBack and DistanceOffsetLoadFront can be found by adding 50mm to the
DisEoLnPlateMn found in the ConfigLevel tab of the warehouse file.

9

10
Pallet calculations
To perform above calculations for ‘Dynamic Gap’, an excel document was made (‘PltPosCalc.xlsx’). This document will aid the user in
calculating the minimum lane length that is required to fit all pallets by giving the number of pallets for each pallet type or give the
maximum number of pallets that can be fitted in a lane with given length.
How to work with this excel file:
1. Fill in ‘Storage Strategy’, ‘Shuttle Type’, ‘Warehouse config’ and ‘Load Type’.
1. ‘Storage Strategy’: Is the rack high throughput, low storage (‘High Throughput’) or is the rack low throughput, high
storage (‘Dense Storage’)? This choice will determine whether the shuttle is allowed to reposition the pallet or not.
2. ‘Shuttle Type’: Shuttle type defines whether the rack is meant for an industrial, euro or US shuttle. Also the
hardware version is defined here. This will give us set of shuttle variables for the formulas (shuttle length, size of
bumpers...)
3. ‘WarehouseConfig’: Configuration settings depending on the warehouse layout.
1. LANE FRONT / LANE BACK: How does the edge of the rack look like? Does it end with an endstop plate
or is this edge connected to a main node?
2. DisEoLnPlateMn: Distance of the beginning of the Z-rails to the blue upright. (See figure above in chapter
4)
3. LenPlateMn: The length of the crossing plate. (See figure above in chapter 4)
4. ‘Load Type’: Possibility to define 5 different load types, where the length and the maximal single side overload can
be defined for each load type.
2. Choose which calculation you want to perform.
1. Option 1: Calculate max number of loads (Sheet “**PLTPOSCALC_MaxLoads**”) For this option, the lane length is
given and can be filled in in C6 cell. This will trigger the calculations and all ‘Max number of loads’ are shown in the
list below for each load type.
2. Option 2: Calculate minimum lane length (Sheet “**PLTPOSCALC_MinLaneLength**”) For this option, the maximum
number of loads row in the first list needs to be filled in (C6:G6). For each load type, a minimal lane length will be
calculated and shown. Below, the ‘Total Minimum Lane Length’ is shown.
Below these two options, a table is shown with all corresponding positions for each load type!
DistOffsetFront/Back calculations
To calculate the distOffsetFront and DistOffsetBack variables for our formula, the edge layout of the lane must be known on both
sides. This can be set at ‘WarehouseConfig/LANE FRONT’ and ‘WarehouseConfig/LANE BACK’ respectively. The possible edge layouts
are explained below.
REMARK: These distance offsets are also dependent on Load types when ‘Storage Strategy’ is ‘High Throughput’!!!!
Endstop layout
For the endstop layout, there is a distinction in offset needed based on Storage Strategy. For Dense storage, the offset can be
smaller for pallets smaller than the shuttle. Some parameters are defined here for the formula (figure below):
ShLength: Length of shuttle, depends on Shuttle Type.
BumperLength: Length of bumper
DistVertSnsShEdge: Distance from vertical pallet sensor to the edge of the shuttle, depends on Shuttle Type and is only
important when ‘Storage Strategy’ is ‘Dense Storage’.
PalletLength: Length of pallet shuttle is carrying (without overload).

11
LoadSingleOverload: Single side overload of the load.
MarginEndstop: Some margin must be taken into account to tackle inaccuracies defined below. A minimal margin of 7cm is
recommended.

For Storage Strategy ‘Dense Storage’ (repositioning of pallet allowed):

For Storage Strategy ‘High Throughput’ (repositioning of pallet NOT allowed):

Some margin (‘MarginEndstop’) must be taken into account here such as:
Inaccuracy of the endstop sensor (small distance versus long distance and coldstore versus non-coldstore). Sensor
sometimes behaves differently.
Skewed pallets (pallets skew around their normal axis when being lifted because of mechanical inaccuracies)
Pallet measurement inaccuracies (during Pick, pallet is measured but this can have some inaccuracies due to speed of
shuttle during measurement, unideal pallet shapes, sample time inaccuracies)
Main node layout
For the main node layout, there is a distinction in offset needed based on Storage Strategy. For Dense storage, the offset can be
smaller for pallets smaller than the shuttle.
Some parameters are defined here for the formula (figure below):
ShLength: Length of shuttle, depends on Shuttle Type.

12
DistVertSnsShEdge: Distance from vertical pallet sensor to the edge of the shuttle, depends on Shuttle Type and is only
important when ‘Storage Strategy’ is ‘Dense Storage’.
DistProfileSnsShEdge: Distance from profile sensor to the edge of the shuttle, depends on Shuttle Type.
PalletLength: Length of pallet shuttle is carrying (without overload).
LoadSingleOverload: Single side overload of the load.
MarginMain: Some margin must be taken into account to tackle inaccuracies defined below. A minimal margin of 4cm is
recommended.
LenPlateMn: The length of the crossing plate, with support beam taken into account.
DisEoLnPLateMn: Distance from begin of Z-rail of lane to blue upright (where crossing plate begins).
CrossPlateDetDist: Distance shuttle has to see the crossing plate with its profile sensor (in mm). Depends on shuttle config
settings!

For Storage Strategy ‘Dense Storage’ (repositioning of pallet allowed):

First part of the formula (between the brackets ()) indicates how far the edge of shuttle can be from begin of lane without shuttle
entering main node. This is the closest position shuttle can go towards the main node without moving on the main node.
For Storage Strategy ‘High Throughput’ (repositioning of pallet NOT allowed):

Some margin (‘MarginMain’) must be taken into account here such as:

13
Inaccuracy of the crossing plate and construction sensor.
Skewed pallets (pallets skew around their normal axis when being lifted because of mechanical inaccuracies)
Pallet measurement inaccuracies (during Pick, pallet is measured but this can have some inaccuracies due to speed of
shuttle during measurement, unideal pallet shapes, sample time inaccuracies)
Margins
Some margin (‘MarginMain’) must be taken into account here such as:
Inaccuracy of the crossing plate and construction sensor.
Skewed pallets (pallets skew around their normal axis when being lifted because of mechanical inaccuracies)
Pallet measurement inaccuracies (during Pick, pallet is measured but this can have some inaccuracies due to speed of
shuttle during measurement, unideal pallet shapes, sample time inaccuracies)
Use Excel Sheets
Below, a short guideline is given to use ‘PltPosCalc.xlsx’ excel sheet.
First thing to do, is set the correct parameters for this rack on the top to the right in the datasheet you are using.
1. ‘Storage Strategy’: Is the rack high throughput, low storage (‘High Throughput’) or is the rack low throughput, high storage
(‘Dense Storage’)? This choice will determine whether the shuttle is allowed to reposition the pallet or not.
2. ‘Shuttle Type’: Shuttle type defines whether the rack is meant for an industrial, euro or US shuttle. Also the hardware version
is defined here. This will give us set of shuttle variables for the formulas (shuttle length, size of bumpers...)
3. ‘WarehouseConfig’: Configuration settings depending on the warehouse layout.
1. LANE FRONT / LANE BACK: How does the edge of the rack look like? Does it end with an endstop plate or is this
edge connected to a main node?
2. DisEoLnPlateMn: Distance of the beginning of the Z-rails to the blue upright. (See figure above in chapter 4)
3. LenPlateMn: The length of the crossing plate. (See figure above in chapter 4) 4. ‘Load Type’: Possibility to define
5 different load types, where the length and the maximal single side overload can be defined for each load type.
PLTPOSCALC_MaxLoads Excel sheet
After setting all parameters for the rack correctly, the correct lane length must be given for which you want to calculate the
maximum number of loads that could fit in this storage lane. This length is filled in at “Total Lane length (in mm)” in cell C6.
For each Load Type, the corresponding maximum number of loads that can fit in here will be shown below “Total Lane length (in
mm)”, in “Max number of loads” (C9:G9). Additionally, below this result (C11:G11), the missing length of your entered lane is given, if
you would want to add an additional load in this lane.
Below this, a list is given of all relative pallet positions in this lane is given.

14
Example (Load Type 1 is industrial pallet and Load Type 2 is euro pallet):

A lane length of 10m (10000mm) is given in a ‘Dense Storage’ rack (repositioning allowed) for a lane which is connected to a main
aisle on one side and ends with an endstop plate on the other side.
As can be seen in the table ‘Max number of loads’, 8 industrial pallets and 10 euro pallets will fit in this lane. Additionally, if the lane
length would be increased with 96mm, a 9th industrial pallet would fit (9 pallets in a storage lane of 10096mm) and if lane length
was increased with 126mm, a 11th euro pallet would fit (11 pallet in a storage lane of 10126mm).
On the bottom-left, all relative pallet positions can be seen that needs to be sent by WCS according to the correct index (‘idx’).
PLTPOSCALC_MinLaneLength Excel sheet
After setting all parameters for the rack correctly, the wanted number of loads in this lane must be entered in fields of list “Number
of loads” (C6:G6). This is filled in for each Load Type.
Below, for each Load Type, a minimal lane length will be calculated and shown. The total minimal lane length is shown in table “Total
minimal Lane Length (in mm)” (B12), which is the maximum of all minimal lane lengths.
Below this, a list is given of all relative pallet positions in this lane is given.

15
Example (Load Type 1 is industrial pallet and Load Type 2 is euro pallet):

In a ‘High Throughput’ rack (no repositioning allowed), we want to calculate the minimal lane length needed to store 5 industrial or 6
euro pallets in (with overhang 50mm) in a node which is connected to a main aisle to both sides. This results in a minimal lane length
of 5952mm for the industrial pallets and 6102mm for the euro pallets. If for both load types, the condition must be fulfilled, the lane
must be at least 6102mm long.
On the bottom-left, all relative pallet positions can be seen that needs to be sent by WCS according to the correct index (‘idx’).
STORAGECONFIG Excel sheet
This sheet is not to be altered! This contains fixed configurations for all possible storages, such as the possible Storage strategies,
with corresponding flag to indicate if repositioning is allowed. Also, the ‘MarginEndstop’ and ‘MarginMain’ margins are defined here.
SHCONFIG Excel sheet
This sheet is not to be altered! This contains fixed configurations for all possible shuttle types and shuttle hardware versions. Below,
all parameters that are defined.
Length: Shuttle length.
Width: Shuttle width.
BumperLength: Length of bumper mounted on this shuttle on front and back in lane direction (important for DistOffset
calculations towards endstop plate).
PltSensorOffset: Distance from the center of the vertical pallet sensor (sensor that measures pallet while driving below it) to
the edge of the shuttle in lane direction.
CrossPltDetDis: Distance defined in shuttle configuration in software, which indicates at which detected plate length, the
shuttle will trigger on crossing plate. In other words, how long (distance in mm) does the profile sensor needs to be covered
before labelling it as a crossing plate.
ProfileSensorOffset: Distance from the center of the lane profile sensor (sensor that looks downwards and detects crossing
plates) to the edge of the shuttle in lane direction.
1. INTRODUCTION
stow Robotics provides an Application Programming Interface (API) that enables a Host
Warehouse Management System (WMS) or Warehouse Control System (WCS) to
integrate stow’s line of automated storage and transportation solutions.
1.1 Terminology
WMS Warehouse Management System
WES Warehouse Execution System
WCS Warehouse Control System
TCS Traffic Control System
STC stow Transport Control
API Application Programming Interface
URI Uniform Resource Identifier
1.2 Integration levels
stow provides a layered interface, supporting different levels of integration with
corresponding operating models:
• Task Interface. (Not Implemented)
• Mission Interface
• Auxiliary Interfaces. (Not implemented)
1.2.1 Task Interface
The Task Interface operates at the lowest level and gives the Host control over individual
shuttle movements. It is focused on tailored shuttle operations. When used to the fullest,
the Host is responsible for task assignment, battery management and idle shuttle handling
logic.
1.2.2 Mission Interface
The Mission Interface operates at a higher level and gives the Host control over specific
load movements. It is focused on tailored transportation operations. When used to the
fullest, the Host is responsible for storage strategy, priority definition, sequencing and
high-level throughput optimization logic.
1.2.3 Auxiliary Interfaces
Auxiliary interfaces can provide additional views on system state (e.g., Loads, Storage
Locations, Shuttles and other).
1.2.4 Notice
Combined usage of operating models can be tricky and should be handled with care.

4
1.3 Topology

5
2. TECHNICAL SETUP
2.1 Categories
All communication across the interface can be categorized as follows:
• Query: Interrogating the system without mutating state.
• Command: Instructing the system to perform an action (and hence mutating
state).
• Notifications: Notifying changes in the system.
Queries and Commands are initiated by the Host, Notifications are received by the Host.
2.2 Architecture
The interface is composed of a Level 2 RESTful API, as defined by the Richardson Maturity
Model1
.
• It relies on HTTP(S) as the communication protocol.
• URIs break down the service endpoint into smaller scopes of resources.
• Verbs and Status Codes are standardized, reducing unnecessary variation.
To receive the notification stream on a specific resource, a webhook with a callback URI
needs to be defined. This allows the Host to subscribe to only those notifications it is
interested in. The Host only needs to subscribe once, the subscription details will be
persisted.
The receiving endpoint is expected to always be available, but if it is not, events will be
buffered and transmitted in batch as soon as the connection can be re-established.
2.3 Generic http Status Codes
Category Status Code Description
Success 200 OK The request has succeeded. The information
returned with the response is dependent on the
method used in the request.

201 Created The request has been fulfilled and resulted in a new

resource being created.

202 Accepted The request has been accepted for processing, but
the processing has not been completed. The
request might or might not eventually be acted
upon, as it might be disallowed when processing
takes place.

204 No Content The request has been successfully processed but is

explicitly not returning any content.

Client Error 400 Bad Request The request could not be understood due to
malformed syntax. The client should not repeat the
request without modifications.

401 Unauthorized The request requires user authentication.
403 Forbidden The request was a legal request, but the server is

refusing to respond to it.
1 https://en.wikipedia.org/wiki/Richardson_Maturity_Model

6
Category Status Code Description

404 Not Found The requested resource could not be found but may

be available again in the future.

409 Conflict The request cannot be completed due to a conflict

with the current state of the resource.

Server Error 500 Internal Server

Error

A generic error message, given when no more
specific message is suitable.

501 Not Implemented The server either does not recognize the request
method, or it lacks the ability to fulfill the request.

2.4 Specification
The Interface description is available as an OpenAPI2 specification, as provided by the
Swagger toolset. Swagger adds online technical documentation, exploration and code
generation possibilities.
See https://editor.swagger.io/ for a non-related sample specification.

2 https://www.openapis.org/

7
3. MISSION INTERFACE
3.1 Basic Concepts
The Mission Interface focuses on tailored transportation and gives the Host control over
specific load movements. Responsibility for storage strategy, priority definition,
sequencing and high-level throughput optimization logic remains with the Host.
The following resources are involved
• Load: the basic unit that is stored or transported in the warehouse.
• Location: a space in the warehouse, where Loads can be placed for storage or
transferal.
• Mission: an abstraction representing the single transport movement of a Load
from a Source Location to a Destination Location.
• Vehicle: the machine used to execute the Mission. (i.e. a shuttle)
3.1.1 Location
Each source or destination location consists of 5 properties.
• Level: Like story or floor
• Lane: Number of the target lane in main lane direction
• Section: Consists of either a main lane or a group of concatenated storage
locations
• Index: Index within a section starting from the front of the warehouse
• Exit or Approach: Side of the warehouse to leave or enter a location with the load.
Either front or back
3.2 Mission States
A Mission can go through several states during its lifetime:
State Substate Description
Pending Created The Mission is ready to be executed, but a
Vehicle has not been assigned to it yet.
Assigning The selection of a Vehicle has started, but it

is not executing the Mission yet.

Assign Error Not implemented: The current state of the
warehouse does not allow the Mission to be
executed. E.g., one of the Mission Locations is
currently not available.

Retrieving Assigned The Mission is being executed, but the Load
has not been retrieved from the source
Location yet.

Awaiting Entrance
Confirmation

Optional. Waiting for source Location
confirmation.

Picking Not implemented: The Vehicle has started the
pick action to retrieve the Load from the
source Location.

8
State Substate Description

Pick Error Not implemented: The pick action could not

be completed successfully.

Storing Picked The Mission is being executed, the Load has
been retrieved from the source Location, but
has not been stored on the destination
Location yet.

Awaiting Exit Confirmation Optional. Waiting confirmation before leaving

the source location.

Awaiting Entrance
Confirmation

Optional. Waiting for destination Location
confirmation.

Dropping Not implemented: The Vehicle has started the
drop action, but it has not been completed
yet.

Drop Error Not implemented: The drop action could not

be completed successfully.

Finished Dropped The Mission has been completed
successfully. The Load has been retrieved
from the source Location and stored in the
destination Location

Cancelled The Mission has been cancelled gracefully.
Aborted The Mission has been aborted, corrective
actions might be necessary to continue
automatic operation

3.3 Mission Queries
3.3.1 Mission Status
The Mission Status query returns current status information of a specific mission, or of all
current missions.
3.4 Mission Commands
3.4.1 Create Mission
The Create Mission command will create a new mission, ready to be executed.
The Mission Id must be unique among all missions.
The minimal information needed to perform a mission is the destination location and a
source. The mission source can either be a source location, and in some cases the vehicle
that is currently carrying the load or the load id.
The Vehicle id can be specified if you want a specific vehicle to execute the mission. This
is the only case when a mission can have a vehicle in its created substate.
The Load id is only used for information and temporary tracking. We recommend
specifying a unique load id in the create mission request.
The system keeps track of all loads in transit, in certain situations, it may be possible to
infer the vehicle id from the load id alone. This may be useful in case of error handling and
missions that could not be completed.
The new mission will be in the Created substate.

9
If a vehicle is available to handle the mission, the mission will quickly go from substate
Created to Assigning. If no vehicle is immediately available, the mission will stay in Pending
until a vehicle is assigned.
3.4.2 Update Mission
The Update Mission command allows the Host to update some properties of the mission.
Depending on the current state of the mission, certain properties may not be allowed to
change.
Priority
Priority 1 is the highest and 10 is the lowest. The priority of a mission can be changed if the
mission is not yet in state Retrieving.
Destination
The destination of a mission can be changed if the vehicle has not yet dropped its load.
Vehicle
The vehicle of a Mission can be changed if the mission is not yet in state Retrieving.
3.4.3 Cancel Mission
The Cancel Mission command allows to cancel a mission when the load has not been
retrieved from the source location. Trying to cancel a mission when a load has already
been retrieved is not allowed.
The cancelled mission will be in the Cancelled state.
3.4.4 Abort Mission
The Abort Mission command allows to force mission completion, indicating that the
mission should be terminated. Manual actions may need to be performed to continue
automatic operation. Aborting a mission when a load was picked will result in the shuttle
not being available for any future missions. Manual actions are needed to release the
shuttle for future executions.
This command is intended as error handling and should not be needed for standard
operation.
The aborted mission will be in the Aborted state.
If a mission is aborted when the load is already retrieved the shuttle will stop its execution.
A new mission will need to be created to get the shuttle with load moving again. This new
mission request does not need a source location but a shuttle or load id. Either of those
two will work. This new mission request can have a totally different destination and
priority.

10
3.5 Mission Webhooks
The Host can be notified every time a mission is created or updated. The notifications are
sent over a webhook, also known as http callback.
The contract with exact payload to be implemented can be found in the swagger
documentation of the API. Code generation tools should be used to generate the
consumer side of the webhooks including the webhook notification contract.
Registering a webhook is done by specifying which address and context you are
interested in.
Each webhook notification contains a unique Notification id, context type (e.g., Mission),
notification type (e.g., MissionCreated) and timestamp in ISO-8601 format when the event
occurred. The payload of the notification depends on the notification type.
3.5.1 Example notifications
Example notifications expected to be received by a webhook consumer in case of a
successful Mission.
MissionCreated, MissionAssigning, MissionAssigned, MissionPicked, MissionDropped.
3.5.2 Configuration
It is possible to configure the webhook subscriber, this configuration is shared between
all webhook registrations. It isn’t possible to configure an individual webhook registration.
Connection timeout, discard timeout and max batch size can all be configured.

11
4. SHUTTLE INTERFACE
4.1 Shuttle feedback
Over time the API will send feedback messages about the states of the shuttles.
State Description
VehicleBatteryLevelChanged Contains the battery level for a specific vehicle

12
5. HEALTH CHECK INTERFACE
Health checks are a fast and simple way of reporting the health of the application and
application infrastructure components.
Health checks are informational and are not required for normal operation of the system.
5.1 Liveness probe
The Liveness probe can be used to verify that the application is running and active.
Request: GET /health/live
Response: 200 OK

408 Request Timeout
503 Service Unavailable

5.2 Readiness probe
In some cases, the application can be running and responsive, but temporarily unable to
serve requests, for example during start-up or when a dependency is unhealthy. A
readiness probe can be used to detect these situations and will respond with all unhealthy
or degraded dependencies.
Request: GET /health/ready
Response: 200 OK

408 Request Timeout
503 Service Unavailable

13
6. MISSION USE CASES
Atlas® Load movement from location to storage lane
6.1 Way of work
Storing a load in a storage lane is preferably done by giving the requested storage
distance within the mission. The shuttle will then try to store the load at the requested
distance. The shuttle will use its sensors to finetune the storage distance in relation to the
last load already present in the lane. If the shuttle is able to store the load within a
predefined tolerance window, the real storage distance will be sent in the
‘MissionDropped’ feedback. This distance should be stored in a way it can added in the
mission to pick this load again. The distance is always given relative to the ‘FRONT’ of the
warehouse.

14
6.2 Messages
Nr. HOST -> ASTC ASTC -> HOST
1 Create Mission
2 MissionCreated
3 MissionAssigning
4 MissionAssigned
5 MissionPicked
6 MissionDropped
6.2.1 Mission Creation
{
"id": 21,
"source": { "level": 1, "lane": 2, "section": 11, "distance": 600, "exit": "Front" },
"destination": { "level": 1, "lane": 2, "section": 4, "distance": 1500, "approach": "Back" }
}
6.2.2 Feedback Mission Created
{
"notificationType": "MissionCreated",
"Payload": { "MissionId": "21", "Priority": 5,
"Source": { "Exit": "Front", "Level": 1, "Lane": 2, "Section": 11, "Distance": 600 },
"Destination": { "Approach": "Back", "Level": 1, "Lane": 2, "Section": 4, "Distance": 1500
},
"LoadId": "load-9-9ijcvoeoa00lrqmkq4ohr"
},
"NotificationId": "b0a788d0-ab4e-4b72-889f-7b1b742841c0",
"TimeStamp": "2023-06-29T12:12:55.6144034Z",
"ContextType": "Mission"
}
6.2.3 Feedback MissionAssigning
{
"notificationType": "MissionAssigning",
"Payload": { "MissionId": "21", "VehicleId": "1" },
"NotificationId": "e9e1eb98-31de-42fe-9826-bc8d1332868e",
"TimeStamp": "2023-06-29T12:12:55.9760617Z",
"ContextType": "Mission"
}

15
6.2.4 Feedback MissionAssigned
{
"notificationType": "MissionAssigned",
"Payload": { "MissionId": "21", "VehicleId": "1" },
"NotificationId": "df76d11a-692b-4c6d-9068-aa515d464f51",
"TimeStamp": "2023-06-29T12:12:57.8787298Z",
"ContextType": "Mission"
}
6.2.5 Feedback MissionPicked
{
"notificationType": "MissionPicked",
"Payload": {
"MissionId": "21",
"VehicleId": "1",
"Source": { "Exit": "Front", "Level": 1, "Lane": 2, "Section": 11, "Distance": 600 },
"LoadId": "load-9-9ijcvoeoa00lrqmkq4ohr"
},
"NotificationId": "cdef1010-52b9-42a4-bcc7-472bf9bd2590",
"TimeStamp": "2023-06-29T12:13:10.2863457Z",
"ContextType": "Mission"
}
6.2.6 Feedback MissionDropped
{
"notificationType": "MissionDropped",
"Payload": { "MissionId": "21", "VehicleId": "1",
"Destination": { "Approach": "Back", "Level": 1, "Lane": 2, "Section": 4, "Distance": 1500 },
"LoadId": "load-9-9ijcvoeoa00lrqmkq4ohr"
},
"NotificationId": "da8c577f-db4b-441b-a4a1-3ac95bd172e0",
"TimeStamp": "2023-06-29T12:13:22.3825944Z",
"ContextType": "Mission"
}
Remark: the yellow marked distance is the real feedback, coming from the shuttle. This
distance should be given into the mission when this load needs to be retrieved again.

16
6.3 Atlas® Load movement from storage lane to location
6.3.1 Mission Creation
{
"id": 22,
"source": { "level": 1, "lane": 2, "section": 4, "distance": 1500, "exit": "Back" },
"destination": { "level": 1, "lane": 2, "section": 11, "index" : 1, "approach": "Front" }
}
Remark: the yellow marked distance is the real feedback that came from the shuttle
during load storage. It is important for the reliability of the system to specify it correctly
into the mission creation message.
6.4 Atlas® Load movements with confirmation requests
6.4.1 Late decision at destination
Typically, if more than one shuttle is sent towards a storage lane, it cannot be predicted
in which sequence they will arrive. Therefor the principle of “late decision” is introduced.
With this principle it becomes possible to update the mission at the moment that the
shuttle is positioned in front of the lane. The ASTC will ask “confirmation” at the right
moment in such a case.
The illustration shows a typical example of the use case: the situation of the destination
lane has changed during the shuttle movement from source to destination. Initially
‘Distance X’ was expected but finally ‘Distance Y’ should be sent.

17

18
Mission creation
{
"id": 105,
"source": { "level": 1, "lane": 13, "section": 1, "distance": 600, "exit": "Back" },
"destination": { "level": 1, "lane": 15, "section": 1, "distance": 2000, "approach": "Back",
"entranceConfirmationRequired" : true }
}
Feedback MissionAwaitingDestinationEntryConfirmation
{
"notificationType": "MissionAwaitingDestinationEntryConfirmation",
"Payload": { "MissionId": "105", "VehicleId": "1",
"Destination": { "Approach": "Back", "Level": 1, "Lane": 15, "Section": 1, "Distance": 2000 },
"LoadId": "load-6-8o5pemlcd2vqj4i3zu8v4"
},
"NotificationId": "32b1a5ce-c411-4438-8ce1-8f5d9ed8087a",
"TimeStamp": "2023-06-30T14:14:27.8523623Z",
"ContextType": "Mission"
}
Update the Mission
{
"destination": { "level": 1, "lane": 15, "section": 1, "distance": 2500, "approach": "Back" }
}
Feedback MissionDestinationChanged
{ "notificationType": "MissionDestinationChanged", "Payload": { "MissionId": "105", "Destination": { "Approach": "Back", "Level": 1,
"Lane": 15, "Section": 1, "Index": 0, "Distance": 2500 } }, "NotificationId": "f0f3c9a1-fe41-40f5-bfc4-4b3600117f6f", "TimeStamp":
"2023-07-12T15:35:08.6601516Z", "ContextType": "Mission" }
Confirm the Mission
Endpoint
/api/v0.1/missions/{missionId}/confirmenterdestination
Where {missionId} is the id of the mission that you want to confirm
Response:
202 Accepted: The request to confirm source entry has been accepted.
400 Bad Request: When the request is not accepted. For example when the mission is
already finished.

19
6.4.2 Late decision at source
Comparable with the late decision at destination, the same principle can be used at the
source location. The reason to use this feature is the same as using it at the destination:
the situation at the source location can be different between the moment of mission
generation and the moment of arriving in front of the source location.

Mission Creation
{
"id": 106,
"source": { "level": 1, "lane": 13, "section": 1, "distance": 600, "exit": "Back",
"entranceConfirmationRequired" : true },
"destination": { "level": 1, "lane": 15, "section": 1, "distance": 2000, "approach": "Back" }
}
Feedback MissionAwaitingSourceEntryConfirmation
{
"notificationType": "MissionAwaitingSourceEntryConfirmation",
"Payload": { "MissionId": "106", "VehicleId": "1",
"Source": { "Exit": "Back", "Level": 1, "Lane": 13, "Section": 1, "Distance": 600 },
"LoadId": "load-1-cexcu9mysupsqa78g9mo5" },
"NotificationId": "a43e1f96-ca11-41fb-9b64-3c88bc6aeb3f",
"TimeStamp": "2023-07-04T15:20:49.2687826Z",
"ContextType": "Mission"
}
Update the Mission
{
"source": { "level": 1, "lane": 14, "section": 1, "exit": "Back", "distance": 600 }
}
Feedback MissionSourceChanged
{ "notificationType": "MissionSourceChanged", "Payload": { "MissionId": "1", "Source": { "Exit": "Back", "Level": 1,
"Lane": 14, "Section": 1, "Index": 0, "Distance": 600 } }, "NotificationId": "6dd89281-815e-4ad6-8e34-
3a9640218139", "TimeStamp": "2023-07-12T14:39:21.8732471Z", "ContextType": "Mission" }
Confirm the Mission
Endpoint

20
/api/v0.1/missions/{missionId}/confirmentersource
Where {missionId} is the id of the mission that you want to confirm
Response:
202 Accepted: The request to confirm source entry has been accepted.
400 Bad Request: When the request is not accepted. For example when the mission is
already finished.

21
6.5 Atlas® Load movement from location to storage lane on another level
In this use case the steps are described to a shuttle with a load to another level.
Note: This can only be done if the shuttle can move onto the lift.
6.5.1 Picking and docking
The payload is similar to a default movement. Except this time we have to set the
dockAtDestination-flag to true. It is also required to pass the loadId. If this is not done, the
aSTC will lose track of which load is on the shuttle that is docked and the software will
throw an error on the undocking-mission.
Note: The destination distance is 800 here, which is the calculated center of the lift.
Mission creation
{
"id": 204,
"source": { "level": 1, "lane": 3, "section": 3, "distance": 600, "exit": "Front" },
"dockedAtSource": false,
"destination": { "level": 1, "lane": 20, "section": 6, "distance": 800, "approach": "Front" },
"dockAtDestination": true,
"Load" : { "Id": "LOAD-1" }
}
The feedback is similar to the previous use cases. The task goes through the assigning,
assigned, picked and dropped state.
6.5.2 Undocking and dropping
At this moment the shuttle is still docked under the load. Hence we cannot send another
shuttle to the load and we need to pass the Vehicle-Id of the shuttle under the load in the
mission. On top of that we need to let the software know the shuttle is still docked, so we
flag dockedAtSource to true. The last thing we need to define is the Load-Id that was
specified in the docking mission for verification purposes.
Mission Creation
{
"id": 205,
"source": { "level": 7, "lane": 20, "section": 6, "distance": 800, "exit": "Front" },
"dockedAtSource": true,
"destination": { "level": 7, "lane": 10, "section": 7, "distance": 600, "approach": "Back" },
"dockAtDestination": false,
"vehicle": {"id": 1 },
"Load" : { "Id": "LOAD-1" }
}

22
6.6 Atlas® Roaming a shuttle to another level
When a new shuttle is added, or there is a lot of work on another level, it could be possible
that a shuttle needs to move to another level. For this the WCS can perform shuttle
roaming by the use of 2 tasks docking and undocking.
Note: These commands are only valid for a shuttle without a load, in other cases the
missions interface should be used (see previous chapter).
6.6.1 Dock Shuttle
For this a docking node is required. A docking node could be a vertical conveyer (used in
this example). Docking means the shuttle needs to apply his brakes when on this node.
Payload
We want to send shuttle with id 1 to the lift (destination). This is a POST request to:
/api/v0.1/tasks/dock
{
"id": 3,
"destination": { "level": 7, "lane": 20, "section": 6, "index": 1, "distance": 600, "approach": "Front",
entranceConfirmationRequired": false, "entranceConfirmed": true },
"vehicle": { "id": 1 },
"priority": 10
}
Feedback TaskStateChanged: NotAssigned
The task was received successfully.
{
"notificationType": "TaskStateChanged",
"Payload": { "TaskId": "3", "State": "NotAssigned" },
"NotificationId": "3e83f8bf-ed98-44b2-851b-84957e07c347",
"TimeStamp": "2023-07-31T14:09:04.7837543Z",
"ContextType": "Task"
}
Feedback TaskStateChanged: Assigned
{
"notificationType": "TaskStateChanged",
"Payload": {
"TaskId": "3",
"State": "NotAssigned"
},
"NotificationId": "3e83f8bf-ed98-44b2-851b-84957e07c347",
"TimeStamp": "2023-07-31T14:09:04.7837543Z",
"ContextType": "Task"
}
Feedback TaskStateChanged: MovingShuttle
{
"notificationType": "TaskStateChanged",
"Payload": { "TaskId": "3", "State": "MovingShuttle" },
"NotificationId": "5bdec871-159e-43d6-9cc9-513829ea0aff",
"TimeStamp": "2023-07-31T14:09:06.778574Z",
"ContextType": "Task"
}

23
Feedback TaskStateChanged: WaitForHandover
The shuttle is now at the destination and initiating the handshake.
{
"notificationType": "TaskStateChanged",
"Payload": { "TaskId": "3", "State": "WaitForHandover" },
"NotificationId": "3c31c6ba-302e-4fd4-937c-330c5690cc05",
"TimeStamp": "2023-07-31T14:09:15.7906275Z",
"ContextType": "Task"
}
Feedback TaskStateChanged: Done
This means the handshake is complete and the shuttle is now docked in the destination.
{
"notificationType": "TaskStateChanged",
"Payload": { "TaskId": "3", "State": "Done" },
"NotificationId": "443f2351-daaa-42c8-b96c-b9a1ef604fc4",
"TimeStamp": "2023-07-31T14:09:28.7749684Z",
"ContextType": "Task"
}
6.6.2 Undock Shuttle
In this example the lift was moved to level 7.
Payload
The destination property is optional. If the WCS does not specify a destination property,
the aSTC will move the shuttle automatically to a parking spot.
This is a POST request to: /api/v0.1/tasks/undock
{
"id": 201,
"source": { "level": 7, "lane": 20, "section": 6, "index": 1, "distance": 600, "exit": "Front",
"entranceConfirmationRequired": false,
"entranceConfirmed": true,
"exitConfirmationRequired": false,
"exitConfirmed": true
},
"vehicle": { "id": 1 },
"priority": 10
}
Feedback TaskStateChanged: Undocking
{
"notificationType": "TaskStateChanged",
"Payload": { "TaskId": "201", "State": "Undocking" },
"NotificationId": "84882fca-ec9c-46a7-88fc-a0f3a3b31c8f",
"TimeStamp": "2023-08-10T13:54:20.8256322Z",
"ContextType": "Task"
}

24
6.7 escala® Load movement to a Universal Picking Station
6.7.1 Description
This chapter describes what mission the WCS needs to send to bring a load towards the
picking location of the Universal Picking Station (UPS) and what mission send to bring the
load back to the warehouse. For optimization purposes the WCS can send the mission for
leaving the UPS immediately after the mission report that the shuttle has arrived at the
picking station. There is a low level handshake in place that holds the shuttle as long as
the picking is active.
6.7.2 Bringing a load towards the UPS
{
"id": 1000,
"load": { "id": "Load1005" },
"source": { "level": 2, "lane": 11, "section": 17, "index": 2, "exit": "Front" },
"destination": { "level": 2, "lane": 2, "section": 12 }
}
Remarks:
• In an escala system we always work with indexes and not distances (cfr. ATLAS system)
• In the destination there is no need to include the approach and index fields as they do not mean
anything within the UPS setup
• The feedback messages are the same as already described in the previous chapters:
"MissionCreated", "MissionAssigning", "MissionAssigned", "MissionPicked", "MissionDropped"
• MissionDropped does mean here that the shuttle has arrived at the picking location of the UPS;
please not that the shuttle does not drop the load; the load remains on the shuttle.
6.7.3 Sending the load back to the warehouse
{
"id": 1001,
"load": { "id": "Load1005" },
"destination": { "level": 2, "lane": 12, "section": 17, "index": 2, "approach": "Front" }
}
Remarks:
• A source cannot be included in the mission because there is no need to execute a pickup (load is
still on the shuttle)
• Instead of sending a source, the WCS should inform the system with the correct load identification.
• The feedback messages are the same as already described in the previous chapters:
"MissionCreated", "MissionAssigning", "MissionAssigned", "MissionPicked", "MissionDropped"
Heartbeat signal
Description
This signal is used to detect communication losses. The handshake signals described in the following chapters are only relevant if the
communication is established. A standard heartbeat signal will toggle every second. This means that the absence of an upward flank
for x number of seconds from the heartbeat signal needs to be seen as ‘communication down’.
Timing
Toggling of the signal is done every second.

Timing Description
T1 Heartbeat is on
T2 Heartbeat is off
T3 No flank detected. Communication lost
T4 Heartbeat is on
T5 Heartbeat is off
T6 Heartbeat is on

3
Handshake with a fire door
Description
A fire door will quite often not support any automation interface. It will just report that it’s fully opened (optionally). Therefore, aSTC
can only check if the door is open and cannot report that a shuttle is driving through the fire door. This means that, in order to give
the shuttle a fair chance of evacuating the door area in case of fire, a predefined timeout will need to be applied before the fire door
can close.
Signal overview

Timing
A fire alarm is mostly generated by a central fire control unit. If the fire alarm is set, the aSTC will suppose that all the fire doors are
closed. The aSTC will not allow any shuttle to pass a fire door anymore and will ensure not to block a fire door from closing.
Optionally each fire door can communicate that it is fully opened. If not opened the aSTC will make sure that the shuttles will not
pass this specific door anymore. A fire door that is not fully opened while the central fire alarm is not raised usually means a
malfunctioning of the specific fire door (sensor / spring). We recommend detecting this on a centralized reporting system so the
door can be repaired. Disturbing the automated shuttle system for this does not make a lot of sense.

4
Handshake with a (high-speed)door
Description
This interface is meant for a door in the trajectory of the shuttles. Typically a shuttle will arrive in the neighbourhood of the door and
will then request to pass the door. After passing, the door will usually close again.
Signal overview

Signal
Number

Name Description

1

Shuttle is passing or
wants to pass the door

aSTC sets this signal if a shuttle wants to pass the door or when a shuttle is
passing the door. The door must remain open as long as this signal is set!
2 Opened The door controller sets this signal when the door is fully opened.
3 Error

The door controller has an error. ASTC will re-route all shuttles to prevent them
passing this door. Remark: re-routing may not be possible due to warehouse
design.

5
Timing

Timing Description
T1
Shuttle reaches the door zone and requests to open the door. Shuttle will stop (between T1 and T2) if the door is
not open in time.
T2
Door controller informs aSTC that the door is fully opened. The shuttle will start driving through the door and
cannot be stopped anymore.
T3 The shuttle as completely passed the door. The door is allowed to close again.
T4 The door starts to close.

6
Handshake conveyor to conveyor
Description
This section describes the handshake between 2 conveyors. Conveyor 1 wants to give a load to conveyor 2. The conveyors can be
both stationary or can be both part of a shuttle. Also the combination of a stationary conveyor and a conveyor positioned on a
shuttle follows the same interface.
Signal Overview

Signal
Number

Name Description

1 Conveyor 1 OK

The OK signal gives the error status of conveyor 1. When there is NO error the OK
signal is high.

2 Conveyor 2 OK

The OK signal gives the error status of conveyor 2. When there is NO error the OK
signal is high.

3

Conveyor 1
READY

Conveyor 1 is ready to transfer its load.
4 Conveyor 2 ASK Conveyor 2 is ready to receive the load.

7
Timing
The OK signals of the conveyors are high when there is no error.
The load arrives on conveyor 1 and is ready to be transferred to conveyor 2.
Conveyor 1 sets its READY signal.
When Conveyor 2 is ready to receive the load it sets its ASK signal.
Both conveyors start operating. The ASK Signal must stay high until the conveyor 2 has received the load.
As long the OK signals are high, the transfer between conveyor 1 and conveyor 2 goes on. If one of the 2 conveyors drops
its OK signal, both conveyors need to stop immediately.
The transfer continues until the Ask Signal of conveyor 2 becomes low. This means that it has received the load successfully.
Important Remark: the OK signals must stay high; this is a condition to have a transfer finished.

Timing Description

8
T1 Conveyor 1 has load ready to be transferred
T2 Conveyor 2 is ready to receive the load; both conveyors start running
T3 Conveyor 1 has an error; both conveyors stop running
T4 Error is solved on conveyor 1; both conveyors start running again
T5 Conveyor 2 has an error; both conveyors stop running
T6 Error is solved on conveyor 2; both conveyors start running again
T7 Conveyor 2 successfully received the load. Both conveyors stop running, transfer is complete.

9
Handshake for load pickup
Description
This interface is used whenever a shuttle wants to pick a load from an automated system. The supplying system can be a lift, a
conveyor, a robot cell or ... . This interface ensures a safe transfer of the load and minimizes the risk of material damage.7
Signal Overview

Signal
Number

Name Description

1 Pick Request The STC informs the ‘supplying system’ that a shuttle wants to pick the load.
2 Pick Enable The ‘supplying system’ allows the pick action.
3 Pick Busy The STC informs the ‘supplying system’ that a shuttle is picking a load.
4 Error

The ‘supplying system’ informs ASTC that it is in error state. If a shuttle is present it will be
stopped. This is not a ‘performance level safety signal’. It is only meant to prevent material
damage.

5 Not In Zone The shuttle is NOT at the ‘supplying system’ location.
6

Not In Zone
Loaded

The loaded shuttle is NOT at the ‘supplying system’ location.

10
Timing

Timing Description
T1 Shuttle arrives at pick point and request to pick the load.
T2 The ‘supplying system’ makes sure that it is safe to allow the pick and enables the pick action.
T3 The shuttle starts its pick routine.
T4 The shuttle is loaded.
T5 The shuttle is not picking anymore and has left.

11
Handshake for load dropoff
Description
This interface is used whenever a shuttle wants to drop a load on an automated system. The receiving system can be a lift, a
conveyor, a robot cell or ... . This interface ensures a safe transfer of the load and minimizes the risk of material damage.
Signal overview

Signal
Number

Name Description

1

Drop
Request

The STC informs the ‘receiving system’ that a shuttle wants to drop the load.

2 Drop Enable The ‘receiving system’ allows the drop action.
3 Drop Busy The STC informs the ‘receiving system’ that a shuttle is dropping a load.

4 Error

The ‘receiving system’ informs ASTC that it is in error state. If a shuttle is present it will be
stopped. This is not a ‘performance level safety signal’. It is only meant to prevent material
damage.

5 Not In Zone The shuttle is NOT at the ‘receiving system’ location.

12
6

Not In Zone
Loaded

The loaded shuttle is NOT at the ‘receiving system’ location.

Timing

Timing Description
T1 Shuttle arrives at drop point and request to drop its load
T2 The ‘receiving system’ makes sure that it is safe to allow the drop and enables the drop action.
T3 The shuttle starts its drop routine.
T4 The shuttle is not loaded anymore, load is dropped on the receiving system.
T5 The shuttle is not dropping anymore and has left.

13
Handshake with flipper device
Description
A flipper is a device that is typically used in an e.scala system. More specific: a flipper is used in case more then one tote is carried by
a shuttle. In this case the flipper can reorganize the pile of totes and make sure that the desired tote is on top of the pile after the
‘flipping process’.

Working principle: a flipper device is always mounted in a double-deep warehouse lane. A shuttle will drop a pile of totes on location
B. The flipper will create a correct ‘flipped’ pile on location A. During the ‘flipping process’ the shuttle will move to location B and will
wait until the flipper is finished before picking the ‘flipped’ pile of totes again.
Signal overview
An empty shuttle is always allowed to drive into the flipper area because there is no possibility of damaging either the shuttle or
either the flipper.

14
Signal
Number Name Description
1

Drop
Request

The STC informs the flipper that a shuttle is in the neighborhood and that this shuttle wants
to drop a load on location B of the flipper.
2 Drop Enable The flipper allows the drop action.
3 Drop Busy The STC informs the flipper that a shuttle is dropping a load.
4 Pick Request The STC informs the flipper that a shuttle wants to pick the load.
5 Pick Enable The flipper allows the pick action.
6 Pick Busy The STC informs the flipper that a shuttle is picking a load.
7 Error

The flipper informs ASTC that it is in error state. If a shuttle is present in the flipper it will be
stopped. This is not a ‘performance level safety signal’. It is only meant to prevent material
damage.

8 Not In Zone The shuttle is NOT at the flipper location.

15
9

Not In Zone
Loaded

The loaded shuttle is NOT at the flipper location

Remark: after the drop action, the empty shuttle will move from flipper location B to flipper location A. It is supposed that
this movement is always allowed and that this cannot cause any damage to the shuttle and the flipper.
Timing

Timing Description
T1 Shuttle arrives at flipper point and request to drop its load
T2 The flipper makes sure that it is safe to allow the drop and enables the drop action.
T3 The shuttle starts its drop routine

16
T4
The shuttle is not dropping anymore and is on a position that allows the flipper to start ‘flipping’. (the shuttle starts
driving empty to flipper location A)
T5 Shuttle arrives at pick point and request to pick the load.
T6
The flipper makes sure it has finished its ‘flipper action’ and that it is safe to allow the pick and enables the pick
action.
T7 The shuttle starts its pick routine.
T8 The shuttle has left the flipper location.

17
Handshake with a lift
Description
We need to distinguish two different interfaces: an interface for picking and/or dropping a load from/into the lift and an interface for
moving a shuttle (roaming) from one rack level to the other level. It is important to realize that every level requires its own
handshake signals (e.g. Pick Request Level 1, Drop Request Level 5 etc.). The lift controller should ensure that the lift is always on the
correct level before sending the corresponding enable signal!
Handshake for picking a load from a lift
The handshake is identical to the one discribed in chapter Handshake for load pickup. As an example, the signal list for a 3 level
warehouse:
1. Pick Request Level 0 2. Pick Enable Level 0 3. Pick Busy Level 0 4. Not In Zone Level 0 5. Not In Zone Loaded Level 0
6. Pick Request Level 1 7. Pick Enable Level 1 8. Pick Busy Level 1 9. Not In Zone Level 1 10. Not In Zone Loaded Level 1
11. Pick Request Level 2 12. Pick Enable Level 2 13. Pick Busy Level 2 14. Not In Zone Level 2 15. Not In Zone Loaded Level 2 16.
Error
Handshake for dropping a load into a lift
The handshake is identical to the one discribed in chapter Handshake for load dropoff. As an example, the signal list for a 3 level
warehouse:
1. Drop Request Level 0 2. Drop Enable Level 0 3. Drop Busy Level 0 4. Not In Zone Level 0 5. Not In Zone Loaded Level 0
6. Drop Request Level 1 7. Drop Enable Level 1 8. Drop Busy Level 1 9. Not In Zone Level 1 10. Not In Zone Loaded Level 1
11. Drop Request Level 2 12. Drop Enable Level 2 13. Drop Busy Level 2 14. Not In Zone Level 2 15. Not In Zone Loaded Level 2
16. Error
Handshake for Shuttle roaming
This interface is used to move a shuttle from one level to another. This is what we call shuttle roaming. Shuttle roaming can be done
with a loaded or an empty shuttle. In most cases the lift is also used for moving loads, therefor this interface is mostly combined with
the interfaces described in chapters Handshake for picking a load from a lift and Handshake for dropping a load into a lift. The
signals “Not In Zone”, “Not In Zone Loaded” and “Error” are then redundant and will therefore only exist once.
Remark: all the signals, except the Error signal, do exist per warehouse level. (see also previous lift chapters).
Signal Overview

18
Signal
Number

Name Description

1

Request Entrance
Empty Level x

An empty shuttle at level x wants to drive into the lift.

2

Request Entrance
Loaded Level x

A loaded shuttle at level x wants to drive into the lift.

3

Entrance Enable
Level x

The lift allows the shuttle at level x to enter the lift. The allowance takes the correct
request (empty or loaded) into account.
4 Request Exit Level x A shuttle at level x wants to drive out of the lift.
5 Exit Enable Level x The lift allows the shuttle at level x to drive out.
6 Not In Zone Level x The shuttle is NOT at the lift location at level x.
7

Not In Zone
Loaded Level x

The loaded shuttle is NOT at the lift location at level x.

8 Error

The lift informs ASTC that it is in error state. If a shuttle is present it will be stopped.
This is not a ‘performance level safety signal’. It is only meant to prevent material
damage..

19
Timing

Timing Description
T1 Shuttle arrives at lift and requests access.
T2 The lift allows the shuttle to enter.
T3 The shuttle starts driving into the lift.
T4 The shuttle has arrived into the lift.
T5 Shuttle wants to leave the lift.
T6 The lift allows the shuttle to leave.
T7 The shuttle has left the lift.

20
Handshake with Universal Picking station (UPS)
Description
This interface is used to make sure that a shuttle can safely enter and leave the UPS.
Signal Overview

Signal
Number

Name Description

1

Request
Entrance

A shuttle wants to drive into the UPS picking location.

2

Entrance
Enable

The UPS allows the shuttle to enter the UPS.

3

Request
Exit

A shuttle wants to drive out of the UPS.
4 Exit Enable The UPS allows the shuttle to drive out.

21
5

Not In
Zone

The shuttle is NOT at the UPS picking location.

6 Error

The UPS informs ASTC that it is in error state. If a shuttle is present it will be stopped. This is not
a ‘performance level safety signal’. It is only meant to prevent material damage..

7

Picking
Active

The WCS informs the UPS that picking is active. On the positive edge of this signal the UPS
should take all precautions to enable the picking process. The UPS should also make sure to
keep the Exit Enable signal false as long as ‘Picking Active’ is true.

Timing

Timing Description
T1 Shuttle arrives at UPS picking point and requests access.

22
T2 The UPS allows the shuttle to enter.
T3 The shuttle starts driving into the UPS.
T4 The shuttle has arrived into the UPS picking point.
T5 The WCS informs UPS that picking wants to start
T6
Shuttle wants to leave the UPS picking point. (there is a possibility that this signal is already raised before picking is
finished. This is for timing optimisations.)
T7 The WCS informs UPS that picking is done.
T8 The UPS allows the shuttle to leave.
Introduction to aSTC
ASTC is short for Advanced Stow Traffic Controller and is a bridge between the Warehouse Control System (WCS) and Movu's Atlas
or EScala shuttle. It was created with the purpose to have an easy interface to control the load within a warehouse.
Architectural overview
The software resides in the automated ecosystem as a layer between the WCS and the shuttles. A HMI is also provided that can send
tasks to the aSTC directly and also delivers monitoring of the system. Another core functionality of the software is to integrate with
the ProfiNet to communicate with other components within the system (horizontal conveyers, vertical conveyers and so on).

Responsibilities of the aSTC
The primary role of the ASTC system is to execute tasks. These tasks involve moving loads to and from specified locations. To
perform these instructions successfully, other key concepts are taken into account.
Path planning: Decision on which route the shuttle should take
Traffic control: Decision which shuttle should get access to a node first, when a shuttle has to wait, ..
Battery management: Ensuring all shuttles remain charged enough to perform tasks
Idle management: Positioning of the shuttles when there are no tasks

4
Interfacing with external components: communication for example with a conveyer to pick and drop pallets.
Safety

5
Integrating aSTC
Hardware
The following image shows an overview of the Atlas 2D hardware setup that is delivered by Movu.

6
ASTC Cabinets
The system is provided as a standalone cabinet. In this cabinet all required hardware for operating and monitoring the warehouse is
included:
Controller (Beckhoff Target)
Touch panel for Movu visualization (HMI)
Traffic light for quick overview of operational state
Reset, stop, emergency , manual / automatic buttons
Moxa router for remote access to and logging off shuttles.
The integrator needs to provide some hardware as well: - Ethernet cable with access to internet - Ethernet cable for ProfiNet
communication with WCS - Cable for connection to safety relay for safety handshaking - 230V + PE power connection
Optionally an extra traffic light can be installed for quick overview of the system status.
The hardware can also be delivered on a mounted plate. For more details, refer to the document.
Wi-Fi cabinets
Movu has chosen for the Wi-Fi Mesh technology that gives better Wi-Fi coverage in the warehouse. To achieve this coverage,
multiple Wi-Fi Mesh nodes must be spread throughout the warehouse. Movu determines on what locations the nodes are placed,
taking possible load and physical setup into account. Each Wi-Fi Mesh node is mounted into a Wi-Fi cabinet together with a power
connector and a PSU. The Wi-Fi antenna is mounted on top of this cabinet. This is delivered by Movu on product installation.
Network integration
To integrate the equipment into the facility IP network, Movu uses its own subnet. This approach makes the network configuration of
the equipment much easier and only one IP address is required for the WAN side. The appropriate forwarding is provided by Movu
to forward the ports internally to the aSTC and vice versa. The following illustration depicts Movu network setup.

7
For logging to the cloud and remote support by Movu, internet access is strongly recommended. Movu uses the IXON router to
connect with VPN to this subnet to provide remote support to the shuttles, Moxa router and aSTC.
For extra security the firewall can be configured to only allow communication from the WSC and vice versa.
More details on the subnet and WiFi solution is described in the document 'movu-network-solution.pdf'.
Communication types
Shuttle communication
The communication between the shuttles and aSTC is done wireless and standard Wi-Fi networks are used. The aSTC communicates
with the MQTT communication protocol (Message Queuing Telemetry Transport). Clients, such as the shuttles and ASTC, connect to
the broker to publish or subscribe to topics. Messages published to a topic are delivered by the broker to all subscribed clients that
have expressed interest in that topic.
ProfiNet communication
To communicate with other components than the shuttles, a connection to the ProfiNet is required. This is done over ethernet with
the TCP/IP protocol. For this specific handshake contracts are defined, which is explained in chapter [ASTC handshake interfaces]
(#ASTC handshake interfaces).
WCS communication
The WCS needs to be able to send instructions to the aSTC and recieve feedback from it. For this ethernet is used as well. Details
about the communication are explained in chapter 'Communicating with aSTC'.
Warehouse configuration
To start controlling the warehouse and shuttles, the aSTC needs to have a representation of the physical installation. For this a
configuration file is used that is build in Microsoft Excel. Movu provides this file when installing the system.
More information on how this works is found in the document 'movu-warehouse-definition.pdf'.

8
Communicating with aSTC
REST API Interface
Movu provides a REST API that enables a host WMS or WCS to integrate Movu’s line of automated storage and transportation
solutions. It is an extra layer between the WMS or WCS and the aSTC. Currently the aSTC can queue up to 250 missions at once.
Mission Interface
The Mission Interface operates at a higher level and gives the Host control over specific load movements. It is focused on tailored
transportation operations. When used to the fullest, the Host is responsible for storage strategy, priority definition, sequencing and
high-level throughput optimization logic.
For a full detailed explanation, refer to the document 'API-Integration-Interface-spec.pdf'.
Webhooks
The ASTC provides feedback about the mission states. Integrators can subscribe on this feedback through registering an endpoint in
IRIS and recieve updates about the missions.
Mission parameters
Index and distance
When sending commands to the aSTC the WCS needs to indicate where to pick up the load and where to drop the load. In the
EScala warehouse these locations are hard defined, which means the WCS can send the Index parameter in the requests. For an Atlas
2D warehouse this isn't possible since the WCS can decide to stock different types of pallets within the same lane, this improves
flexibility of the system. From this view the amount of indexes can vary depending on what type of load is added to the lane.
Therefor the index can not be used for Atlas 2D warehouses and the WCS should pass the distance parameter. The distance is a
relative position that the shuttle will measure and pick/drop the load from/to. This should be the center of the load.
The distance can be calculated based on the length of the lane, the pallet types and other various parameters. For a full overview of
this, refer to the document 'pallet-index-calculation-guide.pdf'.
Level parameter
Note that the 2D in Atlas 2D stands for '2 dimensional', which means it only can move in X and Y direction. Therefor the source and
destination level needs to be thesame, otherwise the aSTC will throw an error. For EScala this is not a problem since they have traffic
lanes that allow them to change levels.
S2D Example: Move load from node to node
{{host-address}} is the IP where the IRIS application is running and the port where the WCS can communicate to. This defaults to port
number 9729.
The instruction is sent over a HTTP POST request to the endpoint {{host-address}}:{{port}}/api/v0.1/missions
Payload
{
"id": 1,
"source": {
"level": 1,
"lane": 16,

9

"section": 5,
"distance": 600,
"exit": "Front"
},
"dockedAtSource": false,
"destination": {
"level": 1,
"lane": 4,
"section": 3,
"distance": 600,
"approach": "Back"
},
"dockAtDestination": false
}
Source contains the data of the node where we want to pick from.
Level, lane and index determine the node’s location.
Distance is on what position the load is stocked.
Exit sets on what side the shuttle should leave the location
Note: not all lanes have a front and back, for example the lift drop location only has a ‘front’
Destination contains the data of the node where we want to pick from.
Approach is on what side the shuttle should enter the destination.
distance is here the position where the load should be dropped
Webhook feedback
From the webhook feedback insights are provided about how the aSTC works internally and how the load was handled.
Mission Created
The system recieved the mission, it is added to the queue of missions that need to be executed.
[
{
"notificationType": "MissionCreated",
"Payload": {
"MissionId": "1",
"Priority": 5,
"Source": {
"Exit": "Front",
"Level": 1,
"Lane": 16,
"Section": 5,
"Index": 0,
"Distance": 600
},
"Destination": {
"Approach": "Back",
"Level": 1,
"Lane": 4,
"Section": 3,
"Index": 0,

10

"Distance": 600
},
"LoadId": "load-1-dgolc5693zl8rwm502hq7"
},
"NotificationId": "48c42210-f952-4029-ae56-81aee74552b8",
"TimeStamp": "2023-07-19T10:59:01.0542007Z",
"ContextType": "Mission"
}
]
Mission Assigning
The aSTC is calculating which path is the most favorable and which shuttle should execute the mission.
[
{
"notificationType": "MissionAssigning",
"Payload": {
"MissionId": "1",
"VehicleId": "1"
},
"NotificationId": "4d436fdc-36e2-4131-84c1-eb3baf4422ee",
"TimeStamp": "2023-07-19T10:59:01.2407481Z",
"ContextType": "Mission"
}
]
Mission Assigned
A shuttle was found to execute the mission.
[
{
"notificationType": "MissionAssigned",
"Payload": {
"MissionId": "1",
"VehicleId": "1"
},
"NotificationId": "d25b2a41-49ce-407e-98d2-3f1e1c2d9bb9",
"TimeStamp": "2023-07-19T10:59:02.9192526Z",
"ContextType": "Mission"
}
]
Mission Picked
The shuttle picked up the load.
[
{
"notificationType": "MissionPicked",
"Payload": {
"MissionId": "1",

11

"VehicleId": "1",
"Source": {
"Exit": "Front",
"Level": 1,
"Lane": 16,
"Section": 5,
"Index": 0,
"Distance": 600
},
"LoadId": "load-1-dgolc5693zl8rwm502hq7"
},
"NotificationId": "b19183d4-7a78-4047-88ed-89b2bbd73e28",
"TimeStamp": "2023-07-19T10:59:14.4415864Z",
"ContextType": "Mission"
}
]
Mission Dropped
The shuttle delivered and released the load.
[
{
"notificationType": "MissionDropped",
"Payload": {
"MissionId": "1",
"VehicleId": "1",
"Destination": {
"Approach": "Back",
"Level": 1,
"Lane": 4,
"Section": 3,
"Index": 0,
"Distance": 600
},
"LoadId": "load-1-dgolc5693zl8rwm502hq7"
},
"NotificationId": "10966d7f-857f-4150-a583-7eb01de6412e",
"TimeStamp": "2023-07-19T10:59:24.0476533Z",
"ContextType": "Mission"
}
]
Note here: the requested distance is not always the exact distance the shuttle will drop the load. This is because the shuttle scans the
node for sticking out load that is already on the location. If the arriving load can be dropped within a tolerated margin, it will be
dropped and that distance will be returned from the aSTC. The value returned from this feedback should be used the next time that
load needs to be picked up.

12
ASTC handshake interfaces
Handshakes
To exchange information and coordinate actions with devices beside the shuttles, aSTC has a defined interface to connect said
devices to the system. This could be a flipper, conveyer, automatic door, ... Each handshake is based on the request - acknowledge
principle.
Example: handshake for load pickup
This interface is used whenever a shuttle wants to pick a load from an automated system. The supplying system can be a lift, a
conveyor or any other load carrying entity. This interface ensures a safe transfer of the load and minimizes the risk of material
damage.
Signal overview

Signal
Number

Name Description

1 Pick Request The STC informs the ‘supplying system’ that a shuttle wants to pick the load.
2 Pick Enable The ‘supplying system’ allows the pick action.
3 Pick Busy The STC informs the ‘supplying system’ that a shuttle is picking a load.
4 Error The ‘supplying system’ informs ASTC that it is in error state. If a shuttle is present it will be
stopped. This is not a ‘performance level safety signal’. It is only meant to prevent material

13

damage.

5 Not In Zone The shuttle is NOT at the ‘supplying system’ location.
6

Not In Zone
Loaded

The loaded shuttle is NOT at the ‘supplying system’ location.

Timing

Timing Description
T1 Shuttle arrives at pick point and request to pick the load.
T2 The ‘supplying system’ makes sure that it is safe to allow the pick and enables the pick action.
T3 The shuttle starts its pick routine.

14
T4 The shuttle is loaded.
T5 The shuttle is not picking anymore and has left.
For all handshakes, please refer to the the document 'movu-handshake-interface.pdf'.

15
ASTC and Safety measures
Safety modes
An warehouse can consist of one safety zone or can be divided into several safety zones. For the safe use of each safety zone in the
warehouse, the aSTC has four safety modes distinguished:
Automatic mode: In this mode, the aSTC allows automatic operations in that safety zone of the shuttles at normal speed.
The access doors in that safety zone must be closed and locked and the inbound/outbound gates and doors must be
closed (perimeter closed & locked). Presence of people inside that safety zone of the warehouse is prohibited.
Manual mode: In manual mode, service technicians can execute local commands for low-speed shuttle movements in that
safety zone. The access doors in that safety zone must be closed and locked and the inbound/outbound gates and doors
must be closed (perimeter closed). Only the presence of service technicians in that safety zone of the warehouse is allowed
for manual shuttle movements.
Off mode: In this mode, all shuttle movements are prohibited. One access door must stay open (perimeter open). The
presence of operators and service technicians in the warehouse is allowed for service and maintenance activities.
Controlled Stop intermediate mode: In this mode, no new tasks are given to the shuttles in that safety zone, so that they
can finish their tasks. Once all task are completed, that safety zone is moved to the Off mode.
To control these safety modes, a number of signals is provided by Movu. The following signals are defined:
Perimeter Closed & Locked
Request Access
Automatic Mode
E(mergency)-stop
The control of raising these signals is done by the integrator and sends this to the aSTC which can be done for each zone. In return
the aSTC has two output signals that are sent to the integrator:
aSTC E-Stop
Access Allowed
Changing the states of these signals allows the system to transition between the safety modes:

16
Within every safety zone, there are one or more points where the warehouse can be accessed, such as safety doors, emergency
doors, pallet gates, and so on. The inputs from the safety devices of all access points in a safety zone are combined by the integrator
with possible additional safety devices (light grids, air locks, and so on). For each zone separately, the signals *Perimeter closed and
locked*, *Request access*, *Automatic Mode*, and *E-stop* are determined by the integrator and then sent to the aSTC. Based on
that information, the aSTC determines the safety mode of every safety zone. The aSTC sends the signals *aSTC E-stop* and *Access
Allowed* to the integrator.
Typically each level of a S2D warehouse is a safety zone. Depending on the physical structure, several safety zones can also be
defined within the same level.

17
ASTC HMI
The provided HMI serves as the central point of interaction between the operators and the system. It allows the operators to
monitor, control and manage components and running processes.
The following options are available and are also available through the WCS interface:
Adding / removing shuttles
Task list
Shuttle overview
Warehouse layout
Commissioning
Manual mode
Quick overview of errors and operational states of all levels
The HMI can be accessed through a web browser. A detailed explanation can be found in the 'Movu-visualisation.pdf' document.

18
Testing aSTC
ASTC Emulator
To get familiar with the aSTC software and the integration an emulator was developed to help integrators in this process. The
emulator is software that is able to load in warehouse files and recieve commands through the HTTP API.
The intregrator can connect his WCS to the emulator and can simulate certain scenario's and visually see the results of the
commands send to the software.
For a full guide on how to install the emulator, refer to the 'Emulator Installation' document for either S2D or Escala.

19
Functional concepts
Digging
Digging is a process added by the WCS to retrieve a certain pallet from the lane. The procedure will remove all pallets in front of the
needed pallet and bring these to a dedicated buffer lane (mandatory). The pallet can then be retrieved and brought to the
outbound. After this the buffer lane is emptied and the pallets from the buffer lane are brought back to the original lane.
To ensure a specific order of pallets in the buffer and storage lane, the concept of late decisions can be used at source and
destination. This is explained in the document 'API-Integration-Interface-spec.pdf' chapter 'Atlas® 2D Load movements with
confirmation requests'.
Important: This adds 2 movements for the pallet. Is it advised to have a max of 5 movements for a pallet within a normal lane and a
max of 10 movements within a lane with guidance bars. This due the fact that picking and dropping can change the position of the
pallet and increases the chance on mechanical issues. Therefor it is strongly advised to only use this feature for quality control
procedures.
Shuttle-roaming
Shuttle roaming indicates the feature that a shuttle can be moved to every viable node within the warehouse (storage node, main
node, ...). For this specific commands can be used. Note: For EScala, the command can be between levels. For S2D this needs to be
done with multiple commands that require (un)docking and if a vertical conveyer is available.
Stay at target
It is possible to ask an Escala shuttle to stay parked a certain position. For this the flag "StayAtTarget" can be used. The aSTC will
break the parked state for the following reasons:
Shuttle battery runs low
There's a specific task for a shuttle
There was a task created for the node where the shuttle resides
The shuttle goes from automatic mode to any other mode (Service or error state for example)
Note: Available from API version 1.0.1 and above.

20
System management
Battery management
Depending on the battery State of Charge (SOC) of the shuttle, the aSTC will add a ChargeTask.
When the battery level is below 80%, the ASTC will add a ChargeTask at the end of task list with a low priority (1).
When the battery level is below 30%, the ASTC will increase the ChargeTask priority to 11 (highest priority). No missions will
be assigned to this shuttle
If something goes wrong and the battery level keeps lowering, the shuttle will go into shutdown to protect the battery.
When restarting the Atlas2D shuttle only MoveShuttle and ChargeShuttle commands are accepted.
Note: When a ChargeTask has been postponed by a MovePallet task it will be added again automatically after 10 minutes.
Idle management
This concerns positioning of the shuttles to the next most favorable location when they are without tasks. For example this could be
close to a drop position if that is where the shuttles have the most interaction with.
The following rules are applied:
1. Starting point of a not assigned task (If this starting point is a valid parking spot), preferring the previous parking spot.
2. A parking spot that is not part of a busy or error task, preferring the previous parking spot
3. A parking spot that is part of an error task, preferring the previous parking spot
4. The nearest valid parking spot.
Introduction
This documents provides examples flows of how the WCS could interact with the Movu Atlas shuttle system and the components
around it. In the sequences the “Emulator’ component can be seen as an external control (For example: a vertical or horizontal
conveyer). The handshakes and communications with this controls are out of scope for this document.

3
Sequence diagrams
Move load with confirmation flag
This sequence is valid for the following processes:
Inbound to storage lane
Storage lane to storage lane
Storage lane to outbound

4
Move load with late decision at destination
Typically, if more than one shuttle is sent towards a storage lane, it cannot be predicted in which sequence they will arrive. Therefor
the principle of “late decision” is introduced. With this principle it becomes possible to update the mission at the moment that the
shuttle is positioned in front of the lane. The ASTC will ask “confirmation” at the right moment in such a case. This sequence diagram
shows the message flow in case of late decision at destination.

5
Shuttle roaming
This scenario can be used to move a shuttle from one level of a warehouse to another.

Shuttle roaming with a load
In this scenario a shuttle picks up a load and brings it to a vertical conveyer. After that the WCS sends the elevator to the correct
level. Once that is done, the WCS needs to instruct the shuttle to move out to a specific node.

6
Lane full exception solution

7
In this scenario we try to perform a normal Move Load, but there is already a pallet standing on the destination of the mission. In that
case we will receive a MissionError notification, with the detail that there already is an object on the location. To resolve this error, the
WCS must abort the mission and create a new one to move the load to another location.
The specifications and information regarding the products in this manual are subject to change without notice. All statements,
information, and recommendations in this manual are believed to be accurate but are presented without warranty of any kind,
express or implied. Users are responsible for the application of the products
Your use of this document and any associated product is subject to the terms and conditions of the agreement that is in place
between you and Movu Robotics or any of its affiliates. Should there be no agreement in place, then you are not authorized to use
this document or the associated product.
The Movu name together with any associated logo are a registered trademark. You may not make use of them without the specific
permission of Movu Robotics NV or its affiliates. Movu Robotics NV and its affiliates reserve all its rights in respect to the use of its
branding.
2023 Movu Robotics NV. All rights reserved.

Movu Robotics NV
Registered office:
Brandstraat 30
9160 Lokeren, Belgium

Principal place of business:
Brandstraat 30
9160 Lokeren, Belgium
Tel +32 56 48 11 11
Fax +32 56 48 63 70

info@stow-group.com
www.stow-group.com
www.movu-robotics.com
www.movu-robotics.com/en/engineering
For service, interventions and spare parts:
Service Department
Contact number:
Tel +32 56 960 990
aftersales@stow-group.com

2
Introduction
Movu provides an Application Programming Interface (API) that enables a Host Warehouse Management System (WMS) or
Warehouse Control System (WCS) to integrate Movu’s line of automated storage and transportation solutions.
API Release notes
API
Version Date Concepts
1.0.0 07/07/2023 Initial version - routes listen to v0.1
1.0.1 10/08/2023

StayAtTarget for Escala was added.
Routes are now updated to listen to v1.0
1.1.0 28/09/2023 Vehicle interface is now available
1.2.0 19/10/2023

Webhook notifications are now camelcased, CORS support is added, fixed double feedback
message bug

3
Technical setup
Communication types
All communication across the interface can be categorized as follows:
Query: Interrogating the system without mutating state.
Command: Instructing the system to perform an action (and hence mutating state).
Notifications: Notifying changes in the system. Queries and Commands are initiated by the Host, Notifications are received
by the Host.
Architecture
The interface is composed of a Level 2 RESTful API, as defined by the _Richardson Maturity Model.
It relies on HTTP(S) as the communication protocol.
URIs break down the service endpoint into smaller scopes of resources.
Verbs and Status Codes are standardized, reducing unnecessary variation.
To receive the notification stream on a specific resource, a webhook with a callback URI needs to be defined. This allows the Host to
subscribe to only those notifications it is interested in. The Host only needs to subscribe once, the subscription details will be
persisted.
The receiving endpoint is expected to always be available, but if it is not, events will be buffered and transmitted in batch as soon as
the connection can be re-established.
Generic HTTP Status Codes
Category Status Code Description
Success 200 OK

The request has succeeded. The information returned with the response is dependent on
the method used in the request.

201 Created The request has been fulfilled and resulted in a new resource being created.
202 Accepted

The request has been accepted for processing, but the processing has not been
completed. The request might or might not eventually be acted upon, as it might be
disallowed when processing takes place.

204 No Content The request has been successfully processed but is explicitly not returning any content.

Client
Error

400 Bad Request

The request could not be understood due to malformed syntax. The client should not
repeat the request without modifications.

401
Unauthorized

The request requires user authentication.

403 Forbidden The request was a legal request, but the server is refusing to respond to it.
404 Not Found The requested resource could not be found but may be available again in the future.
409 Conflict The request cannot be completed due to a conflict with the current state of the resource.

Server
Error

500 Internal
Server Error

A generic error message, given when no more specific message is suitable.

4

501 Not
Implemented

The server either does not recognize the request method, or it lacks the ability to fulfill
the request.

Specification
The Interface description is available as an OpenAPI specification, as provided by the Swagger toolset. Swagger adds online technical
documentation, exploration and code generation possibilities.
See https://editor.swagger.io/ for a non-related sample specification.

5
Mission Interface
Basic Concepts
The Mission Interface focuses on tailored transportation and gives the Host control over specific load movements. Responsibility for
storage strategy, priority definition, sequencing and high-level throughput optimization logic remains with the Host.
The following resources are involved
Load: the basic unit that is stored or transported in the warehouse.
Location: a space in the warehouse, where Loads can be placed for storage or transferal.
Mission: an abstraction representing the single transport movement of a Load from a Source Location to a Destination
Location.
Vehicle: the machine used to execute the Mission. (i.e. a shuttle)
Location
Each source or destination location (or node) consists of 5 properties.
Level: Like story or floor
Lane: Number of the target lane in main lane direction
Section: Consists of either a main lane or a group of concatenated storage locations
Index: Index within a section starting from the front of the warehouse (Only to be used in EScala warehouses)
Exit or Approach: Side of the warehouse to leave or enter a location with the load. Either front or back
Mission States
A Mission can go through several states during its lifetime:
State Substate Description
Pending Created The Mission is ready to be executed, but a Vehicle has not been assigned to it yet.
Assigning The selection of a Vehicle has started, but it is not executing the Mission yet.

Retrieving Assigned

The Mission is being executed, but the Load has not been retrieved from the source
Location yet.

Awaiting Entrance
Confirmation

Optional. Waiting for source Location confirmation.

Storing Picked

The Mission is being executed, the Load has been retrieved from the source Location,
but has not been stored on the destination Location yet.

Awaiting Exit
Confirmation

Optional. Waiting confirmation before leaving the source location.

Awaiting Entrance
Confirmation

Optional. Waiting for destination Location confirmation.

Finished Dropped

The Mission has been completed successfully. The Load has been retrieved from the
source Location and stored in the destination Location
Cancelled The Mission has been cancelled gracefully.

6

Aborted

The Mission has been aborted, corrective actions might be necessary to continue
automatic operation

Mission Queries
Mission Status
The Mission Status query returns current status information of a specific mission, or of all current missions.
Mission Commands
Create Mission
The Create Mission command will create a new mission, ready to be executed.
The Mission Id must be unique among all missions.
The minimal information needed to perform a mission is the destination location and a source. The mission source can either be a
source location, and in some cases the vehicle that is currently carrying the load or the load id.
The Vehicle id can be specified if you want a specific vehicle to execute the mission. This is the only case when a mission can have a
vehicle in its created substate.
The Load id is only used for information and temporary tracking. We recommend specifying a unique load id in the create mission
request.
The system keeps track of all loads in transit, in certain situations, it may be possible to infer the vehicle id from the load id alone.
This may be useful in case of error handling and missions that could not be completed.
The new mission will be in the Created substate.
If a vehicle is available to handle the mission, the mission will quickly go from substate Created to Assigning. If no vehicle is
immediately available, the mission will stay in Pending until a vehicle is assigned.
Creating a mission can be done by a POST-request to /api/v1.0/missions , examples can be found in the Mission Use Case
chapter
Update Mission
The Update Mission command allows the Host to update some properties of the mission.
Depending on the current state of the mission, certain properties may not be allowed to change.
Source
The source can be changed if the mission has not yet picked the load.
Updating a mission source can be achieved by a PUT-request to /api/v1.0/missions/{missionId}/source
Priority
Priority 1 is the highest and 10 is the lowest. The priority of a mission can be changed if the mission is not yet in state Retrieving.
Updating a mission priority can be achieved by a PUT-request to /api/v1.0/missions/{missionId}/priority
Destination
The destination of a mission can be changed if the vehicle has not yet dropped its load.

7
Updating a mission destination be achieved by a PUT-request to /api/v1.0/missions/{missionId}/destination
Vehicle
The vehicle of a Mission can be changed if the mission is not yet in state Retrieving.
Updating a mission vehicle can be achieved by a PUT-request to /api/v1.0/missions/{missionId}/vehicle
Cancel Mission
The Cancel Mission command allows to cancel a mission when the load has not been retrieved from the source location. Trying to
cancel a mission when a load has already been retrieved is not allowed.
The cancelled mission will be in the Cancelled state. Cancelling a mission can be done by a POST-request to
/api/v1.0/missions/{missionId}/cancel
Abort Mission
The Abort Mission command allows to force mission completion, indicating that the mission should be terminated. Manual actions
may need to be performed to continue automatic operation. Aborting a mission when a load was picked will result in the shuttle not
being available for any future missions. Manual actions are needed to release the shuttle for future executions.
This command is intended as error handling and should not be needed for standard operation.
The aborted mission will be in the Aborted state.
If a mission is aborted when the load is already retrieved the shuttle will stop its execution. A new mission will need to be created to
get the shuttle with load moving again. This new mission request does not need a source location but a shuttle or load id. Either of
those two will work. This new mission request can have a totally different destination and priority.
Aborting a mission can be done by a POST-request to /api/v1.0/missions/{missionId}/cancel
Mission Webhooks
The Host can be notified every time a mission is created or updated. The notifications are sent over a webhook, also known as http
callback.
The contract with exact payload to be implemented can be found in the swagger documentation of the API. Code generation tools
should be used to generate the consumer side of the webhooks including the webhook notification contract.
Registering a webhook is done by specifying which address and context you are interested in.
Each webhook notification contains a unique Notification id, context type (e.g., Mission), notification type (e.g., MissionCreated) and
timestamp in ISO-8601 format when the event occurred. The payload of the notification depends on the notification type.
Example notifications
Example notifications expected to be received by a webhook consumer in case of a successful Mission.
MissionCreated, MissionAssigning, MissionAssigned, MissionPicked, MissionDropped
Configuration
It is possible to configure the webhook subscriber, this configuration is shared between all webhook registrations. It isn’t possible to
configure an individual webhook registration.
Connection timeout, discard timeout and max batch size can all be configured.

8
Vehicle Interface
The Vehicle Interface exposes information about the vehicles within the system.
Vehicle queries
Single Vehicle status
By requesting a specific vehicle status, the details of it's current state is returned. The details contain:
The mode the Vehicle is in
The battery level
The current position
The load the shuttle is carrying
The error messages if the Vehicle is currently in error
The query is available via a GET -request to /api/v1.0/vehicles/{vehicleId}
Status of all vehicles
This query returns the details as mentioned above for all the available vehicles within the system.
The query is available via a GET -request to `/api/v1.0/vehicles
Shuttle States
value state
0 Idle
1 Info
2 LoadPLt
3 PickPLt
4 Stop
5 Shuffle
6 Count
7 Demo
8 Manual
9 ChangeHome
10 Homing
11 Pincode
12 Pincode_OK
13 Error
14 Low_Power

9
15 Charge
16 Startup
17 Drive2Sct
18 Unknown
19 StorePlt
20 RetrievePlt
22 Dock
23 Undock

Vehicle Webhooks
Over time the API will send feedback messages about the states of the shuttles. It is possible to register on these messages through
the webhook interface and register for the context 'Vehicle'.
State Description
VehicleBatteryLevelChanged State of Charge of the shuttle

10
Task Interface
The task Interface exposes the possibility to perform shuttle roaming.
Task commands
Docking
This command is used to send a shuttle within a vertical conveyer and secure it to be able to move the shuttle to a different level.
The WCS needs to indicate which shuttle and where the shuttle needs to go. The command can be executed via a POST -request to
`/api/v1.0/tasks/dock
An example can be found in the Task use case chapter.
Undocking
The undocking command is used to release the docked shuttle from a vertical conveyer.
The command can be executed via a POST -request to `/api/v1.0/tasks/undock
An example can be found in the Task use case chapter.

11
Health Check Interface
Health checks are a fast and simple way of reporting the health of the application and application infrastructure components. Health
checks are informational and are not required for normal operation of the system.
Liveness probe
The Liveness probe can be used to verify that the application is running and active.
Request: GET /api/v1.0/health/live Response:
200 OK
408 Request Timeout
503 Service Unavailable
Readiness probe
In some cases, the application can be running and responsive, but temporarily unable to serve requests, for example during start-up
or when a dependency is unhealthy. A readiness probe can be used to detect these situations and will respond with all unhealthy or
degraded dependencies.
Request: GET /api/v1.0/health/ready Response:
200 OK
408 Request Timeout
503 Service Unavailable

12
Mission use cases
Way of work
Storing a load in a storage lane is preferably done by giving the requested storage distance within the mission. The shuttle will then
try to store the load at the requested distance. The shuttle will use its sensors to finetune the storage distance in relation to the last
load already present in the lane. If the shuttle is able to store the load within a predefined tolerance window, the real storage
distance will be sent in the ‘MissionDropped’ feedback. This distance should be stored in a way it can added in the mission to pick
this load again. The distance is always given relative to the ‘FRONT’ of the warehouse.

Atlas® 2D Load movement from location to storage lane
Messages
Nr. HOST -> ASTC ASTC -> HOST
1 Create Mission
2 MissionCreated
3 MissionAssigning
4 MissionAssigned

13
5 MissionPicked
6 MissionDropped
Mission Creation
Creation of a mission is done by sending the json in a POST-request to /api/v1.0/missions/ the HTTP request will return a
201 response with the mission payload.
{
"id": 21,
"source": {
"level": 1,
"lane": 2,
"section": 11,
"distance": 600,
"exit": "Front"
},
"destination": {
"level": 1,
"lane": 2,
"section": 4,
"distance": 1500,
"approach": "Back"
}
}
Webhook feedback: aSTC is assigning the mission
{
"notificationType": "MissionAssigning",
"payload": {
"missionId": "21",
"vehicleId": "1"
},
"notificationId": "e9e1eb98-31de-42fe-9826-bc8d1332868e",
"timeStamp": "2023-06-29T12:12:55.9760617Z",
"contextType": "Mission"
}
Webhook feedback: aSTC is assigned the mission
{
"notificationType": "MissionAssigned",
"payload": {
"missionId": "21",
"vehicleId": "1"
},
"notificationId": "df76d11a-692b-4c6d-9068-aa515d464f51",
"timeStamp": "2023-06-29T12:12:57.8787298Z",

14
"contextType": "Mission"
}
Webhook feedback: the shuttle picked the load
{
"notificationType": "MissionPicked",
"payload": {
"missionId": "21",
"vehicleId": "1",
"source": {
"exit": "Front",
"level": 1,
"lane": 2,
"section": 11,
"distance": 600
},
"loadId": "load-9-9ijcvoeoa00lrqmkq4ohr"
},
"notificationId": "cdef1010-52b9-42a4-bcc7-472bf9bd2590",
"timeStamp": "2023-06-29T12:13:10.2863457Z",
"contextType": "Mission"
}
Webhook feedback: the shuttle dropped the load at the destination
{
"notificationType": "MissionDropped",
"payload": {
"missionId": "21",
"vehicleId": "1",
"destination": {
"approach": "Back",
"level": 1,
"lane": 2,
"section": 4,
"distance": 1500
},
"loadId": "load-9-9ijcvoeoa00lrqmkq4ohr"
},
"notificationId": "da8c577f-db4b-441b-a4a1-3ac95bd172e0",
"timeStamp": "2023-06-29T12:13:22.3825944Z",
"contextType": "Mission"
}
Remark: the Distance returned in this payload is the real feedback coming from the shuttle. This distance should be given into the
mission when this load needs to be retrieved again.

15
Atlas® 2D Load movement from storage lane to location
Mission Creation
{
"id": 22,
"source": {
"level": 1,
"lane": 2,
"section": 4,
"distance": 1500,
"exit": "Back"
},
"destination": {
"level": 1,
"lane": 2,
"section": 11,
"index": 1,
"approach": "Front"
}
}
Remark: the source distance is the real feedback that came from the shuttle during load storage. It is important for the reliability of
the system to specify it correctly into the mission creation message.
Atlas® 2D Load movements with confirmation requests
Late decision at destination
Typically, if more than one shuttle is sent towards a storage lane, it cannot be predicted in which sequence they will arrive. Therefor
the principle of “late decision” is introduced. With this principle it becomes possible to update the mission at the moment that the
shuttle is positioned in front of the lane. The ASTC will ask “confirmation” at the right moment in such a case.
The illustration shows a typical example of the use case: the situation of the destination lane has changed during the shuttle
movement from source to destination. Initially ‘Distance X’ was expected but finally ‘Distance Y’ should be sent.

16
Message flow of the use case:

Mission creation
{
"id": 105,
"source": {

17

"level": 1,
"lane": 13,
"section": 1,
"distance": 600,
"exit": "Back"
},
"destination": {
"level": 1,
"lane": 15,
"section": 1,
"distance": 2000,
"approach": "Back",
"entranceConfirmationRequired": true
}
}
We request the load to be dropped at a distance of 2000. Since there are multiple tasks ongoing, we want to confirm this drop. We
can ask aSTC to do this by adding the entranceConfirmationRequired property.
Feedback MissionAwaitingDestinationEntryConfirmation
{
"notificationType": "MissionAwaitingDestinationEntryConfirmation",
"payload": {
"missionId": "105",
"vehicleId": "1",
"destination": {
"approach": "Back",
"level": 1,
"lane": 15,
"section": 1,
"distance": 2000
},
"loadId": "load-6-8o5pemlcd2vqj4i3zu8v4"
},
"notificationId": "32b1a5ce-c411-4438-8ce1-8f5d9ed8087a",
"timeStamp": "2023-06-30T14:14:27.8523623Z",
"contextType": "Mission"
}
The shuttle is ready to pick at distance 2000, but the composition of the lane changed. An extra load was stored. Hence we ask the
shuttle to pick the last one in the lane.
Update the Mission
{
"destination": {
"level": 1,
"lane": 15,
"section": 1,
"distance": 2500,

18

"approach": "Back"
}
}
We sent the new distance (2500) where the latest added load should be.
Feedback MissionDestinationChanged
{
"notificationType": "MissionDestinationChanged",
"payload": {
"missionId": "105",
"destination": {
"approach": "Back",
"level": 1,
"lane": 15,
"section": 1,
"index": 0,
"distance": 2500
}
},
"notificationId": "f0f3c9a1-fe41-40f5-bfc4-4b3600117f6f",
"timeStamp": "2023-07-12T15:35:08.6601516Z",
"contextType": "Mission"
}
The mechanism notifies the registered listeners that the shuttle will pick on distance 2500.
Confirm the Mission
This change needs to be acknowledged by the WMS or WCS. This can be done by calling the following endpoint:
/api/v1.0/missions/{missionId}/confirmenterdestination
Where {missionId} is the id of the mission that you want to confirm (in this example it is 105).
Responses:
202 Accepted: The request to confirm source entry has been accepted.
400 Bad Request: When the request is not accepted. For example when the mission is already finished.
Late decision at source
Comparable with the late decision at destination, the same principle can be used at the source location. The reason to use this
feature is the same as using it at the destination: the situation at the source location can be different between the moment of
mission generation and the moment of arriving in front of the source location.

19
Message flow of the use case:

Mission Creation
We request the load to be picked at a distance of 600. Since there are multiple tasks ongoing, we want to confirm this pick up. We
can ask aSTC to do this by adding the entranceConfirmationRequired property.
Late decision at source mission creation:
{
"id": 106,
"source": {
"level": 1,
"lane": 13,
"section": 1,
"distance": 600,
"exit": "Back",
"entranceConfirmationRequired": true
},
"destination": {
"level": 1,
"lane": 15,
"section": 1,
"distance": 2000,
"approach": "Back"
}
}
Source entry confirmation feedback when shuttle arrives at source:
{
"notificationType": "MissionAwaitingSourceEntryConfirmation",

20
"payload": {
"missionId": "106",
"vehicleId": "1",
"source": {
"exit": "Back",
"level": 1,
"lane": 13,
"section": 1,
"distance": 600
},
"loadId": "load-1-cexcu9mysupsqa78g9mo5"
},
"notificationId": "a43e1f96-ca11-41fb-9b64-3c88bc6aeb3f",
"timeStamp": "2023-07-04T15:20:49.2687826Z",
"contextType": "Mission"
}
The shuttle is ready to pick at distance 600 in lane 13, but the composition of the lane changed. There is no load left. Hence we ask
the shuttle to pick a load in the lane next to it.
Update the source of the Mission
{
"source": {
"level": 1,
"lane": 14,
"section": 1,
"exit": "Back",
"distance": 600
}
}
We sent the shuttle to another lane, 14, on distance 600 where a load is available.
Feedback MissionSourceChanged
{
"notificationType": "MissionSourceChanged",
"payload": {
"missionId": "106",
"source": {
"exit": "Back",
"level": 1,
"lane": 14,
"section": 1,
"index": 0,
"distance": 600
}
},
"notificationId": "6dd89281-815e-4ad6-8e34-3a9640218139",
"timeStamp": "2023-07-12T14:39:21.8732471Z",

21
"contextType": "Mission"
}
The mechanism notifies the registered listeners that the shuttle will pick on lane 14.
Confirm the Mission
This needs to be acknowledged by the WMS or WCS. This can be done by calling the following endpoint:
/api/v1.0/missions/{missionId}/confirmentersource
Where {missionId} is the id of the mission that you want to confirm (in this example it is 106).
Responses:
202 Accepted: The request to confirm source entry has been accepted.
400 Bad Request: When the request is not accepted. For example when the mission is already finished.

22
Atlas® 2D Shuttle roaming with a load
In this use case the steps are described to a shuttle with a load to another level.
Note: This can only be done if the shuttle can move onto the lift.
Picking and docking
The payload is similar to a default movement. Except this time we have to set the dockAtDestination-flag to true. It is also required to
pass the loadId. If this is not done, the aSTC will lose track of which load is on the shuttle that is docked and the software will throw
an error on the undocking-mission.
Note: The destination distance is 800 here, which is the calculated center of the lift.
Payload
{
"id": 204,
"source": {
"level": 1,
"lane": 3,
"section": 3,
"distance": 600,
"exit": "Front"
},
"dockedAtSource": false,
"destination": {
"level": 1,
"lane": 20,
"section": 6,
"distance": 800,
"approach": "Front"
},
"dockAtDestination": true,
"Load" : {
"Id": "LOAD-1"
}
}
The feedback is similar to the previous use cases. The task goes through the assigning, assigned, Picked and Dropped state.
Undocking and dropping
In this use case the lift was moved to level 7 by the WCS.
Payload
In this scenario the shuttle is still docked under the load. Hence we cannot send another shuttle to the load and we need to pass the
Vehicle-Id of the shuttle under the load in the mission. On top of that we need to let the software know the shuttle is still docked, so
we flag dockedAtSource to true. The last thing we need to define is the Load-Id that was specified in the docking mission for
verification purposes.

23
{
"id": 205,
"source": {
"level": 7,
"lane": 20,
"section": 6,
"distance": 800,
"exit": "Front"
},
"dockedAtSource": true,
"destination": {
"level": 7,
"lane": 10,
"section": 5,
"distance": 600,
"approach": "Back"
},
"dockAtDestination": false,
"vehicle": {"id": 1 },
"Load" : {
"Id": "LOAD-1"
}
}
The feedback is similar to the previous use cases. The task goes through the assigning, assigned, Picked and Dropped state.
Atlas® 2D Lane full error handling
In this use case the error "Lane full" is triggered. This error is thrown when a load is moved to a position where another load already
resides. To simplify the example, no confirmation flags are used.
Note: This is an unhappy flow, it should be clarified on why the pallet was send to an occupied location. The problem could reside
on each layer, hence a deep dive into the context is advised.
Create Initial Mission
The mission is send as a JSON in a POST-request to /api/v1.0/missions/
{
"id": 400,
"source": {
"level": 1,
"lane": 3,
"section": 3,
"distance": 700,
"exit": "Front"
},
"destination": {
"level": 1,
"lane": 4,
"section": 3,
"distance": 700,

24
"approach": "Front"
}
}

Mission feedback
In this example the normal feedback is received: MissionCreated, MissionAssigning, MissionAssigned,
MissionAwaitingSourceEntryConfirmation, MissionPicked, MissionAwaitingDestinationEntryConfirmation until we receive the
MissionError we are looking for in this example:
{
"notificationType": "MissionError",
"payload": {
"missionId": "400",
"errors": [
{
"id": 0,
"message": "1-2006-103: WMS / TASKUPD / Lane is full, no loads can be added",
"severity": "Warning"
}
]
},
"notificationId": "7e36e4a0-535a-4bfb-9f33-140ddb2a45b0",
"timeStamp": "2023-11-20T09:29:54.1793466Z",
"contextType": "Mission"
}
In the message we can see that the lane is full and the pallet can not go to the original destination. At this point the error needs to
be analyzed.
Abort Mission
Since this mission failed, we can no longer use it to continue. Hence we abort the message by sending a post to
/api/v1.0/missions/{missingId}/abort . In this way we clear the task from the shuttle and it can move again.
Create Mission To Move Shuttle With Load
The pallet is still on the shuttle, so we need to store it on another location. Since the load does not need to be picked up, the
"source" parameter can be omitted. Since we want the mission to be assigned to the shuttle we also want to pass the vehicle
parameter. It is also important to send the load-Id, it is used for internal tracking within the aSTC.
{
"id": 401,
"destination": {
"level": 1,
"lane": 5,
"section": 3,
"distance": 700,
"approach": "Front"
},
"vehicle": {
"Id": 1

25
},
"Load": {
"id": "load-1-er935g2wwynia823pzqx3"
}
}
Mission feedback
For the new mission the normal feedback is returned that is the same as a simple 'MoveLoad'-operation, with exclusion of
''MissionAwaitingSourceEntryConfirmation" since the load is already on the shuttle.

26
Task use cases
Atlas® 2D Roaming a shuttle to another level
When a new shuttle is added, or there is a lot of work on another level, it could be possible that a shuttle needs to move to another
level. For this the WCS can perform shuttle roaming by the use of 2 tasks docking and undocking.
Note: These commands are only valid for a shuttle without a load, in other cases the missions interface should be used (see previous
chapter).
Dock Shuttle
For this a docking node is required. A docking node could be a vertical conveyer (used in this example). Docking means the shuttle
needs to apply his brakes when on this node.
Payload
We want to send shuttle with id 1 to the lift (destination). This is a POST to {{host}}/api/v1.0/tasks/dock
{
"id": 3,
"destination": {
"level": 7,
"lane": 20,
"section": 6,
"index": 1,
"distance": 600,
"approach": "Front",
"entranceConfirmationRequired": false,
"entranceConfirmed": true
},
"vehicle": {
"id": 1
},
"priority": 10
}
Task feedback
The task was received:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "NotAssigned"
},
"notificationId": "3e83f8bf-ed98-44b2-851b-84957e07c347",
"timeStamp": "2023-07-31T14:09:04.7837543Z",
"contextType": "Task"
}

27
The task was assigned to the shuttle:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "Assigned"
},
"notificationId": "da19109f-102f-4a95-a822-37e7d74919bf",
"timeStamp": "2023-07-31T14:09:04.9426063Z",
"contextType": "Task"
}
The task is executing, the shuttle is moving:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "MovingShuttle"
},
"notificationId": "5bdec871-159e-43d6-9cc9-513829ea0aff",
"timeStamp": "2023-07-31T14:09:06.778574Z",
"contextType": "Task"
}
Waiting for handover:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "WaitForHandover"
},
"notificationId": "3c31c6ba-302e-4fd4-937c-330c5690cc05",
"timeStamp": "2023-07-31T14:09:15.7906275Z",
"contextType": "Task"
}
Task is done, the shuttle is now docked:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "3",
"state": "Done"
},
"notificationId": "443f2351-daaa-42c8-b96c-b9a1ef604fc4",
"timeStamp": "2023-07-31T14:09:28.7749684Z",

28
"contextType": "Task"
}
Undock shuttle
For this use case the lift was moved to level 7.
Payload
The destination property is optional. If the WCS does not specify a destination property, the aSTC will move the shuttle automatically
to a parking spot. This is a POST to {{host}}/api/v1.0/tasks/undock
{
"id": 201,
"source": {
"level": 7,
"lane": 20,
"section": 6,
"index": 1,
"distance": 600,
"exit": "Front",
"entranceConfirmationRequired": false,
"entranceConfirmed": true,
"exitConfirmationRequired": false,
"exitConfirmed": true
},
"vehicle": {
"id": 1
},
"priority": 10
}
Feedback
Undocking:
{
"notificationType": "TaskStateChanged",
"payload": {
"taskId": "201",
"state": "Undocking"
},
"notificationId": "84882fca-ec9c-46a7-88fc-a0f3a3b31c8f",
"timeStamp": "2023-08-10T13:54:20.8256322Z",
"contextType": "Task"
}
2. INTRODUCTION
The ollowing documen oulines he requiremens and specicatons or Movu iFollow’s
produc oerings including rewall nework connectviy requiremens, Movu iFollow server,
AMRs, conguraton compuers ec.
Movu cusomers are requesed o comply wih he prerequisies in his documen and conrm
all required conguraton has been applied and esed ahead o onsie deploymen and
estng.

3. NETWORK AND SERVER INFORMATION

3.1 Connectviy Overview
A connection between our headquarter’s network and Movu iFollow server to be se using
▪ VPN Ipsec connecton which requires knowledge abou VPN Ipsec, a public IP, Pors 500
and 4500 (i behind NAT) o be accessible on he public IP and NAT raversal o be
allowed because he clien’s local nework will need o be NATed

3
Headquarter’s local network needs to be able to access server via SSH on port 22 via the
VPN connection.

Customer will give Movu the Wi-Fi Inormation: ID and Password, or the dierent
machines to connect to the network.
Ino rom the channels, Movu can uses 2 requencies:
▪ 2,4GHz (channel 1 or 6 or 11) or
▪ 5GHz (channel 36 or 40 or 44 o 48) preerred
Everywhere the AMR must go, the signal should be higher than -67dB to avoid package
loss or blockage. The customer should carry out a network audit between 10 cm and 15
cm rom the ground to check signal quality (at the robot antenna height).
3.2 Firewall Conguraton
In a ypical organisaton nework seup, a rewall is used o proec he privae nework hoss
rom he Inerne. Firewalls are congured wih rules o block or allow rafc o and rom
Inerne destnatons based on directon, proocol, and IP address.
The cusomer’s IT deparmen should enable access o Movu iFollow source code reposiory
and conainer regisry rom he company’s inernal ofce/warehouse nework. The rewall
should allow ougoing TCP and UDP rafc rom he AMRs and Movu iFollow server and
allow reurning rac in response.

3.2.1 Movu iFollow Server and Movu headquarters
All connecton beween he Movu iFollow Server and Movu headquarers should ransi
hrough he VPN connecton.

Proocol Por Directo
n

IP Ranges

Source
Code
Server

HTTPS/SS
H 443/22

To Movu
iFollow
Server
gilab.com

Docker
regisry
server

HTTPS/SS
H 443/22

To Movu
iFollow
Server
regisry-1.docker.io

4
IFollow
regisry
server

HTTPS/SS
H 443/22

To Movu
iFollow
Server
regisery.gilab.com
cdn.regisry.gilab-satc.ne

Ubunu
reposiory

HTTPS/SS
H 443/22 r.archive.ubunu.com/ubunu

ROS
Package

HTTPS/SS
H 443/22

To Movu
iFollow
Server
packages.ros.org/ros/ubunu

Docker
Engine

HTTPS/SS
H 443/22

To Movu
iFollow
Server
download.docker.com/linux/ubun
u

Google
Cloud

HTTPS/SS
H 443/22

To Movu
iFollow
Server
grc.io

Daadog HTTPS 443

From
Movu
iFollow
Server
daadoghq.eu

Mongo HTTPS/SS

H 27017 Boh Movu iFollow server IP

Mongo
express

HTTPS/SS
H 8082 Boh Movu iFollow server IP
Mycelium HTTPS 443 Boh Movu iFollow server IP
SSH
connecto
n

SSH 22 Boh Movu iFollow server IP

Moniorin
g

HTTPS 8080/1808

0 Boh Movu iFollow server IP

5
3.2.2 Network between Movu iFollow Server, AMRs and IOT devices

Proocol Por Directon IP Ranges

SSH
connecton SSH 22 Boh AMRs IP

Docker Proxy HTTPS/SSH 3128/3129

From
AMRs o
Server

Movu iFollow server IP

Mongo HTTPS/SSH 27017 Boh Movu iFollow server IP
ROS Daa HTTPS 8080/18080 Boh Movu iFollow server IP
MQTT Daa MQTT

1883/1884/
8084

Boh IOTs IP

4. SERVER REQUIREMENTS
Two options:
- A rackable server provided and confgured by Movu
- A Virtual Machine, provided by the client and confgured by Movu

4.1 Server Sofware & Hardware Requiremens

Movu iFollow Server NUC Reerence Sofware
Recommended up o 6 AMRs Recommended or more han 6

AMRs
OS Ubunu 22.04 Ubunu 22.04
SOFTWARE Docker Engine Docker Engine
RAM 16GB 32GB
PROCESSOR

4.2 Server Bandwidh Requiremens

Movu iFollow Server NUC Reerence Hardware

6
Descripton Size Enable
Bandwidh 100 Mbps
Laency
(RTT) RTT < 50ms
Packe Loss Packe Loss < 2%
VPN Enabled
Local Pors 8080, 8082, 443, 2022, 27017, 22 (SSH),

3128, 3129

Firewall
Rules -
Inerne
Pors

See Table

5. AMR REQUIREMENTS
5.1 AMR Sofware & Hardware Requiremens

Movu iFollow AMR NUC Reerence Sofware
OS Ubunu 22.04
SOFTWARE Docker Engine

5.2 AMR Bandwidh Requiremens
The AMR local WIFI bandwidth must comply with the ollowing requirements

Movu iFollow AMR Reerence Hardware

7
Descripton Size Enable
Signal srengh Min. -67 dBm
Signal o noise
rato

Min. 20 dBm

Daa rae Avg 20 Mbps
Bandwidh Min 1 Mbps / AMR
 he daa rae and bandwidh requiremens should be
inerpreed as ha he nework mus suppor robos
sending and receiving 1 Mb o daa every second wih a
ranser rae o a leas 20 Mbps.
Laency (RTT) RTT < 50ms
Packe Loss Packe Loss < 2%
VPN Enabled
Local Pors 8080, 22 (SSH)
Firewall Rules -
Inerne Pors See Table
Everywhere he AMR mus go, he signal should be higher han -67dB o avoid package loss
or blockage. The cusomer should carry ou a nework audi beween 10 cm and 15 cm rom
he ground o check signal qualiy (a he robo anenna heigh).

5.3 AMR Wi-Fi Anenna Specicatons

iFollow AMR WIFI Anenna Reerence Hardware
Descripton Sirea Tango 12
Frequency range 5 GHz
Sandards 802.11n 802.11ac 802.11ax

8
6. IOT MODULES REQUIREMENTS
6.1 IOT Bandwidh Requiremens
Descripton Size Enable
Signal srengh Min. -67 dBm
Signal o noise
rato

Min. 20 dBm

Daa rae Avg 20 Mbps
Bandwidh Min 1 Mbps / AMR
 he daa rae and bandwidh requiremens should be
inerpreed as ha he nework mus suppor robos
sending and receiving 1 Mb o daa every second wih a
ranser rae o a leas 20 Mbps.
Laency (RTT) RTT < 50ms
Packe Loss Packe Loss < 2%
VPN Enabled
Local Pors 1883, 1884, 8084
Firewall Rules -
Inerne Pors See Table

6.2 IOT Modules Nework Requiremens
Advanech
ADAM Family

ADAM-6050
ADAM-6052

Eherne

Advanech
WISE Family

WISE-4050
WISE-4060

WiFi 2.4 GHz 