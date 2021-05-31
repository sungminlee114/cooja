/*
 * Copyright (c) 2009, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;

import org.apache.log4j.Logger;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.analyzers.PcapExporter;
import org.contikios.cooja.util.StringUtils;
import org.jdom.Element;

import org.contikios.cooja.MoteType.MoteTypeCreationException;
import org.contikios.cooja.interfaces.Log;
import org.contikios.cooja.util.ArrayUtils;

/**
 * Simulation event central. Simplifies implementations of plugins that observe
 * motes and mote interfaces by keeping track of added and removed motes. For a
 * selected set of interfaces, the event central also maintains an event
 * history.
 * 
 * @see LogOutputEvent
 * @author Fredrik Osterlind
 */
public class SimEventCentral {
  private static final Logger logger = Logger.getLogger(SimEventCentral.class);

  private final Simulation simulation;
  private final File configPath;
  private final String configName;
  private boolean isDataTraceEnabled = false;
  private boolean isObserving = false;
  private File dataTracePath = null;
  private RadioMedium radioMedium = null;
  private PcapExporter pcapExporter = null;
  private PrintWriter radioMediumOutput = null;
  private PrintWriter moteLogOutput = null;
  private PrintWriter eventOutput = null;

  public SimEventCentral(Simulation simulation) {
    this.simulation = simulation;

    File configFile = simulation.getCooja().currentConfigFile;
    File path = configFile != null ? configFile.getParentFile() : null;
    if (path == null) {
      /* Store in current directory */
      path = new File("");
    }
    this.configPath = path;
    String name = configFile != null ? configFile.getName() : null;
    if (name != null) {
      if (name.endsWith(".csc")) {
        name = name.substring(0, name.length() - 4);
      } else if (name.endsWith(".csc.gz")) {
        name = name.substring(0, name.length() - 7);
      }
    }
    this.configName = name;

    /* Default buffer sizes */
    logOutputBufferSize = Integer.parseInt(Cooja.getExternalToolsSetting("BUFFERSIZE_LOGOUTPUT", "" + 40000));


    moteObservations = new ArrayList<MoteObservation>();

    /* Mote count: notifications */
    moteCountListeners = new MoteCountListener[0];

    /* Log output: notifications and history */
    logOutputListeners = new LogOutputListener[0];
    logOutputEvents = new ArrayDeque<LogOutputEvent>();
  }
  
  public synchronized File getSimulationLogFile(String name, String suffix) {
    if (!this.isDataTraceEnabled) {
      return null;
    }
    if (this.configName == null) {
      logger.warn("no simulation name available for data trace!");
      return null;
    }
    if (this.dataTracePath == null) {
      String traceName = this.simulation.getCooja().getNextSimulationName();
      boolean useConfigPath = false;
      if (traceName == null) {
        // No name specified - use default name
        traceName = this.configName + "-dt-" + System.currentTimeMillis();
        useConfigPath = true;
      }
      File p = useConfigPath ? new File(this.configPath, traceName) : new File(traceName);
      if (!p.mkdir()) {
        boolean success = false;
        for (int retry = 1; retry < 10 && !success; retry++) {
          String nextName = traceName + "-" + retry;
          logger.warn("failed to create data trace directory '" + traceName + "'. Testing '" + nextName + "'.");
          p = useConfigPath ? new File(this.configPath, nextName) : new File(nextName);
          success = p.mkdir();
        }
        if (!success) {
          logger.warn("failed to create data trace directory!");
          return null;
        }
      }
      this.dataTracePath = p;
    }
    File fp = new File(this.dataTracePath, name + '.' + suffix);
    logger.info("simulation data trace '" + fp.getAbsolutePath() + "'");
    return fp;
  }

  public boolean isDataTraceEnabled() {
    return this.isDataTraceEnabled;
  }

  public void setDataTraceEnabled(boolean enabled) {
    if (this.isDataTraceEnabled != enabled) {
      this.isDataTraceEnabled = enabled;
      if (enabled && this.simulation.isRunning()) {
        this.simulationStarted();
      }
    }
  }

  public void logEvent(String eventType, String description) {
    PrintWriter output = this.eventOutput;
    if (output != null) {
      output.println(simulation.getSimulationTime() + "\t" + eventType + "\t" + description);
      output.flush();
    }
  }

  private PrintWriter startPrintWriter(PrintWriter output, String name, String suffix, String description) {
    if (output == null) {
      File outputFilename = getSimulationLogFile(name, suffix);
      if (outputFilename != null) {
        try {
          output = new PrintWriter(new FileWriter(outputFilename, true));
          if (description != null && !description.isBlank()) {
            output.println("# "+ description);
          }
        } catch (IOException e) {
          logger.error("failed to setup " + name + " output log", e);
        }
      }
    }
    if (output != null) {
      output.println("# Simulation started: " + this.simulation.getSimulationTime());
    }
    return output;
  }

  private void stopPrintWriter(PrintWriter output) {
    if (output != null) {
      output.println("# Simulation stopped: " + this.simulation.getSimulationTime());
    }
  }

  private PrintWriter closePrintWriter(PrintWriter output) {
    if (output != null) {
      output.println("# Simulation done: " + this.simulation.getSimulationTime());
      output.close();
    }
    return null;
  }

  private String getRadioListAsString(Radio[] radios) {
    if (radios != null && radios.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0, n = radios.length; i < n; i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append(radios[i].getMote().getID());
      }
      return sb.toString();
    }
    return "";
  }

  void simulationStarted() {
    if (this.isDataTraceEnabled) {
      this.moteLogOutput = startPrintWriter(this.moteLogOutput, "mote-output", "log",
                                            "time\tmote\tmessage");
      this.eventOutput = startPrintWriter(this.eventOutput, "events", "log",
                                          "time\tname\tdescription");
      this.radioMediumOutput = startPrintWriter(this.radioMediumOutput, "radio-medium", "log",
                                                "startTime\tendTime\tchannel\tsource"
                                                + "\tdestinations\tinterfered\tinterferedNonDestinations\tdata");
      if (this.pcapExporter == null) {
        File pcapFile = getSimulationLogFile("radio-log", "pcap");
        if (pcapFile != null) {
          try {
            this.pcapExporter = new PcapExporter();
            this.pcapExporter.openPcap(pcapFile);
          } catch (IOException e) {
            logger.error("failed to setup PCAP export", e);
          }
        }
      }
      setMoteCountListener(true);
      setLogOutputListener(true);
      setRadioTransmissionListener(true);
      logEvent("simulation", "start");
    }
  }

  void simulationStopped() {
    logEvent("simulation", "stop");
    stopPrintWriter(this.eventOutput);
    stopPrintWriter(this.moteLogOutput);
    stopPrintWriter(this.radioMediumOutput);
  }

  void removed() {
    /* Cleanup */
    logEvent("simulation", "done");
    this.moteLogOutput = closePrintWriter(this.moteLogOutput);
    this.radioMediumOutput = closePrintWriter(this.radioMediumOutput);
    this.eventOutput = closePrintWriter(this.eventOutput);

    if (this.pcapExporter != null) {
      try {
        this.pcapExporter.closePcap();
      } catch (IOException e) {
        logger.error("failed to close PCAP exporter", e);
      }
    }
  }

  private boolean isRadioTransmissionEnabled = false;
  private Observer radioMediumObserver;
  private void setRadioTransmissionListener(boolean enable) {
    if (enable == isRadioTransmissionEnabled) {
      return;
    }
    if (this.radioMedium == null) {
      this.radioMedium = this.simulation.getRadioMedium();
      if (this.radioMedium == null) {
        return;
      }
    }
    this.isRadioTransmissionEnabled = enable;
    if (enable) {
      if (this.radioMediumObserver == null) {
        this.radioMediumObserver = new Observer() {
          @Override
          public void update(Observable obs, Object obj) {
            RadioConnection conn = radioMedium.getLastConnection();
            if (conn == null) {
              return;
            }
            RadioPacket packet = conn.getSource().getLastPacketTransmitted();
            if (packet == null) {
              return;
            }
            long startTime = conn.getStartTime();
            long endTime = simulation.getSimulationTime();
            byte[] data = (packet instanceof ConvertedRadioPacket) ? ((ConvertedRadioPacket)packet).getOriginalPacketData() : packet.getPacketData();
            if (pcapExporter != null) {
              try {
                pcapExporter.exportPacketData(data, startTime);
              } catch (IOException e) {
                logger.error("failed to log pcap", e);
              }
            }
            if (radioMediumOutput != null) {
              radioMediumOutput.println(startTime
                                                + "\t" + endTime
                                                + "\t" + conn.getSource().getChannel()
                                                + "\t" + conn.getSource().getMote().getID()
                                                + "\t" + getRadioListAsString(conn.getDestinations())
                                                + "\t" + getRadioListAsString(conn.getInterfered())
                                                + "\t" + getRadioListAsString(conn.getInterferedNonDestinations())
                                                + "\t" + StringUtils.toHex(data));
            }
          }
        };
      }
      this.radioMedium.addRadioTransmissionObserver(this.radioMediumObserver);
    } else if (!this.isDataTraceEnabled && this.radioMediumObserver != null){
      this.radioMedium.deleteRadioTransmissionObserver(this.radioMediumObserver);
    }
  }

  /* GENERIC */
  private static class MoteEvent {
    public static int _ID_COUNTER = 0; /* Debugging */
    public final int ID; /* Debugging */

    private final Mote mote;
    private final long time;

    public MoteEvent(Mote mote, long time) {
      ID = _ID_COUNTER++;

      this.mote = mote;
      this.time = time;
    }
    public Mote getMote() {
      return mote;
    }
    public long getTime() {
      return time;
    }

    public String toString() {
      return "" + ID;
    }
  }
  /** Help class for maintaining mote-specific observations */
  private static class MoteObservation {
    private final Mote mote;
    private final Observable observable;
    private final Observer observer;
    public MoteObservation(Mote mote, Observable observable, Observer observer) {
      this.mote = mote;
      this.observable = observable;
      this.observer = observer;

      observable.addObserver(observer);
    }
    public Mote getMote() {
      return mote;
    }
    public Observer getObserver() {
      return observer;
    }
    public void disconnect() {
      observable.deleteObserver(observer);
    }
  }
  private ArrayList<MoteObservation> moteObservations;

  
  /* ADDED/REMOVED MOTES */
  public interface MoteCountListener {
    public void moteWasAdded(Mote mote);
    public void moteWasRemoved(Mote mote);
  }
  private MoteCountListener[] moteCountListeners;
  private Observer moteCountObserver = new Observer() {
    public void update(Observable obs, Object obj) {
      if (obj == null || !(obj instanceof Mote)) {
        return;
      }
      Mote evMote = (Mote) obj;

      /* Check whether mote was added or removed */
      Mote[] allMotes = simulation.getMotes();
      boolean exists = false;
      for (Mote m: allMotes) {
        if (m == evMote) {
          exists = true;
          break;
        }
      }

      if (exists) {
        /* Mote was added */
        moteWasAdded(evMote);

        /* Notify external listeners */
        for (MoteCountListener l: moteCountListeners) {
          l.moteWasAdded(evMote);
        }
      } else {
        /* Mote was removed */
        moteWasRemoved(evMote);

        /* Notify external listeners */
        for (MoteCountListener l: moteCountListeners) {
          l.moteWasRemoved(evMote);
        }
      }
    }
  };
  private boolean moteCountListenerEnabled = false;
  private void setMoteCountListener(boolean enable) {
    if (this.moteCountListenerEnabled == enable) {
      return;
    }
    this.moteCountListenerEnabled = enable;
    if (enable) {
      simulation.addObserver(moteCountObserver);
    } else if (!this.isDataTraceEnabled) {
      simulation.deleteObserver(moteCountObserver);
    }
  }

  public void addMoteCountListener(MoteCountListener listener) {
    if (!this.isDataTraceEnabled && moteCountListeners.length == 0) {
      /* Observe simulation for added/removed motes */
      setMoteCountListener(true);
    }

    moteCountListeners = ArrayUtils.add(moteCountListeners, listener);
  }
  public void removeMoteCountListener(MoteCountListener listener) {
    moteCountListeners = ArrayUtils.remove(moteCountListeners, listener);

    if (!this.isDataTraceEnabled && moteCountListeners.length == 0) {
      /* Stop observing simulation for added/removed motes */
      setMoteCountListener(false);
    }
  }


  /* LOG OUTPUT */
  public static class LogOutputEvent extends MoteEvent {
    public final String msg;
    public LogOutputEvent(Mote mote, long time, String msg) {
      super(mote, time);
      this.msg = msg;
    }
    public String getMessage() {
      return msg;
    }
  }
  private int logOutputBufferSize;
  private ArrayDeque<LogOutputEvent> logOutputEvents;
  public interface LogOutputListener extends MoteCountListener {
    public void removedLogOutput(LogOutputEvent ev);
    public void newLogOutput(LogOutputEvent ev);
  }
  private LogOutputListener[] logOutputListeners;
  private Observer logOutputObserver = new Observer() {
    public void update(Observable obs, Object obj) {
      Mote mote = (Mote) obj;
      String msg = ((Log) obs).getLastLogMessage();
      if (msg == null) {
        return;
      }
      if (msg.length() > 0 && msg.charAt(msg.length() - 1) == '\n') {
        msg = msg.substring(0, msg.length() - 1);
      }

      /* We may have to remove some events now */
      while (logOutputEvents.size() > logOutputBufferSize-1) {
        LogOutputEvent removed;
        synchronized (logOutputEvents) {
          removed = logOutputEvents.pollFirst();
        }
        if (removed == null) {
          break;
        }
        for (LogOutputListener l: logOutputListeners) {
          l.removedLogOutput(removed);
        }
      }

      /* Store log output, and notify listeners */
      if (SimEventCentral.this.moteLogOutput != null) {
        SimEventCentral.this.moteLogOutput.println(simulation.getSimulationTime() + "\t" + mote.getID() + "\t" + msg);
      }
      LogOutputEvent ev = new LogOutputEvent(mote, simulation.getSimulationTime(), msg);
      synchronized (logOutputEvents) {
        logOutputEvents.add(ev);
      }
      for (LogOutputListener l: logOutputListeners) {
        l.newLogOutput(ev);
      }
    }
  };

  private boolean logOutputListenerEnabled;
  private void setLogOutputListener(boolean enable) {
    if (this.logOutputListenerEnabled == enable) {
      return;
    }
    this.logOutputListenerEnabled = enable;
    if (enable) {
      Mote[] motes = simulation.getMotes();
      for (Mote m : motes) {
        for (MoteInterface mi : m.getInterfaces().getInterfaces()) {
          if (mi instanceof Log) {
            moteObservations.add(new MoteObservation(m, mi, logOutputObserver));
          }
        }
      }
      logger.info("enabled log output listeners");
    } else if (!this.isDataTraceEnabled) {
      MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
      for (MoteObservation o: observations) {
        if (o.getObserver() == logOutputObserver) {
          o.disconnect();
          moteObservations.remove(o);
        }
      }
    }
  }

  public void addLogOutputListener(LogOutputListener listener) {
    if (logOutputListeners.length == 0) {
      /* Start observing all log interfaces */
      setLogOutputListener(true);
    }

    logOutputListeners = ArrayUtils.add(logOutputListeners, listener);
    addMoteCountListener(listener);
  }

  public void removeLogOutputListener(LogOutputListener listener) {
    logOutputListeners = ArrayUtils.remove(logOutputListeners, listener);
    removeMoteCountListener(listener);

    if (logOutputListeners.length == 0) {
      /* Stop observing all log interfaces */
      setLogOutputListener(false);

      /* Clear logs (TODO config) */
      logOutputEvents.clear();
    }
  }

  public LogOutputEvent[] getLogOutputHistory() {
    synchronized (logOutputEvents) {
      return logOutputEvents.toArray(new LogOutputEvent[0]);
    }
  }
  public int getLogOutputBufferSize() {
    return logOutputBufferSize;
  }
  public void setLogOutputBufferSize(int size) {
    logOutputBufferSize = size;
    
    /* We may have to remove some events now */
    while (logOutputEvents.size() > logOutputBufferSize) {
      LogOutputEvent removed = logOutputEvents.pollFirst();
      if (removed == null) {
        break;
      }
      for (LogOutputListener l: logOutputListeners) {
        l.removedLogOutput(removed);
      }
    }
  }

  public int getLogOutputObservationsCount() {
    int count=0;
    MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
    for (MoteObservation o: observations) {
      if (o.getObserver() == logOutputObserver) {
        count++;
      }
    }
    return count;
  }

  
  /* HELP METHODS: MAINTAIN OBSERVERS */
  private void moteWasAdded(Mote mote) {
    if (logOutputListeners.length > 0) {
      /* Add another log output observation.
       * (Supports multiple log interfaces per mote) */
      for (MoteInterface mi: mote.getInterfaces().getInterfaces()) {
        if (mi instanceof Log) {
          moteObservations.add(new MoteObservation(mote, mi, logOutputObserver));
        }
      }
    }

    /* ... */
  }
  private void moteWasRemoved(Mote mote) {
    /* Disconnect and remove mote observations */
    MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
    for (MoteObservation o: observations) {
      if (o.getMote() == mote) {
        o.disconnect();
        moteObservations.remove(o);
      }
    }
  }

  public String toString() {
    return 
    "\nActive mote observations: " + moteObservations.size() +
    "\n" +
    "\nMote count listeners: " + moteCountListeners.length +
    "\n" +
    "\nLog output listeners: " + logOutputListeners.length +
    "\nLog output history: " + logOutputEvents.size()
    ;
  }
  

  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<Element>();
    Element element;

    /* Log output buffer size */
    element = new Element("logoutput");
    element.setText("" + logOutputBufferSize);
    config.add(element);

    /* Data trace */
    element = new Element("datatrace");
    element.setText(Boolean.toString(this.isDataTraceEnabled));
    config.add(element);

    return config;
  }

  public boolean setConfigXML(Simulation simulation,
      Collection<Element> configXML, boolean visAvailable)
      throws MoteTypeCreationException {
    for (Element element : configXML) {
      String name = element.getName();
      if (name.equals("logoutput")) {
        logOutputBufferSize = Integer.parseInt(element.getText());
      } else if (name.equals("datatrace")) {
        this.isDataTraceEnabled = Boolean.valueOf(element.getText());
      }
    }
    return true;
  }
  
}
