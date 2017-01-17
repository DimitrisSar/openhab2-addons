/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.mpower.handler;

import java.util.Hashtable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.mpower.MpowerBindingConstants;
import org.openhab.binding.mpower.config.MpowerDeviceConfig;
import org.openhab.binding.mpower.internal.MpowerSocketState;
import org.openhab.binding.mpower.internal.connector.MpowerSSHConnector;
import org.openhab.binding.mpower.internal.discovery.MpowerSocketDiscovery;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MpowerHandler}
 *
 * @author magcode - Initial contribution
 */
public class MpowerHandler extends BaseBridgeHandler {
    private ServiceRegistration<?> discoveryServiceRegistration;
    private MpowerSocketDiscovery discoveryService;
    private MpowerSSHConnector connector;
    private long refresh = 10000;
    private ScheduledFuture<?> watchDogJob;

    public MpowerHandler(Bridge bridge) {
        super(bridge);
    }

    private Logger logger = LoggerFactory.getLogger(MpowerHandler.class);

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // no commands yet. Maybe a "mPower reset" feature?
    }

    public void sendSwitchCommandToMPower(int socket, OnOffType onOff) {
        if (this.connector.isRunning()) {
            this.connector.send(socket, onOff);
        }
    }

    @Override
    public void initialize() {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (connector.isRunning()) {
                    logger.trace(connector.getId() + " is running!");
                } else {
                    logger.info(connector.getId() + " is not running! Trying to restart.");
                    connector.start();
                }
            }
        };
        watchDogJob = scheduler.scheduleAtFixedRate(runnable, 0, 60, TimeUnit.SECONDS);

        updateStatus(ThingStatus.UNKNOWN);
        // an extra discovery service immediately adds the required socket things
        registerDeviceDiscoveryService();
        String model = getThing().getProperties().get(MpowerBindingConstants.DEVICE_MODEL_PROP_NAME);
        String noOfSocketsString = model.substring(1, 2);
        int noOfSockets = Integer.parseInt(noOfSocketsString);
        for (int i = 1; i <= noOfSockets; i++) {
            discoveryService.onDeviceAddedInternal(getThing().getUID(), getThing().getLabel(), i);
        }

        // validate config
        MpowerDeviceConfig config = this.getConfigAs(MpowerDeviceConfig.class);
        this.refresh = config.getRefresh();
        if (StringUtils.isBlank(config.getUsername()) || StringUtils.isBlank(config.getPassword())) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Please set username and password.");
            return;
        }

        // start the connector
        connector = new MpowerSSHConnector(config.getHost(), config.getUsername(), config.getPassword(),
                config.getRefresh(), this);
        connector.start();
        if (connector.isRunning()) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    /**
     * Core feature: update channels with data from mpower
     *
     * finds all things (sockets) and updates it from the core mpower data
     * TODO: update only of required (use a cache)
     *
     * @param state
     */
    public void receivedUpdateFromConnector(MpowerSocketState mpowerSocketState) {
        // maybe change status here? Its back online once there comes data.
        for (Thing thing : getThing().getThings()) {
            int sockNumber = Integer
                    .parseInt(thing.getProperties().get(MpowerBindingConstants.SOCKET_NUMBER_PROP_NAME));
            if (mpowerSocketState.getSocket() == sockNumber) {
                MpowerSocketHandler handler = (MpowerSocketHandler) thing.getHandler();
                MpowerSocketState oldState = handler.getCurrentState();
                boolean needsUpdate = System.currentTimeMillis() > handler.getLastUpdate() + this.refresh;
                if (needsUpdate && (oldState == null || !oldState.equals(mpowerSocketState))) {
                    DecimalType powerState = new DecimalType(mpowerSocketState.getPower());
                    updateState(thing.getChannel(MpowerBindingConstants.CHANNEL_POWER).getUID(), powerState);
                    DecimalType volatageState = new DecimalType(mpowerSocketState.getVoltage());
                    updateState(thing.getChannel(MpowerBindingConstants.CHANNEL_VOLTAGE).getUID(), volatageState);
                    DecimalType energyState = new DecimalType(mpowerSocketState.getEnergy());
                    updateState(thing.getChannel(MpowerBindingConstants.CHANNEL_ENERGY).getUID(), energyState);
                    OnOffType outletState = mpowerSocketState.isOn() ? OnOffType.ON : OnOffType.OFF;
                    updateState(thing.getChannel(MpowerBindingConstants.CHANNEL_OUTLET).getUID(), outletState);
                    handler.setLastUpdate(System.currentTimeMillis());
                }

            }
        }
    }

    /**
     * Updates some properties of the mPower thing. Such as firmware version.
     *
     * @param firmware
     */
    public void receivedBridgeStatusUpdateFromConnector(String firmware) {
        this.getThing().setProperty(MpowerBindingConstants.FIRMWARE_PROP_NAME, firmware);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        watchDogJob.cancel(true);
        logger.debug("Disposing mPower bridge '{}'", getThing().getUID().getId());
        if (connector.isRunning()) {
            connector.stop();
        }
        if (discoveryService != null) {
            unregisterDeviceDiscoveryService();
        }
        super.dispose();
    }

    /**
     * Registers the DeviceDiscoveryService.
     */
    private void registerDeviceDiscoveryService() {
        if (bundleContext != null) {
            logger.trace("Registering mPower Socket discovery for mPower '{}'", getThing().getUID().getId());
            discoveryService = new MpowerSocketDiscovery();
            discoveryServiceRegistration = bundleContext.registerService(DiscoveryService.class.getName(),
                    discoveryService, new Hashtable<String, Object>());
            discoveryService.activate();
        }
    }

    /**
     * Unregisters the DeviceDisoveryService
     */
    private void unregisterDeviceDiscoveryService() {
        if (discoveryServiceRegistration != null && bundleContext != null) {
            MpowerSocketDiscovery service = (MpowerSocketDiscovery) bundleContext
                    .getService(discoveryServiceRegistration.getReference());
            service.deactivate();

            discoveryServiceRegistration.unregister();
            discoveryServiceRegistration = null;
            discoveryService = null;
        }
    }
}
