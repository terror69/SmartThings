definition(
    name: "AlarmResetter",
    namespace: "Operations",
    author: "justinlhudson",
    description: "Resets Alarming (incase someone comes invited without unlocking)",
    category:  "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
    section("Alarming...") {
        input "alarms", "capability.alarm", title:"Reset alarms", multiple:true, required:true
        input "strobe", "bool", title:"Strobe?", description: "Stobe still?", defaultValue: true
//        input "siren", "bool", title:"Siren?", description: "Stobe still?", defaultValue: true
        input "delay", "number", title:"Active (seconds)", defaultValue:60
        input "reset", "number", title:"Reset (minutes)", defaultValue:5
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()

    initialize()
}

def clear() {
    settings.alarms*.off()

    state.alarmActive = false

    sendNotificationEvent "Alarm(s) Reset..."
}

def set() {
	if (state.alarmValue != "strobe") {
    	clear()
    }
    else if(settings.strobe == true) {
        settings.alarms*.strobe()
        sendNotificationEvent "Alarm(s) Silented!"
        runIn(settings.reset*60, clear, [overwrite: true])
    }
}

def alarmHandler(evt)
{
    log.debug "${evt.value}"
/*
    if(settings.siren == true && settings.strobe == true){
        settings.alarms*.both()
    }
    else if(settings.siren == true) {
        settings.alarms*.siren()
    }
    else if(settings.strobe == true){
        settings.alarms*.strobe()
    }
*/
    if( evt.value != "off" && state.alarmActive == false) {
    	state.alarmValue = evt.value
        state.alarmActive = true

        sendNotificationEvent "Alarm(s) Active!"
        runIn(settings.delay, set, [overwrite: false])
    }
}

private def initialize() {
      state.alarmActive = false
      subscribe(alarms, "alarm", alarmHandler)
}
