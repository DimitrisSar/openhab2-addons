# Ubiquiti Networks mPower binding

https://www.ubnt.com/mfi/mpower/

You do not need the mFi Controller software.

## Supported devices
Device | Status
------------ | -------------
P1E|untested
P3E|tested, works
P6E|tested, works

## Concept

The binding is implemented following the recommendation:

> You have one Bridge and an additional Thing for every actor and sensor and they would hold the channels

https://www.eclipse.org/smarthome/documentation/development/bindings/faq.html 

So each mPower strip is a "bridge" and each socket on the strip is a "thing".

Communication with the mPower strip is done via SSH.

Features:
* Switch outlets
* Good latency for switching operations
* Read power consumption values (see below)
* Automatic reconnect once a mPower connection got lost (check performed every 60 seconds)
* Switch state stays in sync with mPower: So if you switch a socket with the mPower App oder Webinterface it will be reflected in OpenHab
* Discovery of strips and sockets
* as least as possible channel updates by comparing new and old values and rounding

## Available channels
Each socket thing provides the following channels:

Channel | description
------------ | -------------
power|The current power draw in Watt
lastUpdated|the date holding the last update timestamp. Usually this is not needed.


## Thing configuration
Each mPower strip needs the following configuration parameters. They can be set either in paperUI after Autodiscovery or manually in a thing file. (see below)

Parameter | Description
------------ | -------------
user|tested, works
password|tested, works





## Let autodiscovery create things

As soon as you added an mPower strip as a thing, autodiscovery will prepare more things: one for each socket.


## Create things manually

```
Bridge mpower:mpower:24_A4_3C_D7_51_EB "mPower living room" [ host="192.168.1.26", password ="ubnt", username="ubnt" ] {
    socket 2 "mPower TV" [ socketNumber="2"]
    socket 3 "mPower Lights" [ socketNumber="3"]
}
``` 

# Extra features
## Power messurement enabling
The binding enables power measurement automatically on the strip.
## Read strip properties into bridge parameters
The binding exposes some bridge information, available in the binding parameters.
So far only "firware version" is implemented

