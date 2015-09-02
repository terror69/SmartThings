/**
 *  Severe Weather Alert
 *
 *  Author: SmartThings
 *  Modified: justinlhudson
 */

// getWeatherFeature: http://www.wunderground.com/weather/api/d/docs?

definition(
    name: "Severe Weather Alert*",
    namespace: "smartthings",
    author: "SmartThings",
    description: "Get notifications when severe weather is in your area.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-SevereWeather.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-SevereWeather@2x.png"
)

preferences {
  section ("Text alerts to...") {
    input("recipients", "contact", title: "Send notifications to") {
        input "phone1", "phone", title: "Phone Number 1", required: false
        input "phone2", "phone", title: "Phone Number 2", required: false
        input "phone3", "phone", title: "Phone Number 3", required: false
    }
  }
  section ("Activate alarms...") {
    input "alarms", "capability.alarm", title:"Alarms", multiple:true, required:false
    input "clear", "number", title:"Active (seconds)", defaultValue:0
  }
  section ("Options...") {
    input "zipcode", "text", title: "Zip Code", required: false
    input "filters", "text", title: "Filter Alerts (x,x,...)", required: false
  }
}

def installed() {
  log.debug "Installed with settings: ${settings}"
  init()
}

def updated() {
  log.debug "Updated with settings: ${settings}"
  log.debug alertFilter()
  unsubscribe()
  unschedule()
  init()
  // just incase update during being active (rare case)
  if( state.checking == true && settings.clear && settings.clear > 0 ) {
    clear()
  }
}

def init() {
  checkForSevereWeather()
  runEvery5Minutes("checkForSevereWeather")

  // HACK: keep alive
  subscribe(location, "mode", modeHandler)
}

def alarms_both() {
    log.debug "alarms_both"
    settings.alarms?.both()
    pause(1000)
    settings.alarms.each {
    while ( it != null && it.latestValue("alarm") != "both") {
      it.both()
        pause(1000)
      }
    }
}

def alarms_strobe() {
    log.debug "alarms_strobe"
    settings.alarms?.strobe()
    pause(1000)
    settings.alarms.each {
    while ( it != null && it.latestValue("alarm") != "strobe") {
        it.strobe()
        pause(1000)
      }
    }
}

def alarms_off() {
    log.debug "alarms_off"
    settings.alarms?.off()
    pause(1000)
    settings.alarms.each {
    while ( it != null && it.latestValue("alarm") != "off") {
      it.off()
        pause(1000)
      }
    }
}

def getRandom(int min, int max) {
  return Math.abs(new Random().nextInt() % max + min)
}

def modeHandler(evt)
{
  updated()
}

def clear() {
    alarms_off()
}

def checkForSevereWeather() {
  state.checking = true
  def alerts
  if(locationIsDefined()) {
    if(zipcodeIsValid()) {
      alerts = getWeatherFeature("alerts", zipcode)?.alerts
    } else {
      log.warn "Severe Weather Alert: Invalid zipcode entered, defaulting to location's zipcode"
      alerts = getWeatherFeature("alerts")?.alerts
    }
  } else {
    log.warn "Severe Weather Alert: Location is not defined"
  }

  //alerts = [ [type: "WRN"], [type: "TRN"] ]  //test

  def newAlerts = alerts?.collect{it.type} ?: []
  log.debug "newAlerts: $newAlerts"

  def oldAlerts = state.lastAlerts ?: []
  log.debug "oldAlerts: $oldAlerts"

  if (newAlerts != oldAlerts) {
    state.lastAlerts = newAlerts

    def alertsFound = []  // changes from last iteration

    // if a new alert is added to list
    newAlerts.each { newAlert ->
      if(!oldAlerts.contains(newAlert)) {
        log.debug("alert")
        alertsFound.add(newAlert)
      }
    }

    // if more then one alert at same time (rare...)
    alertsFound.each { alertFound ->
      def alert = alerts.find { it.type == alertFound }
        if(alert && alertFilter(alert.type)) {
          def msg = "Weather Alert! ${alert.type} from ${alert.date} until ${alert.expires}"

          alarms_both()

          if (settings.clear && settings.clear > 0 ) {
            runIn(settings.clear, clear, [overwrite: true])
        }

        send(msg)
      }
    }
  }
  state.checking = false
}

def alertFilter(String type) {
  def filterList = ["PUB", "REP", "REC", "SPE", "HEA"]
  if(settings.filters) {
   filterList.addAll(settings.filters.split(','))
  }

  log.debug "filters: " + filterList.toListString()

  def passesFilter = true
  if(type) {
    filterList.each() { word ->
      if(type.toLowerCase().contains(word.toLowerCase())) { passesFilter = false }
    }
  }
  passesFilter
}

def locationIsDefined() {
  zipcodeIsValid() || location.zipCode || ( location.latitude && location.longitude )
}

def zipcodeIsValid() {
  zipcode && zipcode.isNumber() && zipcode.size() == 5
}

private send(message) {
    if (message)
    {
      log.debug(message)

      if (location.contactBookEnabled) {
        log.debug("sending notifications to: ${recipients?.size()}")
        sendNotificationToContacts(msg, recipients)
      }
      else
      {
        if (settings.phone1) {
          sendSms phone1, message
        }
        if (settings.phone2) {
          sendSms phone2, message
        }
        if (settings.phone3) {
          sendSms phone3, message
        }
        sendPush message
      }
    }
}