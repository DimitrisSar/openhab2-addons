# Ubiquiti Networks mPower binding

https://www.ubnt.com/mfi/mpower/

You do not need the mFi Controller software.

## Supported devices
Device | Status
------------ | -------------
P1E|untested
P3E|tested, works
P6E|tested, works
P1U|untested
P3U|untested
P8U|untested

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
voltage|Current voltage
outlet|The switch
energy|Accumulated energy consumption
lastUpdated|the date holding the last update timestamp. Usually this is not needed.


## Thing configuration
Each mPower strip needs the following configuration parameters. They can be set either in paperUI after Autodiscovery or manually in a thing file. (see below)

Parameter | Description
------------ | -------------
user|mPower username (default set to `ubnt`)
password|mPower password (default set to `ubnt`)
host|IP address or hostname of the mPower. Autodiscovery will set this value for you. 
refresh|in ms





## Let autodiscovery create the bridge
Autodiscovery scans your network and will add new mPower devices to your inbox. Please note: this won't happen if you added the device manually in a *.thing file.

## Let autodiscovery create the sockets
As soon as you added an mPower strip as a thing, autodiscovery will prepare more things: one for each socket. You can find it in your inbox.


## Create things manually
You can also add mPower and socket things manually. Please note the syntax for the sockets:
```
socket <unique id, simply use the socket number here> "<label>" [ socketNumber="<the socket number on the mPower>"] 
```

The following code provides an example. 

```
Bridge mpower:mpower:24_A4_3C_D7_51_EB "mPower living room" [ host="192.168.1.26", password ="ubnt", username="ubnt" ] {
    socket 2 "mPower TV" [ socketNumber="2"]
    socket 3 "mPower Lights" [ socketNumber="3"]
}
``` 
Make sure that the UID is correct. Otherwise autodiscovery will add the same mPower bridge again to your inbox. To find out the right UID you have 2 options:

* Use your OS and find out the serial number of your mPower
* Use auto discovery (without confirming the item in the inbox) and copy the UID from there


# Extra features
## Power messurement enabling
The binding enables power measurement automatically on the strip.

## Energy consumption statistics
By default the channel `energy` cumulates watt-hours (Wh).
I you like to have a per-day statistic available in OpenHab I suggest the following approach:

Make sure that the "cf_count" values are reset each night. You can do this by connecting to your mPower device via SSH and adding the following lines to /tmp/system.cfg
```
cron.1.job.1.cmd=echo 1 > /proc/power/clear_ae4;echo 1 > /proc/power/clear_ae5;echo 1 > /proc/power/clear_ae6;echo 1 > /proc/power/clear_ae1;echo 1 > /proc/power/clear_ae2;echo 1 > /proc/power/clear_ae3;
cron.1.job.1.schedule=59 23 * * *
cron.1.job.1.status=enabled
cron.1.status=enabled
cron.1.user=ubnt
cron.status=enabled
```

Now you can persist the `enery` each day a minute before midnight.

## Read strip properties into bridge parameters
The binding exposes some bridge information, available in the binding parameters.
So far only "firware version" is implemented. Feel free to suggest more.

