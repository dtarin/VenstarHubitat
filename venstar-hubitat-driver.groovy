/**
 *  Venstar Hubitat Driver
 *
 *  Copyright 2014 Scottin Pollock
 *  Modifications copyright (C) 2019 David Tarin
 *  Fixes, refactoring & functionality copyright (C) 2019 CybrMage
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
 *  Version: 0.13
 */

import groovy.json.JsonSlurper

metadata {
	definition (name: "Venstar Thermostat", namespace: "Thermostat", author: "CybrMage, Scottin Pollock, David Tarin") {
		capability "Actuator"
		capability "Initialize"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Thermostat"
		capability "RelativeHumidityMeasurement"
		capability "PresenceSensor"
		
		attribute "thermostatFanOperatingState","enum",["off","on"]
	}

	preferences {
		section("Driver config") {
			input "thermostatIP", "text", title: "Thermostat IP", required: false
			def pollRate = ["1" : "Poll every minute", "5" : "Poll every 5 minutes", "10" : "Poll every 10 minutes", "15" : "Poll every 15 minutes", "30" : "Poll every 30 minutes", "60" : "Poll every hour", "180" : "Poll every 3 hours"]
			input ("Poll_Rate", "enum", title: "Device Poll Rate", options: pollRate, defaultValue: "10")
			input name: "sensorEnable", type: "bool", title: "Enable sensor child devices", defaultValue: false
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
			input name: "testMode", type: "bool", title: "Enable offline test mode", defaultValue: false
		}
	}
}

private getVERSION() { "v0.13" }

// parse events into attributes
def parseJsonData(result) {
	if (logEnable) log.debug("parseJsonData: data is: $result")
	if (result.success != null){
		//Do nothing as nothing can be done. (runIn doesn't appear to work here and apparently you can't make outbound calls here)
		if (logEnable) log.debug "parseJsonData: getapi()/postapi() data indicates success"
		return
	}
	if (result.error != null){
		if (logEnable) log.debug "parseJsonData: getapi()/postapi() data indicates error: ${result.reason}"
		return
	}
	if (result.mode != null){
		if (logEnable) log.debug "parseJsonData: parsing result.mode"
		def mode = getModeMap()[result.mode]
		state.thermostatMode = result.mode
		if(device.currentState("thermostatMode")?.value != mode){
			sendEvent(name: "thermostatMode", value: mode, descriptionText: "thermostatMode set to ${mode}", isStateChange: true)
		}
	}
	if (result.state != null){
		if (logEnable) log.debug "parseJsonData: parsing result.state"
		def mode = getOperatingModeMap()[result.state]
		state.thermostatOperatingState = result.state
		if(device.currentState("thermostatOperatingState")?.value != mode){
			sendEvent(name: "thermostatOperatingState", value: mode, descriptionText: "thermostatOperatingState set to ${mode}", isStateChange: true)
		}
	}
	if (result.fan != null){
		if (logEnable) log.debug "parseJsonData: parsing result.fan"
		def fan = getFanModeMap()[result.fan]
		state.thermostatFanMode = result.fan
		if (device.currentState("thermostatFanMode")?.value != fan){
			sendEvent(name: "thermostatFanMode", value: fan, descriptionText: "thermostatFanMode set to ${fan}", isStateChange: true)
		}
	}
	if (result.fanstate != null){
		if (logEnable) log.debug "parseJsonData: parsing result.fanstate"
		def mode = (result.fanstate == 0)?"off":"on"
		state.thermostatFanOperatingState = result.fanstate
		if(device.currentState("thermostatFanOperatingState")?.value != mode){
			sendEvent(name: "thermostatFanOperatingState", value: mode, descriptionText: "thermostatFanOperatingState set to ${mode}", isStateChange: true)
		}
	}
	if (result.tempunits != null){
		if (logEnable) log.debug "parseJsonData: parsing result.tempunits"
		def temperatureScale = ((result.tempunits as Integer) == 0) ? "F" : "C"
		state.temperatureScale = temperatureScale
		if (device.currentState("temperatureScale")?.value != temperatureScale.toString()){
			sendEvent(name: "temperatureScale", value: temperatureScale, descriptionText: "temperatureScale set to ${temperatureScale}", isStateChange: true)
		}
	} else {
		if (result.spacetemp != null){
			def temp = result.spacetemp
			if (temp < 32) {
				state.temperatureScale = "C"
			} else {
				state.temperatureScale = "F"
			}
		} else {
			state.temperatureScale = "F"
		}
		sendEvent(name: "temperatureScale", value: temperatureScale, descriptionText: "temperatureScale set to ${temperatureScale}", isStateChange: true)
	}
	if (result.cooltempmin != null){
		if (logEnable) log.debug "parseJsonData: parsing result.cooltempmin"
		state.cooltempmin = result.cooltempmin
	}
	if (result.cooltempmax != null){
		if (logEnable) log.debug "parseJsonData: parsing result.cooltempmax"
		state.cooltempmax = result.cooltempmax
	}
	if (result.heattempmin != null){
		if (logEnable) log.debug "parseJsonData: parsing result.heattempmin"
		state.heattempmin = result.heattempmin
	}
	if (result.heattempmax != null){
		if (logEnable) log.debug "parseJsonData: parsing result.heattempmax"
		state.heattempmax = result.heattempmax
	}
	if (result.cooltemp != null){
		if (logEnable) log.debug "parseJsonData: parsing result.cooltemp"
		def cooltemp = (getTemperatureHE(result.cooltemp) as Float).round(1)
		state.cooltemp = result.cooltemp
		if (device.currentState("coolingSetpoint")?.value != cooltemp.toString()){
			sendEvent(name: "coolingSetpoint", value: cooltemp, descriptionText: "coolingSetpoint set to ${cooltemp}", isStateChange: true)
		}
	}
	if (result.heattemp != null){
		if (logEnable) log.debug "parseJsonData: parsing result.heattemp"
		def heattemp = (getTemperatureHE(result.heattemp) as Float).round(1)
		state.heattemp = result.heattemp
		if (device.currentState("heatingSetpoint")?.value != heattemp.toString()){
			sendEvent(name: "heatingSetpoint", value: heattemp, , descriptionText: "Heating setpoint set to ${heattemp}", isStateChange: true)
		}
	}
	if (result.spacetemp != null){
		if (logEnable) log.debug "parseJsonData: parsing result.spacetemp"
		def temp = (getTemperatureHE(result.spacetemp) as Float).round(1)
		state.temperature = result.spacetemp
		if (device.currentState("temperature")?.value != spacetemp.toString()){
			sendEvent(name: "temperature", value: temp, descriptionText: "temperature set to ${temp}", isStateChange: true)
		}
	}
	if (result.state != null){
		if ( state.thermostatOperatingState == 2) {
			state.thermostatSetpoint = state.cooltemp
		} else {
			state.thermostatSetpoint = state.heattemp
		}
	    sendEvent(name: "thermostatSetpoint", value: (getTemperatureHE(state.thermostatSetpoint) as Float).round(1), descriptionText: "thermostatSetpoint set to ${(getTemperatureHE(state.thermostatSetpoint) as Float).round(1)}", isStateChange: true)
	}
	if (result.away != null){
		if (logEnable) log.debug "parseJsonData: parsing result.away"
		def mode = getAwayModes()[result.away]
		state.presence = result.away
		if(device.currentState("presence")?.value != mode){
			sendEvent(name: "presence", value: mode, descriptionText: "presence set to ${mode}", isStateChange: true)
		}
	}
	if (result.hum != null){
		if (logEnable) log.debug "parseJsonData: parsing result.hum"
		state.humidity = result.hum
		if(device.currentState("humidity")?.value != mode){
			sendEvent(name: "humidity", value: state.humidity, descriptionText: "humidity set to ${mode}", isStateChange: true)
		}
	}
	if (result.sensors != null) {
		if (logEnable) log.debug "parseJsonData: parsing SENSOR data"
		result.sensors.each { sensor ->
			def sensorDNI = device.deviceNetworkId + "-ep" + sensor.name.replaceAll("\\s","").replaceAll("\\W","")
			def targetChild = getChildDevice(sensorDNI)
			if (targetChild != null) {
				if (logEnable) log.debug "parseJsonData: updating SENSOR data for "+sensorDNI
				if (sensor.temp != null) targetChild.setTemperature(sensor.temp)
				if (sensor.hum != null) targetChild.setHumidity(sensor.hum)
			} else {
				if (logEnable) log.warn "parseJsonData: child SENSOR does not exist for "+sensorDNI
			}
		}
	}
}

def poll() {
  if (logEnable) log.debug("poll: Executing 'poll'")
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

def getAwayModes() { [
  0:"present",
  1:"not present"
]}

def getModeMap() { [
  0:"off",
  2:"cool",
  1:"heat",
  3:"auto"
]}

def getOperatingModeMap() { [
	0: "idle",
	1: "heating",
	2: "cooling",
	3: "lockout",
	4: "error"
]}

def getFanModeMap() {[
	0:"auto",
	1:"on"
]}

def clampTemp(temp) {
	def T1 = Math.floor(temp)
	def T2 = temp - T1
	def T3 = 0
	if ((T2 >= 0.25) && (T2 <= 0.75)) {
		T3 = 0.5
	} else if (T2 > 0.75) {
		T3 = 1.0
	}
	def T4 = T1 + T3
	if (logEnable) log.debug("  clampTemp: triming value ${temp} to ${T4}")
	return T4
}

def getTemperatureHE(value) {
	if (logEnable) log.debug("  getTemperatureHE: testing for temperature scale conversion for temperature value ${value}")
	def scaleHE = getTemperatureScale()
	def scaleVS = (device.currentState("temperatureScale")?.value)?(device.currentState("temperatureScale")?.value):state.temperatureScale
	if (logEnable) log.debug("  getTemperatureHE: temperatureScale - HE = ${scaleHE}  VS = ${scaleVS}")
	if ( !scaleHE || !scaleVS || (scaleHE == scaleVS)) {
		value = clampTemp(value)
		if (logEnable) log.debug("  getTemperatureHE: no temperature scale conversion required for temperature value ${value}")
		return value
	}
	if ((scaleVS == "F") && (scaleHE == "C")) {
		// F to C
		def tempC = (((value-32)*5.0)/9.0)
		tempC = clampTemp(tempC)
		if (logEnable) log.warn("  getTemperatureHE: temperature scale conversion required. temperature value ${value}F = ${tempC}C")
		return tempC
	} else {
		// C to F
		def tempF = (((value * 9.0)/5.0)+32)
		tempF = clampTemp(tempF)
		if (logEnable) log.warn("  getTemperatureHE: temperature scale conversion required. temperature value ${value}C = ${tempF}F")
		return tempF
	}
}

def getTemperatureVS(value) {
	if (logEnable) log.debug("  getTemperatureVS: testing for temperature scale conversion for temperature value ${value}")
	def scaleHE = getTemperatureScale()
	def scaleVS = (device.currentState("temperatureScale")?.value)?(device.currentState("temperatureScale")?.value):state.temperatureScale
	if (logEnable) log.debug("  getTemperatureVS: temperatureScale - VS = ${scaleVS}  HE = ${scaleHE}")
	if ( !scaleHE || !scaleVS || (scaleHE == scaleVS)) {
		value = clampTemp(value)
		if (logEnable) log.debug("  getTemperatureVS: no temperature scale conversion required for temperature value ${value}")
		return value
	}
	if ((scaleHE == "F") && (scaleVS == "C")) {
		// F to C
		def tempC = (((value-32)*5.0)/9.0)
		tempC = clampTemp(tempC)
		if (logEnable) log.warn("  getTemperatureVS: temperature scale conversion required. temperature value ${value}F = ${tempC}C")
		return tempC
	} else {
		// C to F
		def tempF = (((value * 9.0)/5.0)+32)
		tempF = clampTemp(tempF)
		if (logEnable) log.warn("  getTemperatureVS: temperature scale conversion required. temperature value ${value}C = ${tempF}F")
		return tempF
	}
}

// handle commands
def getValidHeatSetpoint(newTemp) {
	if (logEnable) log.debug("  getValidHeatSetpoint: testing heat setpoint for temperature value ${newTemp}")
	if (!state.heattempmin || !state.heattempmax) { 
		if (logEnable) log.debug("  getValidHeatSetpoint: no restriction for temperature value ${newTemp}")
		return newTemp 
	}
	def minTemp = state.heattempmin as Integer
	def maxTemp = state.heattempmax as Integer
	if (newTemp < minTemp) {
		if (logEnable) log.warn("  getValidHeatSetpoint: heat setpoint restricted to minimum temperature value ${minTemp}")
		return minTemp
	} else if (newTemp > maxTemp) {
		if (logEnable) log.warn("  getValidHeatSetpoint: heat setpoint restricted to maximum temperature value ${maxTemp}")
		return maxTemp
	} else {
		if (logEnable) log.debug("  getValidHeatSetpoint: no restriction for temperature value ${newTemp}")
		return newTemp
	}
}

def getValidCoolSetpoint(newTemp) {
	if (logEnable) log.debug("  getValidCoolSetpoint: testing cool setpoint for temperature value ${newTemp}")
	if (!state.cooltempmin || !state.cooltempmax) { 
		if (logEnable) log.debug("getValidCoolSetpoint: no restriction for temperature value ${newTemp}")
		return newTemp 
	}
	def minTemp = state.cooltempmin as Integer
	def maxTemp = state.cooltempmax as Integer
	if (newTemp < minTemp) {
		if (logEnable) log.warn("  getValidCoolSetpoint: cool setpoint restricted to minimum temperature value ${minTemp}")
		return minTemp
	} else if (newTemp > maxTemp) {
		if (logEnable) log.warn("  getValidCoolSetpoint: cool setpoint restricted to maximum temperature value ${maxTemp}")
		return maxTemp
	} else {
		if (logEnable) log.debug("  getValidCoolSetpoint: no restriction for temperature value ${newTemp}")
		return newTemp
	}
}

def setCommonSetpoint() {
	if ( state.thermostatOperatingState == 2) {
		state.thermostatSetpoint = state.cooltemp
	} else {
		state.thermostatSetpoint = state.heattemp
	}
	def newTemp = clampTemp(getTemperatureHE(state.thermostatSetpoint))
	if (logEnable) log.debug "  setCommonSetpoint: Executing 'setCommonSetpoint' with ${newTemp} (originally - ${state.thermostatSetpoint})"
    sendEvent(name: "thermostatSetpoint", value: newTemp, descriptionText: "thermostatSetpoint set to ${newTemp}", isStateChange: true)
}

def setHeatingSetpoint(degrees) {
	if (logEnable) log.debug "Executing 'setHeatingSetpoint' with ${degrees}"
	def degreesHE = clampTemp(degrees)
	def degreesVS = getTemperatureVS(degreesHE)
	state.heattemp = getValidHeatSetpoint(degreesVS)
	if (state.heattemp != degreesVS) {
		// temperature was restricted - convert restricted temperature back to HE format
		degreesHE = getTemperatureHE(degreesVS)
	}
	if (logEnable) log.debug "processing 'setHeatingSetpoint' with ${degreesHE} (originally ${degrees})"
	setCommonSetpoint()
	sendEvent(name: "heatingSetpoint", value: degreesHE, descriptionText: "Heating setpoint set to ${degreesHE}", isStateChange: true)
	postapi()
}

def setCoolingSetpoint(degrees) {
	if (logEnable) log.debug "Executing 'setCoolingSetpoint' with ${degrees}"
	def degreesHE = clampTemp(degrees)
	def degreesVS = getTemperatureVS(degreesHE)
	state.cooltemp = getValidCoolSetpoint(degreesVS)
	if (state.cooltemp != degreesVS) {
		// temperature was restricted - convert restricted temperature back to HE format
		degreesHE = getTemperatureHE(degreesVS)
	}
	if (logEnable) log.debug "processing 'setCoolingSetpoint' with ${degreesHE} (originally ${degrees})"
	sendEvent(name: "coolingSetpoint", value: degreesHE, descriptionText: "Cooling setpoint set to ${degreesHE}", isStateChange: true)
	setCommonSetpoint()
	postapi()
}

def off() {
	if (logEnable) log.debug "Executing 'off'"
	state.thermostatMode = 0
	sendEvent(name: "thermostatMode", value: getModeMap()[0], descriptionText: "Thermostat Mode set to ${getModeMap()[0]}", isStateChange: true)
	postapi()
}

def heat() {
	if (logEnable) log.debug "Executing 'heat'"
	state.thermostatMode = 1
	sendEvent(name: "thermostatMode", value: getModeMap()[1], descriptionText: "Thermostat Mode set to ${getModeMap()[1]}", isStateChange: true)
	postapi()
}

def cool() {
	if (logEnable) log.debug "Executing 'cool'"
	state.thermostatMode = 2
	sendEvent(name: "thermostatMode", value: getModeMap()[2], descriptionText: "Thermostat Mode set to ${getModeMap()[2]}", isStateChange: true)
	postapi()
}

def auto() {
	if (logEnable) log.debug "Executing 'auto'"
	state.thermostatMode = 3
	sendEvent(name: "thermostatMode", value: getModeMap()[3], descriptionText: "Thermostat Mode set to ${getModeMap()[3]}", isStateChange: true)
	postapi()
}

def emergencyHeat() {
	if (logEnable) log.error "'emergencyHeat' is not supported by this device"
}

def setSchedule(String json) {
	if (logEnable) log.error "'setSchedule' is not supported by this device driver"
}

def setThermostatMode(String newMode) {
	if (logEnable) log.debug("setThermostatMode(${newMode})")
	switch(newMode) {
		case "off":
			off()
			break
		case "heat":
			heat()
			break
		case "cool":
			cool()
			break
		case "auto":
			auto()
			break
		default:
			if (logEnable) log.error("setThermostatMode: mode '${newMode}' is not supported by this device")
			break
	}
}

def fanOn() {
	if (logEnable) log.debug "Executing 'fanOn'"
	state.thermostatFanMode = 1
	sendEvent(name: "thermostatFanMode", value: getFanModeMap()[1], descriptionText: "Thermostat Fan Mode set to ${getFanModeMap()[1]}", isStateChange: true)
	postapi()
}

def fanAuto() {
	if (logEnable) log.debug "Executing 'fanAuto'"
	state.thermostatFanMode = 0
	sendEvent(name: "thermostatFanMode", value: getFanModeMap()[0], descriptionText: "Thermostat Fan Mode set to ${getFanModeMap()[0]}", isStateChange: true)
	postapi()
}

def fanCirculate() {
	if (logEnable) log.error("'fanCirculate' is not supported by this device")
}

def setThermostatFanMode(String newMode) {
	if (logEnable) log.debug("setThermostatFanMode(${newMode.trim()})")
	switch(newMode.trim()) {
		case "on":
		case "fanOn":
			fanOn()
			break
		case "auto":
		case "fanAuto":
			fanAuto()
			break
		default:
			if (logEnable) log.error("setThermostatFanMode: mode '${newMode}' is not supported by this device")
			break
	}
}

def refresh() {
	if (logEnable) log.debug "Executing 'refresh'"
	getapi()
	if (sensorEnable) {
		if (logEnable) log.warn "Executing 'SENSOR refresh'"
		getSensors()
	}
}

private getapi() {
	def uri = "http://"+getHostAddress()+"/query/info"
	if (logEnable) log.debug("getapi: Executing get api to " + uri)
	if (testMode) uri = "http://127.0.0.1/api/json/utc/now"

	try {
		def requestParams =
		[
			uri:  uri,
			requestContentType: "application/x-www-form-urlencoded",
			contentType: "application/json"
		]
		httpGet(requestParams) { resp ->
			if (resp.status == 200) {
				if (logEnable) log.debug "getapi: httpGet returned: \n${resp.data}"
				parseJsonData(resp.data)
			} else {
				log.error("getapi: httpPost returned http failure code ${resp.status}")
			}
			if (logEnable) log.debug "getapi: getapi() completed"
		}
	} catch (Exception e) {
		if (testMode && (e.message == "Connect to 127.0.0.1:80 [/127.0.0.1] failed: Connection refused (Connection refused)")) {
			return parseJsonData(testDATA())
		}
		log.error "getapi: Call to getapi() failed: ${e.message}"
	}
}

private createModeChangeCommand(){
	def command = "mode=" + state.thermostatMode +
		"&fan=" + state.thermostatFanMode +
		"&heattemp=" + state.heattemp + 
		"&cooltemp=" + state.cooltemp
	return command
}

private postapi() {
	if (logEnable) log.info("postapi: Executing API Control update")
	def uri = "http://"+getHostAddress()+"/control"
	def command = createModeChangeCommand()

	if (logEnable) {
		if (testMode) {
			log.info("postapi: Test Mode: POST request to [${uri}] with command [${command}]")
		} else {
			log.info("postapi: Sending on POST request to [${uri}] with command [${command}]")
		}
	}
	if (testMode) {
		return getapi()
	}

	def postParams = [
		uri: uri,
		contentType: "application/json",
		requestContentType: "application/x-www-form-urlencoded",
		body : command
	]

	try {
		httpPost(postParams) { resp ->
			if (resp.status == 200) {
				if (logEnable) log.debug "postapi: httpPost returned: \n${resp.data}"
				if (resp.data.success == true) {
					if(logEnable) log.debug("postapi: httpGet returned success")
					// update the thermostat state
					getapi()
				} else{
					log.error("postapi: httpGet returned failed")
				}
			} else {
				log.error("postapi: httpPost returned http failure code ${resp.status}")
			}
		}
	} catch (Exception e) {
		log.error "postapi: Call to postapi(${command}) failed: ${e.message}"
	}
}

//helper methods
private getHostAddress() {
  return settings.thermostatIP
}

// driver default methods
def updated() {
  log.debug("updated: Venstar Driver ${VERSION} - Updating parameters")
  initialize()
}

def installed() {
  log.debug("installed: Venstar Driver ${VERSION} - Installed")
}

def initialize() {
	log.debug("initialize: Venstar Driver ${VERSION} - Initializing device ${device.getDeviceNetworkId()}")
	unschedule()
	sendEvent(name: "temperatureDisplayScale", value: getTemperatureScale(), descriptionText: "temperatureDisplayScale set to ${getTemperatureScale()}", isStateChange: true)
	sendEvent(name: "supportedThermostatFanModes", value: ["auto","on"], isStateChange: true)
	sendEvent(name: "supportedThermostatModes", value: ["off","heat","cool","auto"], isStateChange: true)
  
	if (getHostAddress()) {
		// get the initial thermostat state
		if (sensorEnable) {
			def sensorData = getSensors(true)
			createSensors(sensorData)
		} else {
			// remove child sensors if needed
			getChildDevices().each { sensor ->
				if (logEnable) log.debug ("removing disabled sensor ${sensor.deviceNetworkId}")
				deleteChildDevice(sensor.deviceNetworkId)
			}
		}
		getapi()
	} else {
		log.debug("initialize: Venstar IP Address not set")
		return
	}
	def poll = Poll_Rate ? Poll_Rate : "10"
	switch(poll) {
		case "1" :
			runEvery1Minute(refresh)
			log.debug("initialize: Poll Rate set to every 1 minute")
			break
		case "5" :
			runEvery5Minutes(refresh)
			log.debug("initialize: Poll Rate set to every 5 minutes")
			break
		case "10" :
			runEvery10Minutes(refresh)
			log.debug("initialize: Poll Rate set to every 10 minutes")
			break
		case "15" :
			runEvery15Minutes(refresh)
			log.debug("initialize: Poll Rate set to every 15 minutes")
			break
		case "30" :
			runEvery30Minutes(refresh)
			log.debug("initialize: Poll Rate set to every 30 minutes")
			break
		case "60" :
			runEvery1hour(refresh)
			log.debug("initialize: Poll Rate set to every 1 hour")
			break
		case "30" :
			runEvery3hours(refresh)
			log.debug("initialize: Poll Rate set to every 3 hours")
			break
	}
}

private getSensors(dataOnly = false) {
	def uri = "http://"+getHostAddress()+"/query/sensors"
	log.debug("getapi: Executing get api to " + uri)
	if (testMode) uri = "http://127.0.0.1/query/sensors"

	try {
		def requestParams =
		[
			uri:  uri,
			requestContentType: "application/x-www-form-urlencoded",
			contentType: "application/json"
		]
		httpGet(requestParams) { resp ->
			if (resp.status == 200) {
				if (logEnable) log.debug "getSensors: httpGet returned: \n${resp.data}"
				parseJsonData(resp.data)
			} else {
				log.error("getSensors: httpGet returned http failure code ${resp.status}")
			}
			if (logEnable) log.debug "getSensors: getSensors() completed"
		}
	} catch (Exception e) {
		if (testMode && (e.message == "Connect to 127.0.0.1:80 [/127.0.0.1] failed: Connection refused (Connection refused)")) {
			if (dataOnly) return testSensorDATA()
			return parseJsonData(testSensorDATA())
		}
		log.error "getSensors: Call to getSensors() failed: ${e.message}"
	}
}

def findChild(childDNI) {
	getChildDevices()?.find { it.deviceNetworkId == childDNI}
}

def createSensors(sensorData) {
	if (logEnable) log.debug("createSensors: Parsing sensor data: " + sensorData)
	def parentDNI = device.getDeviceNetworkId()
	if (sensorData.sensors) {
		sensorData.sensors.each { sensor ->
			def sensorName = sensor.name.replaceAll("\\s","").replaceAll("\\W","")
			def sensorDNI = "${device.deviceNetworkId}-ep${sensorName}"
			if (sensor.temp != null) {
				if (logEnable) log.debug("createSensors: Found temperature sensor device - " + sensor.name + "  DNI: "+sensorDNI)
				def childDevice = findChild(sensorDNI)
				if (childDevice == null) {
					if (logEnable) log.debug("createSensors: creating temperature Child "+sensorDNI)
					childDevice = addChildDevice("hubitat", "Virtual Temperature Sensor", sensorDNI, [label: "${device.displayName} (temp sensor - ${sensor.name})", isComponent: false])
				} else {
					if (logEnable) log.debug("createSensors: temperatureChild ${sensorDNI} already exists")
				}
				childDevice.setTemperature(sensor.temp)
			} else if (sensor.hum != null) {
				if (logEnable) log.debug("createSensors: Found humidity sensor device - " + sensor.name + "  DNI: "+sensorDNI)
				def childDevice = findChild(sensorDNI)
				if (childDevice == null) {
					if (logEnable) log.debug("createSensors: creating humidity Child "+sensorDNI)
					childDevice = addChildDevice("hubitat", "Virtual Humidity Sensor", "${device.deviceNetworkId}-ep${sensorName}", [label: "${device.displayName} (temp sensor - ${sensor.name})", isComponent: false])
				} else {
					if (logEnable) log.debug("createSensors: humidity Child ${sensorDNI} already exists")
				}
				childDevice.setHumidity(sensor.hum)
			} else {
				if (logEnable) log.debug("createSensors: Found unknown sensor device - " + sensor.name)
			}
		}
	}
}

def testDATA() { 
	def data = '{"name":"Test Data","mode":1,"state":0,"fan":0,"fanstate":1,"tempunits":1,"schedule":0,"schedulepart":255,"away":0,"spacetemp":21.0,"heattemp":21.0,"cooltemp":8.0,"cooltempmin":1.5,"cooltempmax":37.0,"heattempmin":1.5,"heattempmax":37.0,"setpointdelta":2.0,"availablemodes":1}'
	def slurper = new JsonSlurper()
	def result = slurper.parseText(data)
	result.mode = state.thermostatMode ?: 0
	result.state = state.thermostatMode ?: 0
	result.fan = state.thermostatFanMode ?: 0
	result.fanstate = (state.thermostatFanMode && (state.thermostatMode > 0)) ?: 0
	if (state.heattemp != null) result.heattemp = state.heattemp
	if (state.cooltemp!= null) result.cooltemp = state.cooltemp
	return result
}

def testAlertDATA() { 
	def data = '{"alerts": [{"name": "Air Filter","active": false},{"name": "UV Lamp","active": false},{"name": "Service","active": false}]}'
	def slurper = new JsonSlurper()
	def result = slurper.parseText(data)
	return result
}

def testSensorDATA() { 
	def data = '{"sensors": [{"name": "Thermostat","temp": 77},{"name": "Outdoor","temp": 0}]}'
	def slurper = new JsonSlurper()
	def result = slurper.parseText(data)
	return result
}
