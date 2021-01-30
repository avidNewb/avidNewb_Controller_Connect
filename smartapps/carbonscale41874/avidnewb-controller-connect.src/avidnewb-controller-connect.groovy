/**
 *  avidNewb Controller (Connect)
 *
 *  Copyright 2020 avidNewb
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
String appVersion() { return "0.1" }

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field
definition(
    name: "avidNewb Controller (Connect)",
    namespace: "carbonscale41874",
    author: "avidNewb",
    description: "Allows you to integrate your avidNewb Controllers with SmartThings.",
    iconUrl: "https://github.com/avidNewb/resources/raw/main/logo_size_simple.png",
    iconX2Url: "https://github.com/avidNewb/resources/raw/main/logo_size_simple.png",
    iconX3Url: "https://github.com/avidNewb/resources/raw/main/logo_size_simple.png",
    singleInstance: true,
    pausable: false
)

preferences {
    page(name: "mainPage", nextPage: "", uninstall: true, install: true)
    page(name: "configureDevice")
    page(name: "deleteDeviceConfirm")
    page(name: "addDevice")
    page(name: "addDeviceConfirm")
}

def mainPage() {
    if (state?.install) {
        dynamicPage(name: "mainPage", title: "avidNewb Controller (Connect) - v${appVersion()}") {
            section(){
                href "addDevice", title:"New avidNewb Controller", description:""
            }
            section("Installed Devices"){
                getChildDevices().sort({ a, b -> a.label <=> b.label }).each {
                    String typeName = it.typeName
                    if (moduleMap().find{ it.value.type == "${typeName}" }?.value?.settings?.contains('ip')) {
                        String actualDni = it.deviceNetworkId
                        String descText = ""
                        // If the device network id doesn't match the Tasmota device MAC (w/o ":"), it will not work correctly.
                        // Manually modifying the DNI to "fix" this issue is likely not going to work. :)
                        // Make sure you have a physical SmartThings hub v2 or v3,
                        // avidNewb Controller and ST hub are on static IP and on the same subnetwork.
                        if (childSetting(it.id, "ip") != null) {
                            String dni = it.state?.dni
                            descText = childSetting(it.id, "ip")
                            if ((dni != null && dni != actualDni) || (dni == null)) {
                                descText += ' (!!!)'
                            }
                        } else {
                            descText = "Tap to set IP address"
                        }
                        href "configureDevice", title:"$it.label", description: descText, params: [did: actualDni]
                    } else {
                        href "configureDevice", title:"$it.label", description: "", params: [did: it.deviceNetworkId]
                    }
                }
            }
            section(title: "Settings") {
                input("dateformat", "enum",
                        title: "Date Format",
                        description: "Set preferred data format",
                        options: ["MM/dd/yyyy h:mm", "MM-dd-yyyy h:mm", "dd/MM/yyyy h:mm", "dd-MM-yyyy h:mm"],
                        defaultValue: "MM/dd/yyyy h:mm",
                        required: false, submitOnChange: false)
                input("frequency", "enum",
                        title: "Device Health Check",
                        description: "Check in on device health every so often",
                        options: ["Every 1 minute", "Every 5 minutes", "Every 10 minutes", "Every 15 minutes", "Every 30 minutes", "Every 1 hour"],
                        defaultValue: "Every 5 minutes",
                        required: false, submitOnChange: false)
            }
            section(title: "SmartThings Hub") {
                String st = getHub()?.localIP
                if (st == null || st == '') {
                    paragraph "Unable to find a SmartThings Hub. Do you have a physical SmartThings Hub (v2 or v3)?"
                } else {
                    paragraph "IP Address: ${st}"
                }
            }
            remove("Remove (Includes Devices)", "This will remove all devices.")
        }
    } else {
        dynamicPage(name: "mainPage", title: "avidNewb Controller (Connect)") {
            section {
                paragraph "Success!"
            }
        }
    }
}

def configureDevice(params){
    def t = params?.did
    if (params?.did) {
        atomicState?.curPageParams = params
    } else {
        t = atomicState?.curPageParams?.did
    }
    def d = getChildDevice(t)
    state.currentDeviceId = d?.deviceNetworkId
    state.currentId = d?.id
    state.currentDisplayName = d?.displayName
    state.currentTypeName = d?.typeName
    state.currentVersion = (d?.currentVersion) ? (' - ' + d.currentVersion) : ''

    def moduleParameter = moduleMap().find{ it.value.type == "${state.currentTypeName}" }?.value
    return dynamicPage(name: "configureDevice", install: false, uninstall: false, nextPage: "mainPage") {
        section("${state.currentDisplayName} ${state.currentVersion}") {}
        if (moduleParameter && moduleParameter.settings.contains('ip')) {
            section("Device setup") {
                input("dev:${state.currentId}:ip", "text",
                        title: "IP Address",
                        description: "IP Address",
                        defaultValue: "",
                        required: true, submitOnChange: true)
                input("dev:${state.currentId}:username", "text",
                        title: "Username",
                        description: "Username",
                        defaultValue: "",
                        required: false, submitOnChange: true)
                input("dev:${state.currentId}:password", "text",
                        title: "Password",
                        description: "Password",
                        defaultValue: "",
                        required: false, submitOnChange: true)
            }
        }

        // Whether to mark device as "always online"
        if (moduleParameter && moduleParameter.settings.contains('healthState')) {
            section("Health State") {
                input ("dev:${state.currentId}:health_state", "bool",
                        title: "Health State",
                        description: "Mark device as Always Online",
                        defaultValue: false, required: false, submitOnChange: true)
            }
        }

        // Potential Problem
        if (moduleParameter && moduleParameter.settings.contains('ip')) {
            def dc = getChildDevice(state.currentDeviceId)
            String dni = dc.state?.dni
            String actualDni = dc.deviceNetworkId
            if ((dni != null && dni != actualDni)) {
                section("Potential Problem Detected") {
                    paragraph "It appears that this device is either offline or there is another device using the same IP address or Device Network ID."
                }
            }
        }

        section("DANGER ZONE", hideable: true, hidden: true) {
            href "deleteDeviceConfirm", title:"DELETE $state.currentDisplayName", description: "Tap here to delete this device."
        }
    }

}

def deleteDeviceConfirm(){
    try {
        def d = getChildDevice(state.currentDeviceId)
        unsubscribe(d)
        deleteChildDevice(state.currentDeviceId, true)
        deleteChildSetting(d.id)
        dynamicPage(name: "deleteDeviceConfirm", title: "", nextPage: "mainPage") {
            section {
                paragraph "The device has been deleted."
            }
        }
    } catch (e) {
        dynamicPage(name: "deleteDeviceConfirm", title: "Deletion Summary", nextPage: "mainPage") {
            section {
                paragraph "Error: ${(e as String).split(":")[1]}."
            }
        }
    }
}

def addDevice(){
    Map deviceOptions = [:]
    moduleMap().sort({a, b -> a.value.name <=> b.value.name}).each { k,v ->
        deviceOptions[k] = v.name
    }
    dynamicPage(name: "addDevice", title: "", nextPage: "addDeviceConfirm") {
        section ("New avidNewb Controller") {
            input ("virtualDeviceType", "enum",
                title: "Which device do you want to add?",
                description: "", multiple: false, required: true, options: deviceOptions, submitOnChange: false
            )
            input ("deviceName", title: "Device Name", defaultValue: "avidNewb Controller", required: true, submitOnChange: false)
        }
    }
}

def addDeviceConfirm() {
    def latestDni = state.nextDni
    if (virtualDeviceType) {
        def selectedDevice = moduleMap().find{ it.key == virtualDeviceType }.value
        Map virtualParentData = [:]
        // Does this have child device(s)?
        def channel = selectedDevice?.channel
        if (channel != null && selectedDevice?.child != false) {
            if (channel > 1) {
                String parentChildName = selectedDevice.child[0]
                Map virtualParentChild = [:]
                for (i in 2..channel) {
                    parentChildName = (selectedDevice.child[i - 2]) ?: parentChildName
                    virtualParentChild[i] = parentChildName
                }
                virtualParentData["child"] = virtualParentChild.encodeAsJson()
            }
        }
        if (channel != null) {
            virtualParentData["endpoints"] = channel as String
        }
        try {
            def virtualParent = addChildDevice("carbonscale41874", selectedDevice?.type, "avidNewb-controller-${latestDni}", getHub()?.id, [
                    "completedSetup": true,
                    "label": deviceName,
                    "data": virtualParentData
            ])
            // Tracks all installed devices
            def deviceList = state?.deviceList ?: []
            deviceList.push(virtualParent.id as String)
            state?.deviceList = deviceList
            virtualParent.initialize()
            latestDni++
            state.nextDni = latestDni
            dynamicPage(name: "addDeviceConfirm", title: "Add a device", nextPage: "mainPage") {
                section {
                    paragraph "The controller has been added. Please proceed to configure device."
                }
            }
        } catch (e) {
            dynamicPage(name: "addDeviceConfirm", title: "Have you added all the device handlers?", nextPage: "mainPage") {
                section {
                    paragraph "Please follow these steps:", required: true
                    paragraph "1. Sign in to your SmartThings IDE.", required: true
                    paragraph "2. Under 'My Device Handlers' > click 'Settings' > 'Add new repository' > enter the following", required: true
                    paragraph "   Owner: avidNewb, Name: avidNewb-connect, Branch: master", required: true
                    paragraph "3. Under 'Update from Repo' > click 'avidNewb-connect' > Select all files > Tick 'Publish' > then 'Execute Update'", required: true
                    paragraph "Error message: ${(e as String).split(":")[1]}.", required: true
                }
            }
        }
    } else {
        dynamicPage(name: "addDeviceConfirm", title: "Add a device", nextPage: "mainPage") {
            section {
                paragraph "Please try again."
            }
        }
    }
}

def installed() {
    state?.nextDni = 1
    state?.deviceList = []
    state?.install = true
}

def uninstalled() {
    // Delete all child devices upon uninstall
    getAllChildDevices().each {
        deleteChildDevice(it.deviceNetworkId, true)
    }
}

def updated() {
    if (!state?.nextDni) { state?.nextDni = 1 }
    if (!state?.deviceList) { state?.deviceList = [] }
    //log.debug "Updated with settings: ${settings}"

    unsubscribe()

    // Subscription
    getChildDevices().eachWithIndex { cd, i ->
        // contactSensor
        log.debug cd.deviceNetworkId
        def cs = childSetting(cd.id, "contactSensor")
        if (cs != null) {
            subscribe(cs, "contact", contactHandler)
        }
        // crossDeviceMessaging
        def selectedDevice = moduleMap().find{ it.value.type == cd.typeName }?.value
        if (selectedDevice?.messaging == true) {
            subscribe(cd, "messenger", crossDeviceMessaging)
        }
        log.debug "Updated"
        cd.initialize()
    }

    // Clean up uninstalled devices
    def deviceList = state?.deviceList ?: []
    def newDeviceList = []
    deviceList.each { entry ->
        if (!getChildDevices().find { it.id == entry }) { deleteChildSetting(entry) }
        else { newDeviceList.push(entry as String) }
    }
    state?.deviceList = newDeviceList

    // Set new avidNewb Controller values to default
    settingUpdate("deviceName", "avidNewb Controller", "text")
    settingUpdate("virtualDeviceType", "", "enum")
}

def initialize() {
}

/**
 * Call avidNewb controller
 * @param device
 * @param path
 * @param query
 * @return
 */
def postToAvidNewb(childDevice, path, query) {
    // Virtual device sends bridge's ID, find the actual device's object
    if (childDevice instanceof String) {
        childDevice = getChildDevices().find { it.id == childDevice }?: null
    }
    // Real device sends its object
    if (childSetting(childDevice.device.id, "ip")) {
        updateDeviceNetworkId(childDevice)
        def digestAuth = calcDigestAuth("POST", path, (childSetting(childDevice.device.id, "username")), (childSetting(childDevice.device.id, "password")))
        def hubAction = new physicalgraph.device.HubAction(
            method: "POST",
            headers: [HOST: childSetting(childDevice.device.id, "ip") + ":80", Authorization: digestAuth],
        	path: path,
            query: query,
            null,
            [callback: "callBackHandler"]
        )
        log.debug "${hubAction}"
        childDevice.sendHubCommand(hubAction)
    } else {
        log.debug "Please add the IP address of ${childDevice.device.displayName}."
    }
    return
}

def getAvidNewb(childDevice, path) {
    // Virtual device sends bridge's ID, find the actual device's object
    if (childDevice instanceof String) {
        childDevice = getChildDevices().find { it.id == childDevice }?: null
    }
    // Real device sends its object
    if (childSetting(childDevice.device.id, "ip")) {
        updateDeviceNetworkId(childDevice)
        def digestAuth = calcDigestAuth("GET", path, (childSetting(childDevice.device.id, "username")), (childSetting(childDevice.device.id, "password")))
        def hubAction = new physicalgraph.device.HubAction(
            method: "GET",
            headers: [HOST: childSetting(childDevice.device.id, "ip") + ":80", Authorization: digestAuth],
        	path: path,
            null,
            [callback: "callBackHandler"]
        )
        log.debug "${hubAction}"
        childDevice.sendHubCommand(hubAction)
    } else {
        log.debug "Please add the IP address of ${childDevice.device.displayName}."
    }
    return
}

private hashMD5(String somethingToHash) {
	java.security.MessageDigest.getInstance("MD5").digest(somethingToHash.getBytes("UTF-8")).encodeHex()
}

private calcDigestAuth(String method, String uri, username, password) {
	def HA1 =  hashMD5("${username}::${password}")
	def HA2 = hashMD5("${method}:${uri}")
	def response = hashMD5("${HA1}::::auth:${HA2}")

	return 'Digest username="'+ username + '", realm="", nonce="", uri="'+ uri +'", qop=auth, nc=, cnonce="", response="' + response + '", opaque=""'
}

/**
 * Get the JSON value from the incoming
 * @param str
 * @return
 */
def getJson(str) {
    def parts = []
    def json = null
    if (str) {
        str.eachLine { line, lineNumber ->
            if (lineNumber == 0) {
                parts = line.split(" ")
                return
            }
        }
        if ((parts.length == 3) && parts[1].startsWith('/?json=')) {
            String rawCode = parts[1].split("json=")[1].trim().replace('%20', ' ')
            if ((rawCode.startsWith("{") && rawCode.endsWith("}")) || (rawCode.startsWith("[") && rawCode.endsWith("]"))) {
                json = new JsonSlurper().parseText(rawCode)
            }
        }
    }
    return json
}

/**
 * Get the JSON value from the incoming
 * @param str
 * @return
 */
def getJsonFromString(str) {
    json = new JsonSlurper().parseText(str)
    log.debug json
    return json
}

def setNetworkAddress(String mac) {
    mac.toUpperCase().replaceAll(':', '')
}

def updateDeviceNetworkId(childDevice) {
    def actualDeviceNetworkId = childDevice.device.deviceNetworkId
    if (childDevice.state.dni != null && childDevice.state.dni != "" && actualDeviceNetworkId != childDevice.state.dni) {
        log.debug "Updated '${childDevice.device.displayName}' dni to '${childDevice.state.dni}'"
        childDevice.device.deviceNetworkId = "${childDevice.state.dni}"
    }
}

def channelNumber(String dni) {
    if (dni.indexOf("-ep") >= 0) {
        dni.split("-ep")[-1] as Integer
    } else {
        ""
    }
}

/**
 * Return a list of installed child devices that match the input list
 * @param typeList
 * @return
 */
def childDevicesByType(List typeList) {
    List result = []
    if (typeList && typeList.size() > 0) {
        getChildDevices().each {
            if (it.typeName in typeList) {
                result << [(it.id): "${it.displayName}"]
            }
        }
    }
    return result
}

Map moduleMap() {
    Map customModule = [
        "1":    [name: "Salt and Sun Controller", type: "avidNewb Light Controller"]
    ]
    Map defaultModule = [
        "avidNewb Light Controller": [channel: 1, messaging: false, virtual: false, child: ["avidNewb Child Light Controller"], settings: ["ip"]]
    ]
    Map modules = [:]
    customModule.each { k,v ->
        modules[k] = defaultModule[v.type] + v
    }
    return modules
}

/**
 * Get SmartApp's general setting value
 * @param name
 * @return String | null
 */
def generalSetting(String name) {
    return (settings?."${name}") ?: null
}

/**
 * Health Check - online/offline
 * @return Integer
 */
Integer checkInterval() {
    Integer interval = ((generalSetting("frequency") ?: 'Every 1 minute').replace('Every ', '').replace(' minutes', '').replace(' minute', '').replace('1 hour', '60')) as Integer
    if (interval < 15) {
        return (interval * 2 * 60 + 1 * 60)
    } else {
        return (30 * 60 + 2 * 60)
    }
}

/**
 * Get child setting - this is stored in SmartApp
 * @param id String device ID
 * @param name String | List
 * @return
 */
def childSetting(String id, name) {
    def v = null
    if (name instanceof String)  {
        v = (settings?."dev:${id}:${name}")?: null
    } else if (name instanceof List) {
        v = [:]
        name.each() { entry ->
            v[entry] = (settings?."dev:${id}:${entry}")?.trim()?: null
        }
    }
    return (v instanceof String) ? v.trim() : v
}

/**
 * Delete child setting from SmartApp
 * @param id
 * @param name
 * @return
 */
def deleteChildSetting(id, name=null) {
    // If a name is given, delete the K/V
    if (id && name) {
        if (settings?.containsKey("dev:${id}:${name}" as String)) {
            app?.deleteSetting("dev:${id}:${name}" as String)
        }
    } else if (id && name==null) {
        // otherwise, delete everything
        ["ip", "username", "password", "command_on", "command_off", "track_state", "payload_on", "payload_off", "off_delay", "command_open", "command_close", "command_pause", "payload_open", "payload_close", "payload_pause", "payload_active", "payload_inactive", "health_state"].each { n ->
            app?.deleteSetting("dev:${id}:${n}" as String)
        }
        // button
        for(def n : 1..6) {
            app?.deleteSetting("dev:${id}:button_${n}" as String)
        }
    }
}

def settingUpdate(name, value, type=null) {
    if(name && type) { app?.updateSetting("$name", [type: "$type", value: value]) }
    else if (name && type == null) { app?.updateSetting(name.toString(), value) }
}

private getHub() {
    return location.getHubs().find{ it.getType().toString() == 'PHYSICAL' }
}
