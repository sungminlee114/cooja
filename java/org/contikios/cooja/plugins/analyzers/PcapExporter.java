package org.contikios.cooja.plugins.analyzers;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

public class PcapExporter {
  private static final Logger logger = Logger.getLogger(PcapExporter.class);

  DataOutputStream out;

  public PcapExporter() throws IOException {
  }

  public void openPcap(File pcapFile) throws IOException {
    this.openPcap(pcapFile, false);
  }

  public void openPcap(File pcapFile, boolean compress) throws IOException {
    if (out != null) {
      closePcap();
    }
    if (pcapFile == null) {
      /* pcap file not specified, use default file name */
      String filename = "radiolog-" + System.currentTimeMillis() + ".pcap";
      if (compress) {
        filename += ".gz";
      }
      pcapFile = new File(filename);
    }
    if (compress) {
      out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(pcapFile)));
    } else {
      out = new DataOutputStream(new FileOutputStream(pcapFile));
    }

    /* pcap header */
    out.writeInt(0xa1b2c3d4);
    out.writeShort(0x0002);
    out.writeShort(0x0004);
    out.writeInt(0);
    out.writeInt(0);
    out.writeInt(4096);
    out.writeInt(195); /* 195 for LINKTYPE_IEEE802_15_4 */

    out.flush();
    logger.info("Opened pcap file " + pcapFile);
  }

  public void closePcap() throws IOException {
    out.close();
    out = null;
  }

  public void exportPacketData(byte[] data, long ts) throws IOException {
    if (out == null) {
      /* pcap file never set, open default */
      openPcap(null);
    }
    try {
      /* pcap packet header */
      out.writeInt((int) (ts / 1000000));
      out.writeInt((int) (ts % 1000000));
      out.writeInt(data.length);
      out.writeInt(data.length);
      /* and the data */
      out.write(data);
      out.flush();
    } catch (Exception e) {
      logger.error(e);
    }
  }

}
