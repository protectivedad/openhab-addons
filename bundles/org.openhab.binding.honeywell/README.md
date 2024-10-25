# Honeywell Binding

The binding is used to access the Honeywell thermostats and sensors.

It has been tested in a house with multiple Honeywell Home T9 thermostats each connecting multiple indoor sensors.

The Honeywell system groups thermostats into locations (Home, Cottage, etc) where each location can have multiple thermostats.
Each thermostat in turn is linked to the multiple indoor sensors.

## Supported Things

The binding has three things.

`oauth20`: A bridge binding that connects to the Honeywell Home information system.
`thermostat`: A thermostat bridge binding that retrieves and transmits the information from the thermostat.
`sensor`: A sensor binding that retrieves the information from the sensor.

## Discovery

Once an authorized bridge has been created and connected the discovery can search for thermostats and sensors.
It will search all locations and find all thermostats that have been authorized during the creation of the bridge.
Each sensor that is attached to a thermostat will be found and the thermostat itself will show as a sensor.
This means there are at least two discovered devices for each thermostat.
The thermostat devices act as a bridge from the `oauth20` bridge to the sensors.
Make sure the thermostat thing is created first before creating any sensor things (including the sensor thing that is the thermostat).
A thermostat thing allows setting and viewing information and a sensor device which is readonly.

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
| consumerKey       | text    | N/A     | yes      | no       | Honeywell application consumer key            |
| consumerSecret    | text    | N/A     | yes      | no       | Honeywell application consumer secret         |
| refresh           | integer | 300     | yes      | no       | Poll time for getting readings from Honeywell |
| timeout           | integer | 3000    | yes      | no       | The timeout for each request (ms)             |

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
| locationId        | integer | N/A     | yes      | no       | Unique location number for the device |
| deviceId          | text    | N/A     | yes      | no       | Thermostat device id string           |
| sensorId          | integer | N/A     | yes      | no       | Index of the sensor                   |

## Channels

| Channel         | Type                 | Read/Write | Thing      | Description                    |
|-----------------|----------------------|------------|------------|--------------------------------|
| mode            | string               | RW         | thermostat | Operating mode (Off/Heat/Cool) |
| setpointstatus  | string               | RW         | thermostat | Hold mode                      |
| nextperiodtime  | datetime             | RW         | thermostat | Hold mode timing               |
| heatsetpoint    | number:temperature   | RW         | thermostat | Heating setpoint temperature   |
| coolsetpoint    | number:temperature   | RW         | thermostat | Cooling setpoint temperature   |
| temperature     | number:temperature   | R          | both       | Current room temperature       |
| humidity        | number:dimensionless | R          | both       | Current room humidity          |
| motion          | switch               | R          | sensor     | Is there motion                |
| occupancy       | switch               | R          | sensor     | Is it marked occupied          |
| batterystatus   | string               | R          | sensor     | Battery status (Ok/Low)        |


## Full Example

### Thing Configuration

`.things` file:

```java
Bridge honeywell:oauth20:myhoneywell "Honeywell Authorization Bridge" @ "openhab" [ consumerKey="", consumerSecret="" ] {
    Bridge honeywell:thermostat:mythermostat "Living Room Thermostat" @ "Living Room" [ locationId=1234567, deviceId="LCC-112233445566", groupId=0 ] {
        Channels:
            Type mode : Operating_mode []
            Type temperature : Temperature_livingroom []
            Type humidity : Humidity_livingroom []
        Thing honeywell:sensor:bedroom "My Home Bedroom Sensor" @ "Master Bedroom" [ sensorId=1 ] {
            Channels:
                Type temperature : Temperature_bedroom []
                Type humidity : Humidity_bedroom []
                Type occupancy : Occupancy_bedroom []
        }
        Thing honeywell:sensor:kitchen "My Home Kitchen Sensor" @ "Kitchen" [ sensorId=2 ] {
            Channels:
                Type temperature : Temperature_kitchen []
                Type humidity : Humidity_kitchen []
                Type occupancy : Occupancy_kitchen []
        }
    }
}
```

### Item Configuration

`.items` file:

```java
// Equipment representing thing:
// honeywell:thermostat:mythermostat:LCC-112233445566
// (Living Room Thermostat)

Group Living_Room_Thermostat "Living Room Thermostat" ["Equipment"]

// Points:

Number:Temperature   Living_Room_Thermostat_Temperature_Channel "Temperature Channel" <Temperature>      (Living_Room_Thermostat) ["Measurement", "Temperature"]  { channel="honeywell:thermostat:mythermostat:LCC-112233445566:temperature" }    
Number:Dimensionless Living_Room_Thermostat_Humidity_Channel    "Humidity Channel"    <Humidity>         (Living_Room_Thermostat) ["Measurement", "Humidity"]     { channel="honeywell:thermostat:mythermostat:LCC-112233445566:humidity" }       
String               Living_Room_Thermostat_Thermostat_Mode     "Thermostat Mode"     <heating>          (Living_Room_Thermostat) ["Control", "None"]             { channel="honeywell:thermostat:mythermostat:LCC-112233445566:mode" }           
String               Living_Room_Thermostat_Setpoint_Status     "Setpoint Status"                        (Living_Room_Thermostat) ["Control", "Duration"]         { channel="honeywell:thermostat:mythermostat:LCC-112233445566:setpointstatus" } 
DateTime             Living_Room_Thermostat_Next_Period_Time    "Next Period Time"    <time>             (Living_Room_Thermostat) ["Control", "Timestamp"]        { channel="honeywell:thermostat:mythermostat:LCC-112233445566:nextperiodtime" } 
Number:Temperature   Living_Room_Thermostat_Heat_Setpoint       "Heat Setpoint"       <temperature_hot>  (Living_Room_Thermostat) ["Temperature", "Control"]      { channel="honeywell:thermostat:mythermostat:LCC-112233445566:heatsetpoint" }   
Number:Temperature   Living_Room_Thermostat_Cool_Setpoint       "Cool Setpoint"       <temperature_cold> (Living_Room_Thermostat) ["Temperature", "Control"]      { channel="honeywell:thermostat:mythermostat:LCC-112233445566:coolsetpoint" }   
```
