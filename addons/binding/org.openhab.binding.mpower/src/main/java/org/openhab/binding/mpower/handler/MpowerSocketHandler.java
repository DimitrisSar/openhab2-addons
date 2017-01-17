package org.openhab.binding.mpower.handler;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.mpower.MpowerBindingConstants;
import org.openhab.binding.mpower.internal.MpowerSocketState;

/**
 * Handler for socket things. Forwards commands to bridge handler.
 *
 * @author magcode
 *
 */
public class MpowerSocketHandler extends BaseThingHandler {

    private MpowerSocketState currentState;
    private long lastUpdate;

    public MpowerSocketHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType) {
            Bridge mPower = this.getBridge();
            if (mPower != null && mPower.getHandler() instanceof MpowerHandler) {
                MpowerHandler handler = (MpowerHandler) mPower.getHandler();
                int sockNumber = Integer.parseInt(
                        thing.getConfiguration().get(MpowerBindingConstants.SOCKET_NUMBER_PROP_NAME).toString());

                OnOffType type = (OnOffType) command;
                handler.sendSwitchCommandToMPower(sockNumber, type);
            }
        }
    }

    public MpowerSocketState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(MpowerSocketState currentState) {
        this.currentState = currentState;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}