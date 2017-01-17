package org.openhab.binding.mpower.internal.connector;

import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.openhab.binding.mpower.handler.MpowerHandler;
import org.openhab.binding.mpower.internal.MpowerSocketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Core SSH connector for mPower devices.
 * Can read data and perform commands (such as switching outlets)
 *
 * @author magcode
 *
 */
public class MpowerSSHConnector {
    private String host;
    private long refreshInterval = 10000;
    private String user;
    private String password;

    private Session session;
    private PollingAgent agent;
    private int ports = 0;
    private boolean isConnecting = false;
    private static final Logger logger = LoggerFactory.getLogger(MpowerSSHConnector.class);
    private MpowerHandler mPowerHandler;

    public MpowerSSHConnector(String host, String user, String password, long refreshInterval, MpowerHandler handler) {

        this.host = host;
        this.user = user;
        this.password = password;
        if (refreshInterval > 0) {
            this.refreshInterval = refreshInterval;
        }
        this.mPowerHandler = handler;
    }

    /**
     * Starts the connector
     */
    public void start() {
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        JSch jsch = new JSch();
        Session aSession;
        try {
            this.isConnecting = true;
            aSession = jsch.getSession(user, host, 22);
            aSession.setPassword(password);
            aSession.setConfig(config);
            aSession.setTimeout(3000);
            aSession.setServerAliveInterval(1000 * 60);
            aSession.setServerAliveCountMax(10);
            aSession.connect(2000);
            this.session = aSession;
            getNumberOfSockets();
            enableEnergyMeasurement();
            updateBridgeStatus();
            this.agent = new PollingAgent(this);
            agent.start();
            logger.info("connected to mPower on host {}", this.host);
        } catch (JSchException e) {
            logger.error("Could not connect.", e);
        } finally {
            this.isConnecting = false;
        }
    }

    private void enableEnergyMeasurement() {
        try {
            SSHExecutor exec = new SSHExecutor(this.session);
            StringBuilder builder = new StringBuilder();

            for (int i = 1; i < this.ports + 1; i++) {
                builder.append("echo 1 > /proc/power/enabled").append(i).append(";");
            }
            String command = builder.toString();
            exec.execute(command);
        } catch (JSchException e) {
            logger.error("Failed to enable enery measurement");
        }
    }

    private void updateBridgeStatus() {
        try {
            SSHExecutor exec = new SSHExecutor(this.session);
            String command = "cat /etc/version";
            String result = exec.execute(command);
            this.mPowerHandler.receivedBridgeStatusUpdateFromConnector(result);
        } catch (JSchException e) {
            logger.error("Failed to enable enery measurement");
        }

    }

    public boolean isRunning() {
        return this.isConnecting || (this.session != null && this.session.isConnected());
    }

    public void stop() {
        if (this.agent != null) {
            this.agent.interrupt();
        }
        if (isRunning()) {
            this.session.disconnect();
            logger.info("Session closed to {}", this.host);
        }
    }

    protected void message(String message) {
        if (StringUtils.isNotBlank(message)) {
            String[] parts = message.split("\n");
            for (int i = 1; i < this.ports + 1; i++) {
                MpowerSocketState state = new MpowerSocketState(parts[4 * (i - 1)], parts[4 * (i - 1) + 1],
                        parts[4 * (i - 1) + 2], parts[4 * (i - 1) + 3], i);
                this.mPowerHandler.receivedUpdateFromConnector(state);
            }
        }
    }

    /**
     * Polling agents gets it's session from here At the same time it serves as
     * a watchdog If the session is broken, it will be renewed. That 'retry'
     * works endless each minute.
     *
     * @return
     */
    protected Session getSession() {
        if (isRunning()) {
            return session;
        }
        return null;
    }

    /**
     * Sends relay switch commands
     *
     * @param socket
     * @param onOff
     */
    public void send(int socket, OnOffType onOff) {
        try {
            SSHExecutor exec = new SSHExecutor(this.session);
            String onOffString = onOff == OnOffType.ON ? "1" : "0";
            String command = "echo " + onOffString + " > /proc/power/relay" + socket;
            exec.execute(command);
            exec = null;
        } catch (JSchException e) {
            logger.error("Failed to switch");
        }
    }

    /**
     * looks for number of ports directly on the mPower
     */
    private void getNumberOfSockets() {
        try {
            SSHExecutor exec = new SSHExecutor(this.session);
            String command = "cat /etc/board.inc | grep feature_power";
            String result = exec.execute(command);
            result = StringUtils.substringAfterLast(result, "=");
            result = StringUtils.substringBeforeLast(result, ";");
            if (StringUtils.isNotBlank(result) && StringUtils.isNumeric(result)) {
                logger.debug("This is a {} port mPower", result);
                this.ports = Integer.parseInt(result);
            }
        } catch (JSchException e) {
            logger.error("Failed to read number of ports");
        }
    }

    /**
     * Returns the number of ports as queried from the mPower
     *
     * @return
     */
    protected int getPorts() {
        return ports;
    }

    public long getRefreshInterval() {
        return refreshInterval;
    }

    public String getId() {
        return this.mPowerHandler.getThing().getUID().getAsString();
    }
}