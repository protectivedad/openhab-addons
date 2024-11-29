# Honeywell Binding

The binding is used to access the Honeywell thermostats and sensors.

It has been tested in a house with multiple Honeywell Home T9 thermostats each connecting multiple indoor sensors.

The Honeywell system groups thermostats into locations (Home, Cottage, etc) where each location can have multiple thermostats.
Each thermostat in turn is linked to the multiple indoor sensors.

## Prerequisites

A thermostat registered on the "Resideo Smart Home" system.

## Supported Things

The binding has three things.

`oauth20`: A bridge binding that connects to the Honeywell Home information system.
`thermostat`: A thermostat bridge binding that retrieves and transmits the thermostat/sensor data.
`sensor`: A sensor binding that retrieves the sensor information.

## Discovery

Once an authorized bridge has been created and connected, the discovery can search for thermostats and sensors.
It will search all locations and find all thermostats that have been authorized during the creation of the bridge.
Each sensor that is attached to a thermostat will be found and the thermostat itself will show as a sensor.
This means there are at least two discovered devices for each thermostat.
The `thermostat` thing acts as a bridge from the `oauth20` bridge to the `sensor` thing.
Make sure the `thermostat` thing is created first before creating any sensor things (including the sensor thing that is the thermostat).
A `thermostat` thing allows setting and viewing information and a `sensor` thing is readonly.

## Binding Configuration

Each thermostat will require one request for the thermostat information.
Each set of sensors will require one request for the sensor information.
Plus discovery and authorization update requests.
The Honeywell Home information system has a limited amount of requests allowed for each API key.
For this reason it is necessary to setup a new API key when the binding is first used.

After the binding is installed it will create a servlet at `http://<your openHAB address>:8080/connecthoneywell/`.
Visit that address for instructions on creating and linking the Honeywell binding to the Honeywell API.

## Thing Configuration

### `oauth20` Bridge Thing Configuration

| Name              | Type    | Default | Required | Advanced | Description                                   |
|-------------------|---------|---------|----------|----------|-----------------------------------------------|
| consumerKey       | text    | N/A     | yes      | no       | Resideo application consumer key              |
| consumerSecret    | text    | N/A     | yes      | no       | Resideo application consumer secret           |
| optimized         | boolean | false   | yes      | yes      | Has the provided refresh time been optimized? |
| refresh           | integer | 300     | yes      | yes      | Poll time for getting readings from Resideo   |
| timeout           | integer | 3000    | yes      | yes      | The timeout for each request (ms)             |

For a multizoned radiator based system with many sensors the request limit can be hit.
The Honeywell Home developer website gives tools to find the throughput.
If there are periods without any throughput then the limit might have been reached.
Try increasing the refresh time to ensure the system always gets updates.

### `thermostat` Thing Configuration

| Name              | Type    | Default | Required | Advanced | Description                           |
|-------------------|---------|---------|----------|----------|---------------------------------------|
| locationId        | integer | N/A     | yes      | no       | Unique location number for the device |
| deviceId          | text    | N/A     | yes      | no       | Thermostat device id string           |
| groupId           | integer | 0       | yes      | yes      | Only ever seen 0 here just in case    |

1. locationId is just some number Honeywell generates for you.
2. deviceId is LCC- or TCC- followed by the mac address of the thermostat in question.
3. groupId is a grouping of rooms, there isn't any documentation on it leave at 0 unless you know why you need it changed.

### `sensor` Thing Configuration

| Name              | Type    | Default | Required | Advanced | Description                           |
|-------------------|---------|---------|----------|----------|---------------------------------------|
| sensorId          | integer | N/A     | yes      | no       | Index of the sensor                   |

## Channels

| Channel              | Type                 | Read/Write | Thing             | Description                   |
|----------------------|----------------------|------------|-------------------|-------------------------------|
| connected            | switch               | R          | oauth20           | Did the last connect succeed  |
| optimized            | switch               | R          | oauth20           | Refresh timing optimized      |
| refresh              | integer              | R          | oauth20           | Current refresh time used     |
| outdoor-temperature  | number:temperature   | R          | thermostat        | Current outdoor temperature   |
| atmospheric-humidity | number:dimensionless | R          | thermostat        | Current atmosphieric humidity |
| mode                 | string               | RW         | thermostat        | Operating mode                |
| schedulestatus       | string               | RW         | thermostat        | Current status of schedule    |
| setpointstatus       | string               | RW         | thermostat        | Hold mode                     |
| nextperiodtime       | datetime             | RW         | thermostat        | Hold mode timing              |
| heatsetpoint         | number:temperature   | RW         | thermostat        | Heating setpoint temperature  |
| coolsetpoint         | number:temperature   | RW         | thermostat        | Cooling setpoint temperature  |
| indoor-temperature   | number:temperature   | R          | sensor/thermostat | Current indoor temperature    |
| humidity             | number:dimensionless | R          | sensor/thermostat | Current indoor humidity       |
| signal-strength      | number               | R          | sensor            | Signal strength to the base   |
| motion               | switch               | R          | sensor            | Is there motion               |
| occupancy            | switch               | R          | sensor            | Is it marked occupied         |
| low-battery          | switch               | R          | sensor            | Battery status                |
| status               | switch               | R          | sensor            | Status of accessory           |


## Full Example

### Thing Configuration

`.things` file:

```java
Bridge honeywell:oauth20:home "Honeywell API Bridge" [ consumerKey="supersecretkeynoteventellingmom!", consumerSecret="extrasecretsecre", optimized="false", refresh="90" ]

Thing honeywell:thermostat:LCC-112233445566 "Home Thermostat" (honeywell:oauth20:home) [ locationId="9999999", deviceId="LCC-112233445566", groupId="0" ]

Thing honeywell:sensor:LCC-112233445566-0 "Home Thermostat Sensor" (honeywell:thermostat:LCC-112233445566) [ sensorId="0" ]
Thing honeywell:sensor:LCC-112233445566-1 "Bedroom Room Sensor" (honeywell:thermostat:LCC-112233445566) [ sensorId="1" ]

```

### Item Configuration

`.items` file:

```java
// Equipment representing thing:
// honeywell:oauth20:home
// (Honeywell API Bridge)

Group Honeywell_API_Bridge "Honeywell API Bridge" ["Equipment"]

// Points:

Switch      Honeywell_API_Bridge_Connected    "Connected"    <Status> (Honeywell_API_Bridge) ["Status"]  { channel="honeywell:oauth20:home:connected" }
Switch      Honeywell_API_Bridge_Optimized    "Optimized"    <Status> (Honeywell_API_Bridge) ["Status"]  { channel="honeywell:oauth20:home:optimized" }
Number:Time Honeywell_API_Bridge_Refresh_Time "Refresh Time" <Status> (Honeywell_API_Bridge) ["Status"]  { channel="honeywell:oauth20:home:refresh" }

// Equipment representing thing:
// honeywell:thermostat:LCC-112233445566
// (Home Thermostat)

Group Home_Thermostat "Home Thermostat" ["Equipment"]

// Points:

Number:Temperature   Home_Thermostat_measurementsoutdoortemperature  "Outdoor Temperature"  <Temperature>      (Home_Thermostat) ["Temperature", "Measurement"]  { channel="honeywell:thermostat:LCC-112233445566:measurements#outdoor-temperature" }  
Number:Dimensionless Home_Thermostat_measurementsatmospherichumidity "Atmospheric Humidity" <Humidity>         (Home_Thermostat) ["Humidity", "Measurement"]     { channel="honeywell:thermostat:LCC-112233445566:measurements#atmospheric-humidity" } 
Number:Temperature   Home_Thermostat_measurementsindoortemperature   "Indoor Temperature"   <Temperature>      (Home_Thermostat) ["Temperature", "Measurement"]  { channel="honeywell:thermostat:LCC-112233445566:measurements#indoor-temperature" }   
Number:Dimensionless Home_Thermostat_measurementshumidity            "Indoor Humidity"      <Humidity>         (Home_Thermostat) ["Humidity", "Measurement"]     { channel="honeywell:thermostat:LCC-112233445566:measurements#humidity" }             
String               Home_Thermostat_settingsmode                    "Thermostat Mode"      <Heating>          (Home_Thermostat) ["None", "Control"]             { channel="honeywell:thermostat:LCC-112233445566:settings#mode" }                     
String               Home_Thermostat_settingsschedulestatus          "Schedule Status"      <Heating>          (Home_Thermostat) ["None", "Control"]             { channel="honeywell:thermostat:LCC-112233445566:settings#schedulestatus" }           
String               Home_Thermostat_settingssetpointstatus          "Setpoint Status"      <Heating>          (Home_Thermostat) ["Duration", "Control"]         { channel="honeywell:thermostat:LCC-112233445566:settings#setpointstatus" }           
DateTime             Home_Thermostat_settingsnextperiodtime          "Next Period Time"     <Time>             (Home_Thermostat) ["Timestamp", "Control"]        { channel="honeywell:thermostat:LCC-112233445566:settings#nextperiodtime" }           
Number:Temperature   Home_Thermostat_settingsheatsetpoint            "Heat Setpoint"        <Temperature_hot>  (Home_Thermostat) ["Control", "Temperature"]      { channel="honeywell:thermostat:LCC-112233445566:settings#heatsetpoint" }             
Number:Temperature   Home_Thermostat_settingscoolsetpoint            "Cool Setpoint"        <Temperature_cold> (Home_Thermostat) ["Control", "Temperature"]      { channel="honeywell:thermostat:LCC-112233445566:settings#coolsetpoint" }             

// Equipment representing thing:
// honeywell:sensor:LCC-112233445566-0
// (Home Thermostat Sensor)

Group Home_Thermostat_Sensor "Home Thermostat Sensor" ["Equipment"]

// Points:

Number:Temperature   Home_Thermostat_Sensor_readingsindoortemperature "Indoor Temperature" <Temperature>      (Home_Thermostat_Sensor) ["Temperature", "Measurement"]  { channel="honeywell:sensor:LCC-112233445566-0:readings#indoor-temperature" } 
Number:Dimensionless Home_Thermostat_Sensor_readingshumidity          "Indoor Humidity"    <Humidity>         (Home_Thermostat_Sensor) ["Humidity", "Measurement"]     { channel="honeywell:sensor:LCC-112233445566-0:readings#humidity" }           
Number               Home_Thermostat_Sensor_connectionsignalstrength  "Signal Strength"    <QualityOfService> (Home_Thermostat_Sensor) ["Level", "Measurement"]        { channel="honeywell:sensor:LCC-112233445566-0:connection#signal-strength" }  
Switch               Home_Thermostat_Sensor_connectionstatus          "Connected"          <Network>          (Home_Thermostat_Sensor) ["Point"]                       { channel="honeywell:sensor:LCC-112233445566-0:connection#status" }           

// Equipment representing thing:
// honeywell:sensor:LCC-112233445566-1
// (Bedroom Room Sensor)

Group Bedroom_Room_Sensor "Bedroom Room Sensor" ["Equipment"]

// Points:

Number:Temperature   Bedroom_Room_Sensor_readingsindoortemperature "Indoor Temperature" <Temperature>      (Bedroom_Room_Sensor) ["Temperature", "Measurement"]  { channel="honeywell:sensor:LCC-112233445566-1:readings#indoor-temperature" } 
Number:Dimensionless Bedroom_Room_Sensor_readingshumidity          "Indoor Humidity"    <Humidity>         (Bedroom_Room_Sensor) ["Humidity", "Measurement"]     { channel="honeywell:sensor:LCC-112233445566-1:readings#humidity" }           
Switch               Bedroom_Room_Sensor_sensormotion              "Motion"             <Motion>           (Bedroom_Room_Sensor) ["Presence", "Status"]          { channel="honeywell:sensor:LCC-112233445566-1:sensor#motion" }               
Switch               Bedroom_Room_Sensor_sensoroccupancy           "Occupancy"          <Presence>         (Bedroom_Room_Sensor) ["Presence", "Measurement"]     { channel="honeywell:sensor:LCC-112233445566-1:sensor#occupancy" }            
Switch               Bedroom_Room_Sensor_sensorlowbattery          "Low Battery"        <LowBattery>       (Bedroom_Room_Sensor) ["LowBattery", "Energy"]        { channel="honeywell:sensor:LCC-112233445566-1:sensor#low-battery" }          
Number               Bedroom_Room_Sensor_connectionsignalstrength  "Signal Strength"    <QualityOfService> (Bedroom_Room_Sensor) ["Level", "Measurement"]        { channel="honeywell:sensor:LCC-112233445566-1:connection#signal-strength" }  
Switch               Bedroom_Room_Sensor_connectionstatus          "Connected"          <Network>          (Bedroom_Room_Sensor) ["Point"]                       { channel="honeywell:sensor:LCC-112233445566-1:connection#status" }           
```
