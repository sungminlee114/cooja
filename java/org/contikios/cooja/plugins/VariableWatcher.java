/*
 * Copyright (c) 2014, TU Braunschweig.
 * Copyright (c) 2009, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.plugins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MotePlugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.mote.memory.UnknownVariableException;
import org.contikios.cooja.mote.memory.VarMemory;

/**
 * Variable Watcher enables a user to watch mote variables during a simulation.
 * Variables can be read or written either as bytes, integers or byte arrays.
 *
 * User can also see which variables seems to be available on the selected node.
 *
 * @author Fredrik Osterlind
 * @author Enrico Jorns
 */
@ClassDescription("Variable Watcher")
@PluginType(PluginType.MOTE_PLUGIN)
public class VariableWatcher extends VisPlugin implements MotePlugin {
  private static final long serialVersionUID = 1L;

  private VarMemory moteMemory;

  private final static int LABEL_WIDTH = 170;
  private final static int LABEL_HEIGHT = 15;

  private final static int BYTE_INDEX = 0;
  private final static int INT_INDEX = 1;
  private final static int ARRAY_INDEX = 2;
  private final static int CHAR_ARRAY_INDEX = 3;

  private JPanel lengthPane;
  private JPanel valuePane;
  private JPanel charValuePane;
  private JComboBox varNameCombo;
  private JComboBox varTypeCombo;
  private JFormattedTextField[] varValues;
  private JTextField[] charValues;
  private JFormattedTextField varLength;
  private JButton writeButton;
  private JLabel debuglbl;
  private KeyListener charValueKeyListener;
  private FocusListener charValueFocusListener;
  private KeyListener varValueKeyListener;
  private FocusAdapter jFormattedTextFocusAdapter;

  private NumberFormat integerFormat;

  private Mote mote;
  
  /**
   * @param moteToView Mote
   * @param simulation Simulation
   * @param gui GUI
   */
  public VariableWatcher(Mote moteToView, Simulation simulation, Cooja gui) {
    super("Variable Watcher (" + moteToView + ")", gui);
    this.mote = moteToView;
    moteMemory = new VarMemory(moteToView.getMemory());

    JLabel label;
    integerFormat = NumberFormat.getIntegerInstance();
    JPanel mainPane = new JPanel();
    mainPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.Y_AXIS));
    JPanel smallPane;

    // Variable name
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Variable name");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));
    smallPane.add(BorderLayout.WEST, label);

    varNameCombo = new JComboBox();
    varNameCombo.setEditable(true);
    varNameCombo.setSelectedItem("[enter or pick name]");

    List<String> allPotentialVarNames = new ArrayList<>(moteMemory.getVariableNames());
    Collections.sort(allPotentialVarNames);
    for (String aVarName: allPotentialVarNames) {
      varNameCombo.addItem(aVarName);
    }

    varNameCombo.addKeyListener(new KeyListener() {
      @Override
      public void keyPressed(KeyEvent e) {
        writeButton.setEnabled(false);
      }
      @Override
      public void keyTyped(KeyEvent e) {
        writeButton.setEnabled(false);
      }
      @Override
      public void keyReleased(KeyEvent e) {
        writeButton.setEnabled(false);
      }
    });

    smallPane.add(BorderLayout.EAST, varNameCombo);
    mainPane.add(smallPane);

    // Variable type
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Variable type");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));
    smallPane.add(BorderLayout.WEST, label);

    varTypeCombo = new JComboBox();
    varTypeCombo.addItem("Byte (1 byte)"); // BYTE_INDEX = 0
    varTypeCombo.addItem("Integer (" + moteToView.getMemory().getLayout().intSize + " bytes)"); // INT_INDEX = 1
    varTypeCombo.addItem("Byte array (x bytes)"); // ARRAY_INDEX = 2
    varTypeCombo.addItem("Char array (x bytes)"); // CHAR_ARRAY_INDEX = 3

    varTypeCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int selectedIndex = varTypeCombo.getSelectedIndex();  
        if (selectedIndex == ARRAY_INDEX || selectedIndex == CHAR_ARRAY_INDEX) {
          lengthPane.setVisible(true);
          setNumberOfValues(((Number) varLength.getValue()).intValue());
          if(selectedIndex == CHAR_ARRAY_INDEX) {
            charValuePane.setVisible(true);
            setNumberOfCharValues(((Number) varLength.getValue()).intValue());
          } else {
            charValuePane.setVisible(false);
            setNumberOfCharValues(1);  
          }
        } else {
          lengthPane.setVisible(false);
          charValuePane.setVisible(false);
          setNumberOfValues(1);
          setNumberOfCharValues(1);
        }
        pack();
      }
    });

    smallPane.add(BorderLayout.EAST, varTypeCombo);
    mainPane.add(smallPane);

    /* The recommended fix for the bug #4740914
     * Synopsis : Doing selectAll() in a JFormattedTextField on focusGained
     * event doesn't work. 
     */
    jFormattedTextFocusAdapter = new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent ev) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            JTextField jtxt = (JTextField)ev.getSource();
            jtxt.selectAll();
          }
        });
      }
    };

    // Variable length
    lengthPane = new JPanel(new BorderLayout());
    label = new JLabel("Variable length");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));
    lengthPane.add(BorderLayout.WEST, label);

    varLength = new JFormattedTextField(integerFormat);
    varLength.setValue(new Integer(1));
    varLength.setColumns(4);
    varLength.addPropertyChangeListener("value", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent e) {
        setNumberOfValues(((Number) varLength.getValue()).intValue());
        if(varTypeCombo.getSelectedIndex() == CHAR_ARRAY_INDEX) {
          setNumberOfCharValues(((Number) varLength.getValue()).intValue());    
        }
      }
    });
    varLength.addFocusListener(jFormattedTextFocusAdapter);

    lengthPane.add(BorderLayout.EAST, varLength);
    mainPane.add(lengthPane);
    mainPane.add(Box.createRigidArea(new Dimension(0,5)));

    lengthPane.setVisible(false);

    // Variable value label
    label = new JLabel("Variable value");
    label.setAlignmentX(JLabel.CENTER_ALIGNMENT);
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));
    mainPane.add(label);

    // Variable value(s)
    valuePane = new JPanel();
    valuePane.setLayout(new BoxLayout(valuePane, BoxLayout.X_AXIS));

    varValues = new JFormattedTextField[1];
    varValues[0] = new JFormattedTextField(integerFormat);
    varValues[0].setValue(new Integer(0));
    varValues[0].setColumns(3);
    varValues[0].setText("?");

    for (JFormattedTextField varValue: varValues) {
      valuePane.add(varValue);

    }
    charValuePane = new JPanel();
    charValuePane.setLayout(new BoxLayout(charValuePane, BoxLayout.X_AXIS));
    charValues = new JTextField[1];
    charValues[0] = new JTextField();
    charValues[0].setText("?");
    charValues[0].setColumns(1);
    charValues[0].setDocument(new JTextFieldLimit(1, false));

    /* Key Listener for char value changes. */
    charValueKeyListener = new KeyListener(){
      @Override
      public void keyPressed(KeyEvent arg0) {
        Component comp = arg0.getComponent();
        JTextField jtxt = (JTextField)comp;
        int index = comp.getParent().getComponentZOrder(comp);
        if(jtxt.getText().trim().length() != 0) {
          char ch = jtxt.getText().trim().charAt(0);
          varValues[index].setValue(new Integer(ch));
        } else {
          varValues[index].setValue(new Integer(0));  
        }
      }

      @Override
      public void keyReleased(KeyEvent arg0) {
        Component comp = arg0.getComponent();
        JTextField jtxt = (JTextField)comp;
        int index = comp.getParent().getComponentZOrder(comp);
        if(jtxt.getText().trim().length() != 0) {
          char ch = jtxt.getText().trim().charAt(0);
          varValues[index].setValue(new Integer(ch));
        } else {
          varValues[index].setValue(new Integer(0));
        }
      }

      @Override
      public void keyTyped(KeyEvent arg0) {
        Component comp = arg0.getComponent();
        JTextField jtxt = (JTextField)comp;
        int index = comp.getParent().getComponentZOrder(comp);
        if(jtxt.getText().trim().length() != 0) {
          char ch = jtxt.getText().trim().charAt(0);
          varValues[index].setValue(new Integer(ch));
        } else {
          varValues[index].setValue(new Integer(0));
        }
      }           
    };

    /* Key Listener for value changes. */  
    varValueKeyListener = new KeyListener() {
      @Override
      public void keyPressed(KeyEvent arg0) {
        Component comp = arg0.getComponent();
        JFormattedTextField fmtTxt = (JFormattedTextField)comp;
        int index = comp.getParent().getComponentZOrder(comp);
        try {
          int value = Integer.parseInt(fmtTxt.getText().trim());
          char ch = (char)(0xFF & value);
          charValues[index].setText(Character.toString(ch));
        } catch(Exception e) {
          charValues[index].setText(Character.toString((char)0));
        }
      }

      @Override
      public void keyReleased(KeyEvent arg0) {
        Component comp = arg0.getComponent();
        JFormattedTextField fmtTxt = (JFormattedTextField)comp;
        int index = comp.getParent().getComponentZOrder(comp);
        try {
          int value = Integer.parseInt(fmtTxt.getText().trim());
          char ch = (char)(0xFF & value);
          charValues[index].setText(Character.toString(ch));
        } catch(Exception e) {
          charValues[index].setText(Character.toString((char)0));
        }               
      }  

      @Override
      public void keyTyped(KeyEvent arg0) {
        Component comp = arg0.getComponent();
        JFormattedTextField fmtTxt = (JFormattedTextField)comp;
        int index = comp.getParent().getComponentZOrder(comp);
        try {
          int value = Integer.parseInt(fmtTxt.getText().trim());
          char ch = (char)(0xFF & value);
          charValues[index].setText(Character.toString(ch));
        } catch(Exception e) {
          charValues[index].setText(Character.toString((char)0));
        }
      }

    };

    charValueFocusListener = new FocusListener() {
      @Override
      public void focusGained(FocusEvent arg0) {
        JTextField jtxt = (JTextField)arg0.getComponent();
        jtxt.selectAll();
      }
      @Override
      public void focusLost(FocusEvent arg0) {

      }
    };


    for (JTextField charValue: charValues) {
      charValuePane.add(charValue);     
    }

    mainPane.add(valuePane);
    mainPane.add(Box.createRigidArea(new Dimension(0,5)));
    charValuePane.setVisible(false);
    mainPane.add(charValuePane);
    mainPane.add(Box.createRigidArea(new Dimension(0,5)));

    debuglbl = new JLabel();
    mainPane.add(new JPanel().add(debuglbl));
    mainPane.add(Box.createRigidArea(new Dimension(0,5)));

    // Read/write buttons
    smallPane = new JPanel(new BorderLayout());
    JButton button = new JButton("Read");
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (varTypeCombo.getSelectedIndex() == BYTE_INDEX) {
          try {
            byte val = moteMemory.getByteValueOf((String) varNameCombo.getSelectedItem());
            varValues[0].setValue(new Integer(0xFF & val));
            varNameCombo.setBackground(Color.WHITE);
            writeButton.setEnabled(true);
          } catch (UnknownVariableException ex) {
            varNameCombo.setBackground(Color.RED);
            writeButton.setEnabled(false);
          }
        } else if (varTypeCombo.getSelectedIndex() == INT_INDEX) {
          try {
            int val = moteMemory.getIntValueOf((String) varNameCombo.getSelectedItem());
            varValues[0].setValue(new Integer(val));
            varNameCombo.setBackground(Color.WHITE);
            writeButton.setEnabled(true);
          } catch (UnknownVariableException ex) {
            varNameCombo.setBackground(Color.RED);
            writeButton.setEnabled(false);
          }
        } else if (varTypeCombo.getSelectedIndex() == ARRAY_INDEX || 
            varTypeCombo.getSelectedIndex() == CHAR_ARRAY_INDEX) {
          try {
            int length = ((Number) varLength.getValue()).intValue();
            byte[] vals = moteMemory.getByteArray((String) varNameCombo.getSelectedItem(), length);
            for (int i=0; i < length; i++) {
              varValues[i].setValue(new Integer(0xFF & vals[i]));
            }
            if(varTypeCombo.getSelectedIndex() == CHAR_ARRAY_INDEX) {
              for (int i=0; i < length; i++) {
                char ch = (char)(0xFF & vals[i]);  
                charValues[i].setText(Character.toString(ch));  
                varValues[i].addKeyListener(varValueKeyListener);
              } 
            }
            varNameCombo.setBackground(Color.WHITE);
            writeButton.setEnabled(true);
          } catch (UnknownVariableException ex) {
            varNameCombo.setBackground(Color.RED);
            writeButton.setEnabled(false);
          }
        }
      }
    });
    smallPane.add(BorderLayout.WEST, button);

    button = new JButton("Write");
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (varTypeCombo.getSelectedIndex() == BYTE_INDEX) {
          try {
            byte val = (byte) ((Number) varValues[0].getValue()).intValue();
            moteMemory.setByteValueOf((String) varNameCombo.getSelectedItem(), val);
            varNameCombo.setBackground(Color.WHITE);
          } catch (UnknownVariableException ex) {
            varNameCombo.setBackground(Color.RED);
          }
        } else if (varTypeCombo.getSelectedIndex() == INT_INDEX) {
          try {
            int val = ((Number) varValues[0].getValue()).intValue();
            moteMemory.setIntValueOf((String) varNameCombo.getSelectedItem(), val);
            varNameCombo.setBackground(Color.WHITE);
          } catch (UnknownVariableException ex) {
            varNameCombo.setBackground(Color.RED);
          }
        } else if (varTypeCombo.getSelectedIndex() == ARRAY_INDEX || 
            varTypeCombo.getSelectedIndex() == CHAR_ARRAY_INDEX) {
          try {
            int length = ((Number) varLength.getValue()).intValue();
            byte[] vals = new byte[length];
            for (int i=0; i < length; i++) {
              vals[i] = (byte) ((Number) varValues[i].getValue()).intValue();
            }

            moteMemory.setByteArray((String) varNameCombo.getSelectedItem(), vals);
            varNameCombo.setBackground(Color.WHITE);
            writeButton.setEnabled(true);
          } catch (UnknownVariableException ex) {
            varNameCombo.setBackground(Color.RED);
            writeButton.setEnabled(false);
          }
        }
      }
    });
    smallPane.add(BorderLayout.EAST, button);
    button.setEnabled(false);
    writeButton = button;
    mainPane.add(smallPane);

    add(BorderLayout.NORTH, mainPane);
    pack();
  }

  private void setNumberOfValues(int nr) {
    valuePane.removeAll();

    if (nr > 0) {
      varValues = new JFormattedTextField[nr];
      for (int i=0; i < nr; i++) {
        varValues[i] = new JFormattedTextField(integerFormat);
        varValues[i] .setValue(new Integer(0));
        varValues[i] .setColumns(3);
        varValues[i] .setText("?");
        varValues[i].addFocusListener(jFormattedTextFocusAdapter);
        valuePane.add(varValues[i]);
      }
    }
    pack();
  }

  private void setNumberOfCharValues(int nr) {
    charValuePane.removeAll();

    if (nr > 0) {
      charValues = new JTextField[nr];
      for (int i=0; i < nr; i++) {
        charValues[i] = new JTextField();
        charValues[i] .setColumns(1);
        charValues[i] .setText("?");
        charValues[i].setDocument(new JTextFieldLimit(1, false));
        charValues[i].addKeyListener(charValueKeyListener);
        charValues[i].addFocusListener(charValueFocusListener);
        charValuePane.add(charValues[i]);
      }
    }
    pack();
  }

  @Override
  public void closePlugin() {
  }

  @Override
  public Collection<Element> getConfigXML() {
    // Return currently watched variable and type
    Vector<Element> config = new Vector<Element>();

    Element element;

    // Selected variable name
    element = new Element("varname");
    element.setText((String) varNameCombo.getSelectedItem());
    config.add(element);

    // Selected variable type
    if (varTypeCombo.getSelectedIndex() == BYTE_INDEX) {
      element = new Element("vartype");
      element.setText("byte");
      config.add(element);
    } else if (varTypeCombo.getSelectedIndex() == INT_INDEX) {
      element = new Element("vartype");
      element.setText("int");
      config.add(element);
    } else if (varTypeCombo.getSelectedIndex() == ARRAY_INDEX) {
      element = new Element("vartype");
      element.setText("array");
      config.add(element);
      element = new Element("array_length");
      element.setText(varLength.getValue().toString());
      config.add(element);
    } else if (varTypeCombo.getSelectedIndex() == CHAR_ARRAY_INDEX) {
      element = new Element("vartype");
      element.setText("chararray");
      config.add(element);
      element = new Element("array_length");
      element.setText(varLength.getValue().toString());
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    lengthPane.setVisible(false);
    setNumberOfValues(1);
    varLength.setValue(1);

    for (Element element : configXML) {
      if (element.getName().equals("varname")) {
        varNameCombo.setSelectedItem(element.getText());
      } else if (element.getName().equals("vartype")) {
        if (element.getText().equals("byte")) {
          varTypeCombo.setSelectedIndex(BYTE_INDEX);
        } else if (element.getText().equals("int")) {
          varTypeCombo.setSelectedIndex(INT_INDEX);
        } else if (element.getText().equals("array")) {
          varTypeCombo.setSelectedIndex(ARRAY_INDEX);
          lengthPane.setVisible(true);
        } else if (element.getText().equals("chararray")) {
          varTypeCombo.setSelectedIndex(CHAR_ARRAY_INDEX);
          lengthPane.setVisible(true);
        }
      } else if (element.getName().equals("array_length")) {
        int nrValues = Integer.parseInt(element.getText());
        setNumberOfValues(nrValues);
        varLength.setValue(nrValues);
      }
    }

    return true;
  }

  @Override
  public Mote getMote() {
    return mote;
  }
}

/* Limit JTextField input class */
class JTextFieldLimit extends PlainDocument {

  private static final long serialVersionUID = 1L;
  private int limit;
  // optional uppercase conversion
  private boolean toUppercase = false;

  JTextFieldLimit(int limit) {
    super();
    this.limit = limit;
  }

  JTextFieldLimit(int limit, boolean upper) {
    super();
    this.limit = limit;
    toUppercase = upper;
  }

  @Override
  public void insertString(int offset, String  str, AttributeSet attr)
  throws BadLocationException {
    if (str == null) {
      return;
    }

    if ((getLength() + str.length()) <= limit) {
      if (toUppercase) {
        str = str.toUpperCase();
      }
      super.insertString(offset, str, attr);
    }
  }
}
