/**
 *  avidNewb Light Controller
 *
 *  Copyright 2021 avidNewb
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

String driverVersion() { return "20210121" }
import groovy.json.JsonSlurper
metadata {
    definition(name: "avidNewb Light Controller", namespace: "carbonscale41874", author: "avidNewb", ocfDeviceType: "oic.d.light", vid: "e5f62cd5-6507-3367-9411-bb6a0859a01d", mnmn: "SmartThingsCommunity", mcdSync: true) {
        capability "Health Check"
        capability "Polling"
        capability "carbonscale41874.lightingMode"
        capability "Temperature Measurement"
        capability "Refresh"
        capability "Signal Strength"
        
        attribute "lastSeen", "string"
        attribute "version", "string"
    }

    simulator {
    }

    preferences {
        section {
            input(title: "Device Settings",
                    description: "To view/update this settings, go to the avidNewb Controller (Connect) SmartApp and select this device.",
                    displayDuringSetup: false,
                    type: "paragraph",
                    element: "paragraph")
            input(title: "", description: "avidNewb Light Controller v${driverVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph")
        }
    }
    
    
    tiles(scale: 2) {
        multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#00A0DC"
                attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff"
            }
        }

        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
        }
        standardTile("temperature", "device.temperature", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label: 'Temperature: ${currentValue}'
        }
        standardTile("lqi", "device.lqi", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label: 'LQI: ${currentValue}'
        }
        standardTile("rssi", "device.rssi", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label: 'RSSI: ${currentValue}dBm'
        }

        main "switch"
        details(["switch", "refresh", "lqi", "rssi"])
    }
}

private void createChildDevices() {
        // Save the device label for updates by updated()
        state.oldLabel = device.label
        // Add child devices for all five outlets of Zooz Power Strip
        log.debug "Creating child device"
        addChildDevice("avidNewb Child Temperature Sensor", "${device.deviceNetworkId}-cd", device.getHub().getId(),[completedSetup: true, label: "${device.displayName} Tank", isComponent: true, componentName: "tank", componentLabel: "Tank Temp"])
}

def installed() {
    sendEvent(name: "checkInterval", value: 30 * 60 + 2 * 60, displayed: false, data: [protocol: "lan", hubHardwareId: device.hub.hardwareID])
    sendEvent(name: "lightingMode", value: "SunSync")
    sendEvent(name: "temperature", value: 0, unit: "F", displayed: true)
    response(refresh())
}

def uninstalled() {
	//deleteChildDevice("${device.deviceNetworkId}-cd")
    sendEvent(name: "epEvent", value: "delete all", isStateChange: true, displayed: false, descriptionText: "Delete endpoint devices")
}

def updated() {
	log.debug "Updated"
    initialize()
}

def initialize() {
    log.debug "Initialize"
    if (device.hub == null) {
        log.error "Hub is null, must set the hub in the device settings so we can get local hub IP and port"
        return
    }
    
    //createChildDevices()

    def syncFrequency = (parent.generalSetting("frequency") ?: 'Every 1 minute').replace('Every ', 'Every').replace(' minute', 'Minute').replace(' hour', 'Hour')
    try {
        "run$syncFrequency"(refresh)
    } catch (all) { }
    
    sendEvent(name: "checkInterval", value: parent.checkInterval(), displayed: false, data: [protocol: "lan", hubHardwareId: device.hub.hardwareID])
    
    String childDni = "${device.deviceNetworkId}-1"
    def child = childDevices.find { it.deviceNetworkId == childDni }
    child?.sendEvent(name: "temperature", value: 0, unit: "F", displayed: false)
    child?.sendEvent(name: "checkInterval", value: parent.checkInterval(), displayed: false, data: [protocol: "lan", hubHardwareId: device.hub.hardwareID])
    
    def query = [ "hubaddress" : device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP") ]
    def path = "/api/set/smartthingshub"
    parent.postToAvidNewb(this, path, query)
    
    refresh()
}

def parse(String description) {
	log.debug "Starting response parsing"
    log.debug description
    def events = null
    def message = parseLanMessage(description)
    log.debug message
    def json = new JsonSlurper().parseText(message.body)
    log.debug json
    if (json != null) {
        events = parseEvents(200, json)
    }
    return events
}

def callBackHandler(physicalgraph.device.HubResponse hubResponse) {
	log.debug "Call back handler called"
    def events = null
    def status = hubResponse.status
    def json = hubResponse.json
    log.debug "Status: ${hubResponse.status}"
    log.debug "JSON: ${hubResponse.json}"
    events = parseEvents(status, json)
    return events
}

def parseEvents(status, json) {
    def events = []
    if (status as Integer == 200) {
    	log.debug "Parsing events"
        def channel = getDataValue("endpoints")?.toInteger()
        def eventdateformat = parent.generalSetting("dateformat")
        def now = location.timeZone ? new Date().format("${eventdateformat}a", location.timeZone) : new Date().format("yyyy MMM dd EEE h:mm:ss")
        
        if(json?.StatusSTS != null){
    	    log.debug json
	        if(json?.StatusSTS?.Lights != null) {
	    	    def modeString = json?.StatusSTS?.Lights
        
			    def lightingMode = ""
	    		switch(modeString) {
					case "LIGHTS ON":
    					lightingMode = "On"
			    		break
	  				case "LIGHTS OFF":
    					lightingMode = "Off"
    					break
		  			case "LIGHTS AUTO":
    					lightingMode = "Auto"
    					break
	  				case "LIGHTS SUNSYNC":
    					lightingMode = "SunSync"
	    				break
  					default:
    					break
	    		}
	        	log.debug "Lighting Mode: ${lightingMode}"
            
    		    events << sendEvent(name: "lightingMode", value: lightingMode)
    	    }
        
        
	        // MAC
        	if (json?.StatusSTS?.Wifi?.Mac != null) {
    	        def dni = parent.setNetworkAddress(json.StatusSTS.Wifi.Mac)
	            def actualDeviceNetworkId = device.deviceNetworkId
            	if (actualDeviceNetworkId != state.dni) {
        	        runIn(10, refresh)
    	        }
	            log.debug "MAC: '${json.StatusSTS.Wifi.Mac}', DNI: '${state.dni}'"
            	if (state.dni == null || state.dni == "" || dni != state.dni) {
        	        if (channel > 1 && childDevices) {
    	                childDevices.each {
	                        it.deviceNetworkId = "${dni}-" + parent.channelNumber(it.deviceNetworkId)
                        	log.debug "Child: " + "${dni}-" + parent.channelNumber(it.deviceNetworkId)
                    	}
                	}
            	}
            	state.dni = dni
        	}
        
    	    // Signal Strength
	        if (json?.StatusSTS?.Wifi != null) {
        	    events << sendEvent(name: "lqi", value: json?.StatusSTS?.Wifi.Signal, displayed: false)
    	        events << sendEvent(name: "rssi", value: json?.StatusSTS?.Wifi.RSSI, displayed: false)
	        }
            if (json?.StatusSTS?.LightTemp != null) {
        	    events << sendEvent(name: "temperature", value: json?.StatusSTS?.LightTemp, unit: json?.StatusSTS?.LightTempUnit, displayed: false)
	        }           

        }
        // Call back
        if (json?.cb != null) {
            //parent.callTasmota(this, json.cb)
        }

        // Last seen
        events << sendEvent(name: "lastSeen", value: now, displayed: false)
    }
    return events
}

def setLightingMode(lightingMode) {
	log.debug "Mode ${lightingMode}"
    def modeString = ""
    switch(lightingMode) {
		case "On":
    		modeString = "on"
    		break
  		case "Off":
    		modeString = "off"
    		break
  		case "Auto":
    		modeString = "auto"
    		break
  		case "SunSync":
    		modeString = "sunsync"
    		break
  		default:
    		break
    }
    def query = [ "lights" : "${modeString}" ]
    def path = "/api/set/device"
    parent.postToAvidNewb(this, path, query)
    
    log.debug "Setting device to ${modeString}"
}


def refresh(dni=null) {
	log.debug "Refreshed"
    def lastRefreshed = state.lastRefreshed
    if (lastRefreshed && (now() - lastRefreshed < 5000)) return
    state.lastRefreshed = now()

    // Check version every 30m
   // def lastCheckedVersion = state.lastCheckedVersion
   // if (!lastCheckedVersion || (lastCheckedVersion && (now() - lastCheckedVersion > (30 * 60 * 1000)))) {
        //parent.setDevice(this, "Status 2")
   // }

    def actualDeviceNetworkId = device.deviceNetworkId
   // if (state.dni == null || state.dni == "" || actualDeviceNetworkId != state.dni) {
        //parent.setDevice(this, "Status 5")
   // }
   
    def query = [ "lights" : "${modeString}" ]
    def path = "/api/status8"
    parent.getAvidNewb(this, path)
    //refreshChild()
}

def refreshChild(){
    def child = childDevices.find { it.deviceNetworkId == "${device.deviceNetworkId}-1" }
    log.debug "Child refreshed"
    child?.sendEvent(name: "temperature", value: 0, unit: "F", displayed: false)
}

def ping() {
    refresh()
}

def poll() {
    def eventdateformat = parent.generalSetting("dateformat")
    def now = location.timeZone ? new Date().format("${eventdateformat}a", location.timeZone) : new Date().format("yyyy MMM dd EEE h:mm:ss")
    sendEvent(name: "lastSeen", value: now, displayed: false)
}