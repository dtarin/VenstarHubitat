 import groovy.json.JsonSlurper
/**
 *  Venstar T5800
 *
 *  Copyright 2014 Scottin Pollock
 *  Modifications copyright (C) 2019 David Tarin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
  definition (name: "Venstar Thermostat", namespace: "Thermostat", author: "Scottin Pollock, David Tarin") {
    capability "Polling"
    capability "Refresh"
    capability "Temperature Measurement"
    capability "Sensor"

//capability "Thermostat"  //deprecated in latest smart things api
    capability "Thermostat Cooling Setpoint"
    capability "Thermostat Fan Mode"
    capability "Thermostat Heating Setpoint"
    capability "Thermostat Mode"
    capability "Thermostat Operating State"
//capability "Thermostat Setpoint"

  }

  preferences {
    section("Driver config") {
      input "thermostatIP", "text", title: "Thermostat IP", required: false
      input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
  }

  simulator {
    // TODO: define status and reply messages here
  }

//this appears to only apply for Samsung SmartThings
/*
  tiles {
      valueTile("temperature", "device.temperature", width: 2, height: 2) {
      state("temperature", label:'${currentValue}°', unit:"F",
        backgroundColors:[
          [value: 31, color: "#153591"],
          [value: 44, color: "#1e9cbb"],
          [value: 59, color: "#90d2a7"],
          [value: 74, color: "#44b621"],
          [value: 84, color: "#f1d801"],
          [value: 95, color: "#d04e00"],
          [value: 96, color: "#bc2323"]
        ]
      )
    }
    standardTile("mode", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
      state "off", label:'${name}', action:"thermostat.setThermostatMode"
      state "cool", label:'${name}', action:"thermostat.setThermostatMode"
      state "heat", label:'${name}', action:"thermostat.setThermostatMode"
    }
    standardTile("fanMode", "device.thermostatFanMode", inactiveLabel: false, decoration: "flat") {
      state "fanAuto", label:'${name}', action:"thermostat.setThermostatFanMode"
      state "fanOn", label:'${name}', action:"thermostat.setThermostatFanMode"
    }
      controlTile("heatSliderControl", "device.heatingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false) {
      state "setHeatingSetpoint", action:"thermostat.setHeatingSetpoint", backgroundColor:"#d04e00"
    }
    valueTile("heatingSetpoint", "device.heatingSetpoint", inactiveLabel: false, decoration: "flat") {
      state "heat", label:'${currentValue}° heat', unit:"F", backgroundColor:"#ffffff"
    }
    controlTile("coolSliderControl", "device.coolingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false) {
      state "setCoolingSetpoint", action:"thermostat.setCoolingSetpoint", backgroundColor: "#1e9cbb"
    }
    valueTile("coolingSetpoint", "device.coolingSetpoint", inactiveLabel: false, decoration: "flat") {
      state "cool", label:'${currentValue}° cool', unit:"F", backgroundColor:"#ffffff"
    }
    standardTile("refresh", "device.temperature", inactiveLabel: false, decoration: "flat") {
      state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    main "temperature"
        details(["temperature", "mode", "fanMode", "heatSliderControl", "heatingSetpoint", "coolSliderControl", "coolingSetpoint", "refresh"])
  }//tiles
*/
}//metadata


// parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
    def map = [:]
    def retResult = []
    def descMap = parseDescriptionAsMap(description)
    log.debug "parse returns $descMap"
    def body = new String(descMap["body"].decodeBase64())
    def slurper = new JsonSlurper()
    def result = slurper.parseText(body)
    log.debug "json is: $result"
    if (result.containsKey("success")){
      //Do nothing as nothing can be done. (runIn doesn't appear to work here and apparently you can't make outbound calls here)
        log.debug "returning now"
        return null
    }
    if (result.containsKey("mode")){
      def mode = getModeMap()[result.mode]
        if(device.currentState("thermostatMode")?.value != mode){
            retResult << createEvent(name: "thermostatMode", value: mode)
        }
    }
    if (result.containsKey("fan")){
      def fan = getFanModeMap()[result.fan]
        if (device.currentState("thermostatFanMode")?.value != fan){
            retResult << createEvent(name: "thermostatFanMode", value: fan)
        }
    }
    if (result.containsKey("cooltemp")){
      def cooltemp = getTemperature(result.cooltemp)
      if (device.currentState("coolingSetpoint")?.value != cooltemp.toString()){
            retResult << createEvent(name: "coolingSetpoint", value: cooltemp)
        }
    }
    if (result.containsKey("heattemp")){
      def heattemp = getTemperature(result.heattemp)
      if (device.currentState("heatingSetpoint")?.value != heattemp.toString()){
            retResult << createEvent(name: "heatingSetpoint", value: heattemp)
        }
    }
    if (result.containsKey("spacetemp")){
      def temp = getTemperature(result.spacetemp)
      if (device.currentState("temperature")?.value != spacetemp.toString()){
            retResult << createEvent(name: "temperature", value: spacetemp)
        }
    }

    log.debug "Parse returned $retResult"
    if (retResult.size > 0){
    return retResult
    } else {
      return null
    }

}
def poll() {
  log.debug "Executing 'poll'"
  sendEvent(descriptionText: "poll keep alive", isStateChange: false)  // workaround to keep polling from being shut off
  refresh()
}
def modes() {
  ["off", "heat", "cool", "auto"]
}
def parseDescriptionAsMap(description) {
  description.split(",").inject([:]) { map, param ->
    def nameAndValue = param.split(":")
    map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
  }
}

def getModeMap() { [
  0:"off",
  2:"cool",
  1:"heat",
  3:"auto"
]}

def getFanModeMap() { [
  0:"fanAuto",
    1:"fanOn"
]}

def getTemperature(value) {
  if(getTemperatureScale() == "C"){
    return (((value-32)*5.0)/9.0)
  } else {
    return value
  }
}

// handle commands THINK WE NEED A STATE TO HOLD FAN,HEATTEMP,COOLTEMP VALUES
def setHeatingSetpoint(degrees) {
  def degreesInteger = degrees as Integer
  log.debug "Executing 'setHeatingSetpoint' with ${degreesInteger}"
  //todo: does this need to have the {" removed and : changed to =?
  //https://community.smartthings.com/t/venstar-t5800-really-stuck-on-my-first-device-type/2871/19?u=partientturtle
    //postapi("{\"it_heat\"=${degreesInteger}}")
  postapi("heattemp=${degreesInteger}")
}

def setCoolingSetpoint(degrees) {
  def degreesInteger = degrees as Integer
  log.debug "Executing 'setCoolingSetpoint' with ${degreesInteger}"
  //todo: does this need to have the {" removed and : changed to =?
  //https://community.smartthings.com/t/venstar-t5800-really-stuck-on-my-first-device-type/2871/19?u=partientturtle
    //postapi("{\"it_cool\"=${degreesInteger}}")
}

def off() {
  log.debug "Executing 'off'"
    postapi('mode=0')
}

def heat() {
  log.debug "Executing 'heat'"
  postapi('mode=1')
}

def cool() {
  log.debug "Executing 'cool'"
  postapi('mode=2')
}

def setThermostatMode() {
  log.debug "switching thermostatMode"
  def currentMode = device.currentState("thermostatMode")?.value
  def modeOrder = modes()
  def index = modeOrder.indexOf(currentMode)
  def next = index >= 0 && index < modeOrder.size() - 1 ? modeOrder[index + 1] : modeOrder[0]
  log.debug "switching mode from $currentMode to $next"
  "$next"()
}

def fanOn() {
  log.debug "Executing 'fanOn'"
  postapi("fan=1")
}

def fanAuto() {
  log.debug "Executing 'fanAuto'"
  postapi("fan=0")
}

def setThermostatFanMode() {
  log.debug "Switching fan mode"
  def currentFanMode = device.currentState("thermostatFanMode")?.value
  log.debug "switching fan from current mode: $currentFanMode"
  def returnCommand

  switch (currentFanMode) {
    case "fanAuto":
      returnCommand = fanOn()
      break
        case "fanOn":
      returnCommand = fanAuto()
      break
  }
  if(!currentFanMode) { returnCommand = fanAuto() }
  returnCommand
}

def auto() {
  log.debug "Executing 'auto'"
  postapi("mode=3")
}
def refresh() {
  log.debug "Executing 'refresh'"
  getapi()
}

private getapi() {
  def uri = "http://"+getHostAddress()+"/query/info"
  log.debug("Executing get api to " + uri)
/*
  def hubAction = new physicalgraph.device.HubAction(
    method: "GET",
    path: uri,
    headers: [HOST:getHostAddress()]
  )
  hubAction
*/

  try {
        httpGet(uri) { resp ->
            if (resp.success) {
                //todo: do something?
            }
            if (logEnable)
                if (resp.data) log.info "${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Call to getapi() failed: ${e.message}"
  }


}

private createModeChangeCommand(){
  def command =
      "mode=1" + "&" //setting mode to HEAT
      + "fan=0" + "&" //fan to AUTO
      + "heattemp=65" + "&"
      + "cooltemp=78"
  return command
}

private postapi(command) {
/*
  def hubAction = [new physicalgraph.device.HubAction(
    method: "POST",
    path: uri,
    body: command,
    headers: [Host:getHostAddress(), "Content-Type":"application/x-www-form-urlencoded" ]
    ), delayAction(1000), refresh()]
  hubAction
*/
  if (logEnable) log.info("Executing command: [${command}]")
  def uri = "http://"+getHostAddress()+"/control"
  if (logEnable) log.info("Sending on POST request to [${uri}]")

  def postParams = [
    uri: uri,
        contentType: "application/x-www-form-urlencoded",
    body : command
  ]

    try {
        httpPost(postParams) { resp ->
            if (resp.success) {
        log.debug "${resp.data}"
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
        }
    } catch (Exception e) {
    log.warn "Call to postapi(${command}) failed: ${e.message}"
  }
}

//helper methods
private delayAction(long time) {
  //todo: fix this next line
  //new physicalgraph.device.HubAction("delay $time")
}
private Integer convertHexToInt(hex) {
  Integer.parseInt(hex,16)
}
private String convertHexToIP(hex) {
  [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
  /*
  def parts = device.deviceNetworkId.split(":")
  def ip = convertHexToIP(parts[0])
  def port = convertHexToInt(parts[1])
  return ip + ":" + port
  */
  return settings.thermostatIP
}
