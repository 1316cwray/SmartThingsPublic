/**
*  OSRAM Lightify Dimming Switch
*
*  Copyright 2016 Michael Hudson
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
*  Thanks to @Sticks18 for the Hue Dimmer remote code used as a base for this!
*
*/

metadata {
  definition (name: "OSRAM Lightify Dimming Switch", namespace: "motley74", author: "Michael Hudson") {
    capability "Actuator"
    capability "Battery"
    capability "Button"
    capability "Configuration"
    capability "Refresh"
    capability "Sensor"
       
    attribute "zMessage", "String"
       
    fingerprint profileId: "0104", deviceId: "0001", inClusters: "0000, 0001, 0003, 0020, 0402, 0B05", outClusters: "0003, 0006, 0008, 0019", /*manufacturer: "OSRAM", model: "Lightify 2.4GHZZB/SWITCH/LFY", */deviceJoinName: "OSRAM Lightify Dimming Switch"
  }

  // simulator metadata
  simulator {
    // status messages
 
  }

  // UI tile definitions
  tiles(scale: 2) {
    standardTile("button", "device.button", width: 6, height: 4) {
      state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
    }
    valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
      state "battery", label:'${currentValue}% battery'
    }
    standardTile("refresh", "device.button", decoration: "flat", width: 2, height: 2) {
      state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    main "button"
    details(["button", "battery", "refresh"])
  }
}

// Parse incoming device messages to generate events
def parse(String description) {
  Map map = [:]
  log.debug "parse description: $description"
  if (description?.startsWith('catchall:')) {
    // call parseCatchAllMessage to parse the catchall message received
    map = parseCatchAllMessage(description)
  } else if (description?.startsWith('read')) {
    // call parseReadMessage to parse the read message received
    map = parseReadMessage(description)
  } else {
    log.debug "Unknown message received: $description"
  }
  //return event unless map is not set
  return map ? createEvent(map) : null
}

def configure() {
  log.debug "Confuguring Reporting and Bindings."
  def configCmds = [
    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}",

    // Bind the outgoing level cluster from remote to hub, so the hub receives messages when Dim Up/Down buttons pushed
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}",
    
    // Bind the incoming battery info cluster from remote to hub, so the hub receives battery updates
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}",
  ]
  return configCmds 
}

def refresh() {
  def refreshCmds = [
    zigbee.readAttribute(0x0001, 0x0020)
  ]
  //when refresh button is pushed, read updated status
  return refreshCmds
}

private Map parseReadMessage(String description) {
  // Create a map from the message description to make parsing more intuitive
  def msg = zigbee.parseDescriptionAsMap(description)
  //def msg = zigbee.parse(description)
  if (msg.clusterInt==1 && msg.attrInt==32) {
    // call getBatteryResult method to parse battery message into event map
    def result = getBatteryResult(Integer.parseInt(msg.value, 16))
  } else {
    log.debug "Unknown read message received, parsed message: $msg"
  }
  // return map used to create event
  return result
}

private Map parseCatchAllMessage(String description) {
  // Create a map from the raw zigbee message to make parsing more intuitive
  def msg = zigbee.parse(description)
  log.debug msg
  switch(msg.clusterId) {
    case 1:
      // call getBatteryResult method to parse battery message into event map
      log.debug 'BATTERY MESSAGE'
      def result = getBatteryResult(Integer.parseInt(msg.value, 16))
      break
    case 6:
      def button = (msg.command == 1 ? 1 : 2)
      Map result = [:]
      result = [
        name: 'button',
        value: 'pushed',
        data: [buttonNumber: button],
        descriptionText: "$device.displayName button $button was pushed",
        isStateChange: true
      ]
      log.debug "Parse returned ${result?.descriptionText}"
      return result
      break
    case 8:
      switch(msg.command) {
        case 1: // brightness decrease command
          Map result = [:]
          result = [
            name: 'button',
            value: 'held',
            data: [buttonNumber: 2],
            descriptionText: "$device.displayName button 2 was held",
            isStateChange: true
          ]
          log.debug "Parse returned ${result?.descriptionText}"
          return result
          break
        case 3: /* brightness change stop command
          def result = [
            name: 'button',
            value: 'released',
            data: [buttonNumber: [1,2]],
            descriptionText: "$device.displayName button was released",
            isStateChange: true
          ]*/
          log.debug "Recieved stop command, not currently implemented!"
          //return result
          break
        case 5: // brightness increase command
          Map result = [:]
          result = [
            name: 'button',
            value: 'held',
            data: [buttonNumber: 1],
            descriptionText: "$device.displayName button 1 was held",
            isStateChange: true
          ]
          log.debug "Parse returned ${result?.descriptionText}"
          return result
          break
      }
  }
}

//obtained from other examples, converts battery message into event map
private Map getBatteryResult(rawValue) {
  def linkText = getLinkText(device)
  def result = [
    name: 'battery',
    value: '--'
  ]
  def volts = rawValue / 10
  def descriptionText
  if (rawValue == 0) {
  } else {
    if (volts > 3.5) {
      result.descriptionText = "${linkText} battery has too much power (${volts} volts)."
    } else if (volts > 0){
      def minVolts = 2.1
      def maxVolts = 3.0
      def pct = (volts - minVolts) / (maxVolts - minVolts)
      result.value = Math.min(100, (int) pct * 100)
      result.descriptionText = "${linkText} battery was ${result.value}%"
    }
  }
  log.debug "Parse returned ${result?.descriptionText}"
  return result
}