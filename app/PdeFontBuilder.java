/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdeFontBuilder - gui interface to font creation heaven/hell
  Part of the Processing project - http://Proce55ing.net

  Copyright (c) 2001-03 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License 
  along with this program; if not, write to the Free Software Foundation, 
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/


import java.awt.*;
import java.awt.event.*;
import java.io.*;
//import java.net.*;
//import java.text.*;
//import java.util.*;
//import java.util.zip.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;


public class PdeFontBuilder extends JFrame {
  File targetFolder;

  JList fontSelector;
  JComboBox styleSelector;
  JTextField sizeSelector;
  JTextArea sample;
  JButton okButton;
  JTextField filenameField;

  Font font;

  String list[];
  int selection = -1;

  static final String styles[] = {
    "Plain", "Bold", "Italic", "Bold Italic" 
  };


  // font.deriveFont(float size)

  public PdeFontBuilder(File targetFolder) {
    super("Create Font");

    this.targetFolder = targetFolder;

    Container pain = getContentPane();
    pain.setLayout(new BoxLayout(pain, BoxLayout.Y_AXIS));

    String labelText = 
      "Use this tool to create bitmap fonts for your program.\n" +
      "Select a font and size, and click 'OK' to generate the font.\n" + 
      "It will be added to the data folder of the current sketch.";

    //JLabel label = new JLabel(labelText);
    JTextArea textarea = new JTextArea(labelText);
    textarea.setBorder(new EmptyBorder(10, 20, 10, 20));
    textarea.setFont(new Font("Dialog", Font.PLAIN, 12));
    pain.add(textarea);
    //pain.add(label);

    //JPanel panel = new JPanel();
    //panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

    /*
      // save this as an alternative implementation
    GraphicsEnvironment ge = 
      GraphicsEnvironment.getLocalGraphicsEnvironment();

    Font fonts[] = ge.getAllFonts();
    String families[] = ge.getAvailableFontFamilyNames();
    */

    /*
    for (int i = 0; i < fonts.length; i++) {
      //System.out.println(fonts[i]);
      if (fonts[i].getFontName().indexOf(fonts[i].getFamily()) != 0) {
        System.out.println(fonts[i]);
      }
    }
    */

    // don't care about families starting with . or #
    // also ignore dialog, dialoginput, monospaced, serif, sansserif

#ifdef JDK13
    // getFontList is deprecated in 1.4, so this has to be used
    GraphicsEnvironment ge = 
      GraphicsEnvironment.getLocalGraphicsEnvironment();

    //Font fonts[] = ge.getAllFonts();
    String flist[] = ge.getAvailableFontFamilyNames();
#else
    //fontSelector = new JComboBox();
    String flist[] = Toolkit.getDefaultToolkit().getFontList();
#endif

    int index = 0;
    for (int i = 0; i < flist.length; i++) {
      if ((flist[i].indexOf('.') == 0) || (flist[i].indexOf('#') == 0) ||
          (flist[i].equals("Dialog")) || (flist[i].equals("DialogInput")) ||
          (flist[i].equals("Serif")) || (flist[i].equals("SansSerif")) ||
          (flist[i].equals("Monospaced"))) continue;
      flist[index++] = flist[i];
    }
    list = new String[index];
    System.arraycopy(flist, 0, list, 0, index);

    fontSelector = new JList(list); //families); 
    fontSelector.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          //System.out.println(e.getFirstIndex());
          selection = e.getFirstIndex();
          okButton.setEnabled(true);

          int fontsize = 0;
          try {
            fontsize = Integer.parseInt(sizeSelector.getText().trim());
            //System.out.println("'" + sizeSelector.getText() + "'");
          } catch (NumberFormatException e2) { }

          if (fontsize != 0) {
            font = new Font(list[selection], Font.PLAIN, fontsize);
            //System.out.println("setting font to " + font);
            sample.setFont(font);

            String filenameSuggestion = list[selection].replace(' ', '_');
            filenameField.setText(filenameSuggestion);
          }
        }
      });

    fontSelector.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane fontScroller = new JScrollPane(fontSelector);
    pain.add(fontScroller);
    //fontSelector.setFont(new Font("SansSerif", Font.PLAIN, 10));
    /*
    fontSelector.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          fontName = e.getActionCommand();
        }
      });
    */

    sample = new JTextArea("The quick brown fox blah blah.") {
        public void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g;
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                              true ? 
                              RenderingHints.VALUE_ANTIALIAS_ON :
                              RenderingHints.VALUE_ANTIALIAS_OFF);
          super.paintComponent(g);
        }
      };

    pain.add(sample);

    //for (int i = 0; i < list.length; i++) {
    /*
    for (int i = 0; i < families.length; i++) {
      //fontSelector.addItem(list[i]);
      fontSelector.addItem(families[i]);
    }
    panel.add(fontSelector);
    */

    /*
    styleSelector = new JComboBox();
    for (int i = 0; i < styles.length; i++) {
      styleSelector.addItem(styles[i]);
    }
    styleSelector.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String command = e.getActionCommand();
          int style = Font.PLAIN;
          if (command.indexOf("Bold") != -1) { 
            style |= Font.BOLD;
          }
          if (command.indexOf("Italic") != -1) {
            style |= Font.ITALIC;
          }
        }
      });
    panel.add(styleSelector);
    */

    JPanel panel = new JPanel();
    panel.add(new JLabel("Size:"));
    sizeSelector = new JTextField("48 ");
    panel.add(sizeSelector);
    JLabel rec = new JLabel("(Recommended size for 3D use is 48 points)");
    if (PdeBase.platform == PdeBase.MACOSX) {
      rec.setFont(new Font("Dialog", Font.PLAIN, 10));
    }
    panel.add(rec);
    pain.add(panel);

    JPanel filestuff = new JPanel();
    filestuff.add(new JLabel("Filename:"));
    filestuff.add(filenameField = new JTextField(25));
    filestuff.add(new JLabel(".vlw"));
    pain.add(filestuff);

    JPanel buttons = new JPanel();
    JButton cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          hide();
        }
      });
    okButton = new JButton("OK");
    okButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          build();
        }
      });
    okButton.setEnabled(false);

    buttons.add(cancelButton);
    buttons.add(okButton);
    pain.add(buttons);

    getRootPane().setDefaultButton(okButton);

    setResizable(false);
    pack();

    // do this after pack so it doesn't affect layout
    sample.setFont(new Font(list[0], Font.PLAIN, 48));

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension size = getSize();

    setLocation((screen.width - size.width) / 2,
                (screen.height - size.height) / 2);
    show();
  }


  public void build() {
    int fontsize = 0;
    try {
      fontsize = Integer.parseInt(sizeSelector.getText().trim());
    } catch (NumberFormatException e) { }

    if (fontsize <= 0) {
      JOptionPane.showMessageDialog(this, "Bad font size, try again.",
                                    "Badness", JOptionPane.WARNING_MESSAGE);
      return;
    }

    String filename = filenameField.getText();
    if (filename.length() == 0) {
      JOptionPane.showMessageDialog(this, "Enter a file name for the font.",
                                    "Lameness", JOptionPane.WARNING_MESSAGE);
      return;
    }
    if (!filename.endsWith(".vlw")) {
      filename += ".vlw";
    }

    try {
      font = new Font(list[selection], Font.PLAIN, fontsize);
      BFont f = new BFont(font, true);
      f.write(new FileOutputStream(new File(targetFolder, filename)));

    } catch (IOException e) {
      JOptionPane.showMessageDialog(this, 
                                    "An error occurred while creating font.",
                                    "No font for you", 
                                    JOptionPane.WARNING_MESSAGE);
      e.printStackTrace();
    }

    hide();
  }
}
