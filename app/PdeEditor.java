/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdeEditor - main editor panel for the processing ide
  Part of the Processing project - http://Proce55ing.net

  Except where noted, code is written by Ben Fry and
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
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import com.oroinc.text.regex.*;

#ifdef MACOS
import com.apple.mrj.*;
#endif


public class PdeEditor extends JFrame
#ifdef MACOS
  implements MRJAboutHandler, MRJQuitHandler, MRJPrefsHandler
#endif 
{
  // yeah
  static final String WINDOW_TITLE = "Processing";

  // p5 icon for the window
  Image icon;

  // otherwise, if the window is resized with the message label
  // set to blank, it's preferredSize() will be fukered
  static final String EMPTY = 
    "                                                                     " +
    "                                                                     " +
    "                                                                     ";

  static final int SK_NEW  = 1;
  static final int SK_OPEN = 2;
  static final int DO_OPEN = 3;
  static final int DO_QUIT = 4;
  int checking;
  String openingPath; 
  String openingName;

  PdeEditorListener listener;

  PdeEditorButtons buttons;
  PdeEditorHeader header;
  PdeEditorStatus status;
  PdeEditorConsole console;

  JSplitPane splitPane;
  JPanel consolePanel;

  JEditTextArea textarea;

  // currently opened program
  PdeSketch sketch;

  Point appletLocation; //= new Point(0, 0);
  Point presentLocation; // = new Point(0, 0);

  Window presentationWindow;

  //RunButtonWatcher watcher;
  //PdeRuntime runtime;
  //boolean externalRuntime;
  //String externalPaths;
  //File externalCode;

  JMenuItem saveMenuItem;
  JMenuItem saveAsMenuItem;
  JMenuItem beautifyMenuItem;

  //JMenu exportMenu;

  // 

  boolean running;
  boolean presenting;
  boolean renaming;

  PdeMessageStream messageStream;

  // location for lib/build, contents for which will be emptied
  //String tempBuildPath;

  //static final String TEMP_CLASS = "Temporary";

  // undo fellers
  JMenuItem undoItem, redoItem;

  protected UndoAction undoAction;
  protected RedoAction redoAction;
  static public UndoManager undo = new UndoManager(); // editor needs this guy

  // 

  //PdeHistory history;  // TODO re-enable history
  PdeSketchbook sketchbook;
  PdePreferences preferences;
  PdeEditorFind find;

  //static Properties keywords; // keyword -> reference html lookup


  public PdeEditor() {
    super(WINDOW_TITLE + " - " + PdeBase.VERSION);
    // this is needed by just about everything else
    preferences = new PdePreferences();


#ifdef MACOS
      // #@$*(@#$ apple.. always gotta think different
      MRJApplicationUtils.registerAboutHandler(this);
      MRJApplicationUtils.registerPrefsHandler(this);
      MRJApplicationUtils.registerQuitHandler(this);
#endif

    // set the window icon

    try {
      //icon = Toolkit.getDefaultToolkit().getImage("lib/icon.gif");
      icon = PdeBase.getImage("icon.gif", this);
      setIconImage(icon);
    } catch (Exception e) { } // fail silently, no big whup


    // add listener to handle window close box hit event
    addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          handleQuit();
        }
      });

    PdeKeywords keywords = new PdeKeywords();
    // TODO re-enable history
    //history = new PdeHistory(this);
    sketchbook = new PdeSketchbook(this);

    JMenuBar menubar = new JMenuBar();
    menubar.add(buildFileMenu());
    menubar.add(buildEditMenu());
    menubar.add(buildSketchMenu());
    // what platform has their help menu way on the right?
    //if ((PdeBase.platform == PdeBase.WINDOWS) || 
    //menubar.add(Box.createHorizontalGlue());
    menubar.add(buildHelpMenu());

    setJMenuBar(menubar);

    Container pain = getContentPane();
    pain.setLayout(new BorderLayout());

    buttons = new PdeEditorButtons(this);
    pain.add("West", buttons);

    JPanel rightPanel = new JPanel();
    rightPanel.setLayout(new BorderLayout());

    header = new PdeEditorHeader(this);
    rightPanel.add(header, BorderLayout.NORTH);

    textarea = new JEditTextArea(new PdeTextAreaDefaults());
    textarea.setRightClickPopup(new TextAreaPopup());
    textarea.setTokenMarker(new PdeKeywords());

    // assemble console panel, consisting of status area and the console itself
    consolePanel = new JPanel();
    //System.out.println(consolePanel.getInsets());
    consolePanel.setLayout(new BorderLayout());

    status = new PdeEditorStatus(this);
    consolePanel.add(status, BorderLayout.NORTH);
    console = new PdeEditorConsole(this);
    consolePanel.add(console, BorderLayout.CENTER);

    splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                               textarea, consolePanel);

    splitPane.setOneTouchExpandable(true);
    // repaint child panes while resizing
    splitPane.setContinuousLayout(true);
    // if window increases in size, give all of increase to textarea (top pane)
    splitPane.setResizeWeight(1D);

    // to fix ugliness.. normally macosx java 1.3 puts an 
    // ugly white border around this object, so turn it off.
    if (PdeBase.platform == PdeBase.MACOSX) {
      splitPane.setBorder(null);
    }

    // the default size on windows is too small and kinda ugly
    int dividerSize = PdePreferences.getInteger("editor.divider.size");
    if (dividerSize != 0) {
      splitPane.setDividerSize(dividerSize);
    }

    rightPanel.add(splitPane, BorderLayout.CENTER);

    pain.add("Center", rightPanel);

    // hopefully these are no longer needed w/ swing
    // (har har har.. that was wishful thinking)
    listener = new PdeEditorListener(this, textarea);
    textarea.pdeEditorListener = listener;

    // set the undo stuff for this feller
    Document document = textarea.getDocument();
    document.addUndoableEditListener(new PdeUndoableEditListener());

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    if ((PdeBase.platform == PdeBase.MACOSX) ||
        (PdeBase.platform == PdeBase.MACOS9)) {
      presentationWindow = new Frame();

      // mrj is still (with version 2.2.x) a piece of shit, 
      // and doesn't return valid insets for frames
      //presentationWindow.pack(); // make a peer so insets are valid
      //Insets insets = presentationWindow.getInsets();
      // the extra +20 is because the resize boxes intrude
      Insets insets = new Insets(21, 5, 5 + 20, 5);

      presentationWindow.setBounds(-insets.left, -insets.top, 
                                   screen.width + insets.left + insets.right, 
                                   screen.height + insets.top + insets.bottom);
    } else {
      presentationWindow = new Frame();
#ifdef JDK14
      ((Frame)presentationWindow).setUndecorated(true);
#endif
      presentationWindow.setBounds(0, 0, screen.width, screen.height);
    }

    Label label = new Label("stop");
    label.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          setVisible(true);
          doClose();
        }});

    Dimension labelSize = new Dimension(60, 20);
    presentationWindow.setLayout(null);
    presentationWindow.add(label);
    label.setBounds(5, screen.height - 5 - labelSize.height, 
                    labelSize.width, labelSize.height);

    Color presentationBgColor = 
      PdePreferences.getColor("run.present.bgcolor");
    presentationWindow.setBackground(presentationBgColor);

    textarea.addFocusListener(new FocusAdapter() {
        public void focusGained(FocusEvent e) {
          if (presenting == true) {
            try {
              presentationWindow.toFront();
              runtime.applet.requestFocus();
            } catch (Exception ex) { }
          }
        }
      });

    this.addFocusListener(new FocusAdapter() {
        public void focusGained(FocusEvent e) {
          if (presenting == true) {
            try {
              presentationWindow.toFront();
              runtime.applet.requestFocus();
            } catch (Exception ex) { }
          }
        }
      });

    // moved from the PdeRuntime window to the main presentation window
    // [toxi 030903]
    presentationWindow.addKeyListener(new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          //System.out.println("window got " + e);
          if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            runtime.stop();
            doClose();
          } else {
            // pass on the event to the applet [toxi 030903]
            runtime.applet.keyPressed(e);
          }
        }
      });
  }


  // hack for #@#)$(* macosx
  public Dimension getMinimumSize() {
    return new Dimension(500, 500);
  }


  // ...................................................................


  /**
   * Post-constructor setup for the editor area. Loads the last
   * sketch that was used (if any), and restores other Editor settings.
   * The complement to "storePreferences", this is called when the 
   * application is first launched.
   */
  public void restorePreferences() {
    // figure out window placement

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    boolean windowPositionInvalid = false;

    if (PdePreferences.get("last.screen.height") != null) {
      // if screen size has changed, the window coordinates no longer
      // make sense, so don't use them unless they're identical
      int screenW = PdePreferences.getInteger("last.screen.width");
      int screenH = PdePreferences.getInteger("last.screen.height");

      if ((screen.width != screenW) || (screen.height != screenH)) {
        windowPositionInvalid = true;
      }
    } else {
      windowPositionInvalid = true;
    }

    if (windowPositionInvalid) {
      //System.out.println("using default size");
      int windowH = PdePreferences.getInteger("default.window.height");
      int windowW = PdePreferences.getInteger("default.window.width");
      setBounds((screen.width - windowW) / 2, 
                (screen.height - windowH) / 2,
                windowW, windowH);
      // this will be invalid as well, so grab the new value
      PdePreferences.setInteger("last.divider.location", 
                                splitPane.getDividerLocation());
    } else {
      setBounds(PdePreferences.getInteger("last.window.x"), 
                PdePreferences.getInteger("last.window.y"), 
                PdePreferences.getInteger("last.window.width"), 
                PdePreferences.getInteger("last.window.height"));
    }


    // last sketch that was in use

    //String sketchName = PdePreferences.get("last.sketch.name");
    String sketchPath = PdePreferences.get("last.sketch.path");
    //PdeSketch sketchTemp = new PdeSketch(sketchPath);

    //if (sketchName != null) {
    if ((sketchPath != null) && (new File(sketchPath)).exists()) {
      skOpen(new PdeSketch(sketchFile));
      //if (new File(sketchDir + File.separator + sketchName + ".pde").exists()) {
      //skOpen(sketchDir, sketchName);

      //} else {
      //skNew2(true);
      //}
    } else {
      skNew2(true);
    }


    // location for the console/editor area divider

    int location = PdePreferences.getInteger("last.divider.location");
    splitPane.setDividerLocation(location);


    // read the preferences that are settable in the preferences window

    applyPreferences();
  }


  /**
   * Apply changes to preferences that come from changes 
   * by the user in the preferences window.
   */
  public void applyPreferences() {
    // apply the setting for 'use external editor' 

    boolean external = PdePreferences.getBoolean("editor.external");

    listener.setExternalEditor(external);
    textarea.setEditable(!external);
    saveMenuItem.setEnabled(!external);
    saveAsMenuItem.setEnabled(!external);
    beautifyMenuItem.setEnabled(!external);

    TextAreaPainter painter = textarea.getPainter();
    if (external) {
      // disable line highlight and turn off the caret when disabling
      Color color = PdePreferences.getColor("editor.external.bgcolor");
      painter.setBackground(color);
      painter.lineHighlight = false;
      textarea.setCaretVisible(false);

    } else {
      Color color = PdePreferences.getColor("editor.bgcolor");
      painter.setBackground(color);
      painter.lineHighlight = 
        PdePreferences.getBoolean("editor.linehighlight");
      textarea.setCaretVisible(true);
    }


    // in case library option has been enabled or disabled

    //buildExportMenu();


    // in case moved to a new location

    sketchbook.rebuildMenu();
  }


  /**
   * Store preferences about the editor's current state.
   * Called when the application is quitting.
   */
  public void storePreferences() {
    //System.out.println("storing preferences");

    // window location information
    Rectangle bounds = getBounds();
    PdePreferences.setInteger("last.window.x", bounds.x);
    PdePreferences.setInteger("last.window.y", bounds.y);
    PdePreferences.setInteger("last.window.width", bounds.width);
    PdePreferences.setInteger("last.window.height", bounds.height);

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    PdePreferences.setInteger("last.screen.width", screen.width);
    PdePreferences.setInteger("last.screen.height", screen.height);

    // last sketch that was in use
    //PdePreferences.set("last.sketch.name", sketchName);
    //PdePreferences.set("last.sketch.name", sketch.name);
    PdePreferences.set("last.sketch.path", sketch.getMainFilePath());

    // location for the console/editor area divider
    int location = splitPane.getDividerLocation();    
    PdePreferences.setInteger("last.divider.location", location);
  }


  // ...................................................................


  protected JMenu buildFileMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("File");

    item = newJMenuItem("New", 'N');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          skNew();
        }
      });
    menu.add(item);

    menu.add(sketchbook.rebuildMenu());

    saveMenuItem = newJMenuItem("Save", 'S');
    saveMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doSave();
        }
      });
    menu.add(saveMenuItem);

    saveAsMenuItem = newJMenuItem("Save as...", 'S', true);
    saveAsMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          skSaveAs(false);
        }
      });
    menu.add(saveAsMenuItem);

    item = new JMenuItem("Rename...");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          skSaveAs(true);
        }
      });
    menu.add(item);

    item = newJMenuItem("Export", 'E');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.message("Exporting code...");
          try {
            if (sketch.export()) {
              message("Done exporting.");
            } else {
              // error message will already be visible
            }
          } catch (Exception e) {
            editor.message("Error during export.");
            e.printStackTrace();
          }
          buttons.clear();
        }
      });
    //exportMenu = buildExportMenu();
    //menu.add(exportMenu);

    menu.addSeparator();

    item = newJMenuItem("Page Setup", 'P', true);
    item.setEnabled(false);
    menu.add(item);

    item = newJMenuItem("Print", 'P');
    item.setEnabled(false);
    menu.add(item);

    // macosx already has its own preferences and quit menu
    if (PdeBase.platform != PdeBase.MACOSX) {
      menu.addSeparator();

      item = new JMenuItem("Preferences");
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            handlePrefs();
          } 
        });
      menu.add(item);

      menu.addSeparator();

      item = newJMenuItem("Quit", 'Q');
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            handleQuit();
          } 
        });
      menu.add(item);
    }
    return menu;
  }


  /*
  protected JMenu buildExportMenu() {
    if (exportMenu == null) {
      exportMenu = new JMenu("Export");
    } else {
      exportMenu.removeAll();
    }
    JMenuItem item;

    item = newJMenuItem("Applet", 'E');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          skExport();
        }
      });
    exportMenu.add(item);

    item = newJMenuItem("Application", 'E', true);
    item.setEnabled(false);
    exportMenu.add(item);

    if (PdePreferences.getBoolean("export.library")) {
      item = new JMenuItem("Library");
      item.setEnabled(false);
      exportMenu.add(item);
    }
    return exportMenu;
  }
  */


  protected JMenu buildSketchMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("Sketch");

    item = newJMenuItem("Run", 'R');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doRun(false);
        }
      });
    menu.add(item);

    item = newJMenuItem("Present", 'R', true);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doRun(true);
        }
      });
    menu.add(item);

    menu.add(newJMenuItem("Stop", 'T'));
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleStop();
        }
      });
    menu.addSeparator();

    item = new JMenuItem("Add file...");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          sketch.addFile();
        }
      });
    menu.add(item);

    item = new JMenuItem("Create font...");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          new PdeFontBuilder(sketch.dataFolder);
        }
      });
    menu.add(item);

    if ((PdeBase.platform == PdeBase.WINDOWS) || 
        (PdeBase.platform == PdeBase.MACOSX)) {
      // no way to do an 'open in file browser' on other platforms
      // since there isn't any sort of standard
      item = new JMenuItem("Show sketch folder");
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          PdeBase.openFolder(sketchDir);
        }
      });
      menu.add(item);
    }

    // TODO re-enable history
    //history.attachMenu(menu);
    return menu;
  }


  protected JMenu buildHelpMenu() {
    JMenu menu = new JMenu("Help");
    JMenuItem item;

    item = new JMenuItem("Help");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          PdeBase.openURL(System.getProperty("user.dir") + File.separator + 
                          "reference" + File.separator + "environment" +
                          File.separator + "index.html");
        }
      });
    menu.add(item);

    item = new JMenuItem("Reference");
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          PdeBase.openURL(System.getProperty("user.dir") + File.separator + 
                          "reference" + File.separator + "index.html");
        }
      });
    menu.add(item);
    item = newJMenuItem("Find in Reference", 'F', true);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) { 
          if (textarea.isSelectionActive()) {
            String text = textarea.getSelectedText();
            if (text.length() == 0) {
              message("First select a word to find in the reference.");

            } else {
              String referenceFile = PdeKeywords.getReference(text);
              if (referenceFile == null) {
                message("No reference available for \"" + text + "\"");
              } else {
                PdeBase.showReference(referenceFile);
              }
            }
          }
        }
      });
    menu.add(item);

    item = newJMenuItem("Visit processing.org", '5');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          PdeBase.openURL("http://processing.org/");
        }
      });
    menu.add(item);

    // macosx already has its own about menu
    if (PdeBase.platform != PdeBase.MACOSX) {
      menu.addSeparator();
      item = new JMenuItem("About Processing");
      item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            handleAbout();
          }
        });
    }

    return menu;
  }


  public JMenu buildEditMenu() {
    JMenu menu = new JMenu("Edit");
    JMenuItem item; 

    undoItem = newJMenuItem("Undo", 'Z');
    undoItem.addActionListener(undoAction = new UndoAction());
    menu.add(undoItem);

    redoItem = newJMenuItem("Redo", 'Y');
    redoItem.addActionListener(redoAction = new RedoAction());
    menu.add(redoItem);

    menu.addSeparator();

    // TODO "cut" and "copy" should really only be enabled 
    // if some text is currently selected
    item = newJMenuItem("Cut", 'X');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.cut();
        }
      });
    menu.add(item);

    item = newJMenuItem("Copy", 'C');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.copy();
        }
      });
    menu.add(item);

    item = newJMenuItem("Paste", 'V');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.paste();
        }
      });
    menu.add(item);

    item = newJMenuItem("Select All", 'A');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.selectAll();
        }
      });
    menu.add(item);

    beautifyMenuItem = newJMenuItem("Beautify", 'B');
    beautifyMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doBeautify();
        }
      });
    menu.add(beautifyMenuItem);

    menu.addSeparator();

    item = newJMenuItem("Find...", 'F');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (find == null) { 
            find = new PdeEditorFind(PdeEditor.this);
          } else {
            find.show();
          }
        }
      });
    menu.add(item);

    item = newJMenuItem("Find Next", 'G');
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (find != null) find.find();
        }
      });
    menu.add(item);

    return menu;
  }


  /**
   * Convenience method for the antidote to overthought 
   * swing api mess for setting accelerators. 
   */
  static public JMenuItem newJMenuItem(String title, char what) {
    return newJMenuItem(title, what, false);
  }


  /**
   * A software engineer, somewhere, needs to have his abstraction 
   * taken away. I hear they jail people in third world countries for
   * writing the sort of crappy api that would require a four line
   * helpher function to *set the command key* for a menu item. 
   */
  static public JMenuItem newJMenuItem(String title, 
                                       char what, boolean shift) {
    JMenuItem menuItem = new JMenuItem(title);
    int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    if (shift) modifiers |= ActionEvent.SHIFT_MASK;
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, modifiers));
    return menuItem;
  }


  // ...................................................................


  // This one listens for edits that can be undone.
  protected class PdeUndoableEditListener implements UndoableEditListener {
    public void undoableEditHappened(UndoableEditEvent e) {
      //Remember the edit and update the menus.
      undo.addEdit(e.getEdit());
      undoAction.updateUndoState();
      redoAction.updateRedoState();
      //System.out.println("setting sketch to modified");
      //if (!editor.sketchModified) editor.setSketchModified(true);
    }
  }


  class UndoAction extends AbstractAction {
    public UndoAction() {
      super("Undo");
      this.setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
      try {
        undo.undo();
      } catch (CannotUndoException ex) {
        //System.out.println("Unable to undo: " + ex);
        //ex.printStackTrace();
      }
      updateUndoState();
      redoAction.updateRedoState();
    }

    protected void updateUndoState() {
      if (undo.canUndo()) {
        this.setEnabled(true);
        undoItem.setEnabled(true);
        putValue(Action.NAME, undo.getUndoPresentationName());
      } else {
        this.setEnabled(false);
        undoItem.setEnabled(false);
        putValue(Action.NAME, "Undo");
      }
    }      
  }    


  class RedoAction extends AbstractAction {
    public RedoAction() {
      super("Redo");
      this.setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
      try {
        undo.redo();
      } catch (CannotRedoException ex) {
        //System.out.println("Unable to redo: " + ex);
        //ex.printStackTrace();
      }
      updateRedoState();
      undoAction.updateUndoState();
    }

    protected void updateRedoState() {
      if (undo.canRedo()) {
        this.setEnabled(true);
        redoItem.setEnabled(true);
        putValue(Action.NAME, undo.getRedoPresentationName());
      } else {
        this.setEnabled(false);
        redoItem.setEnabled(false);
        putValue(Action.NAME, "Redo");
      }
    }
  }    


  // ...................................................................


  // interfaces for MRJ Handlers, but naming is fine 
  // so used internally for everything else

  public void handleAbout() {
    //System.out.println("the about box will now be shown");
    final Image image = PdeBase.getImage("about.jpg", this);
    int w = image.getWidth(this);
    int h = image.getHeight(this);
    final Window window = new Window(this) {
        public void paint(Graphics g) {
          g.drawImage(image, 0, 0, null);

          /*
            // does nothing..
          Graphics2D g2 = (Graphics2D) g;
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                              RenderingHints.VALUE_ANTIALIAS_OFF);
          */

          g.setFont(new Font("SansSerif", Font.PLAIN, 11));
          g.setColor(Color.white);
          g.drawString(PdeBase.VERSION, 50, 30);
        }
      };
    window.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          window.dispose();
        }
      });
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    window.setBounds((screen.width-w)/2, (screen.height-h)/2, w, h);
    window.show();
  }


  /**
   * Show the (already created on app init) preferences window.
   */
  public void handlePrefs() {
    // make sure this blocks until finished
    preferences.showFrame();

    // may need to rebuild sketch and other menus
    applyPreferences();

    // next have editor do its thing
    //editor.appyPreferences();
  }


  /** 
   * Quit, but first ask user if it's ok. Also store preferences
   * to disk just in case they want to quit. Final exit() happens 
   * in PdeEditor since it has the callback from PdeEditorStatus.
   */
  public void handleQuit() {
    // check to see if the person actually wants to quit
    doQuit();
  }


  // ...................................................................


  /**
   * Get the contents of the current buffer. Used by the Sketch class.
   */
  public String getText() {
    return textarea.getText();
  }


  /**
   * Called by PdeEditorHeader when the tab is changed 
   * (or a new set of files are opened)
   */
  public void changeText(String what, boolean emptyUndo) {
    textarea.setText(what);

    // TODO need to wipe out the undo state here
    if (emptyUndo) undo.discardAllEdits();

    textarea.select(0, 0);    // move to the beginning of the document
    textarea.requestFocus();  // get the caret blinking
  }


  public void doRun(boolean present) {
    doClose();
    running = true;
    buttons.run();

    // spew some blank lines so it's clear what's new on the console
    for (int i = 0; i < 10; i++) System.out.println();

    presenting = present;
    if (presenting) {
      // wipe everything out with a bulbous screen-covering window 
      presentationWindow.show();
      presentationWindow.toFront();
    }

    try {
      sketch.run();

    } catch (PdeException e) {
      error(e);

    } catch (Exception e) {
      e.printStackTrace();
    }
    sketch.cleanup();
  }


  public void handleStop() {  // called by menu or buttons
    if (presenting) {
      doClose();
    } else {
      doStop();
    }
  }


  /**
   * Stop the applet but don't kill its window.
   */
  public void doStop() {
    if (runtime != null) runtime.stop();
    if (watcher != null) watcher.stop();
    message(EMPTY);

    // the buttons are sometimes still null during the constructor
    // is this still true? are people still hitting this error?
    /*if (buttons != null)*/ buttons.clear();

    running = false;
  }


  /**
   * Stop the applet and kill its window. When running in presentation
   * mode, this will always be called instead of doStop().
   */
  public void doClose() {
    if (presenting) {
      presentationWindow.hide();

    } else {
      try {
        // the window will also be null the process was running 
        // externally. so don't even try setting if window is null
        // since PdeRuntime will set the appletLocation when an
        // external process is in use.
        if (runtime.window != null) {
          appletLocation = runtime.window.getLocation();
        }
      } catch (NullPointerException e) { }
    }

    if (running) {
      doStop();
    }

    try {
      if (runtime != null) {
        runtime.close();  // kills the window
        runtime = null; // will this help?
      }
    } catch (Exception e) { }
    //buttons.clear();  // done by doStop

    sketch.cleanup();

    // toxi_030903: focus the PDE again after quitting presentation mode
    toFront();
  }


  public void setSketchModified(boolean what) {
    header.sketchModified = what;
    header.repaint();
    sketchModified = what;
  }


  // check to see if there have been changes
  // if so, prompt user whether or not to save first
  // if the user cancels, return false to abort parent operation
  protected void checkModified(int checking) {
    checkModified(checking, null, null);
  }

  protected void checkModified(int checking, String path, String name) {
    this.checking = checking;
    openingPath = path;
    openingName = name;

    if (sketchModified) {
      String prompt = "Save changes to " + sketch.name + "?  ";

      if (checking == DO_QUIT) {
        int result = 0;

        // macosx java kills the app even though cancel might get hit
        // so the cancel button is (temporarily) left off
        // this may be treated differently in macosx java 1.4, 
        // but 1.4 isn't currently stable enough to use.

        // turns out windows has the same problem (sometimes)
        // disable cancel for now until a fix can be found.

        Object[] options = { "Yes", "No" };
        result = JOptionPane.showOptionDialog(this,
                                              prompt,
                                              "Quit",
                                              JOptionPane.YES_NO_OPTION,
                                              JOptionPane.QUESTION_MESSAGE,
                                              null,
                                              options, 
                                              options[0]);  // default to save

          /*
      } else {
        Object[] options = { "Yes", "No", "Cancel" };
        result = JOptionPane.showOptionDialog(this,
                                              prompt,
                                              "Quit",
                                              JOptionPane.YES_NO_CANCEL_OPTION,
                                              JOptionPane.QUESTION_MESSAGE,
                                              null,
                                              options, 
                                              options[2]);
          */

        if (result == JOptionPane.YES_OPTION) {
          //System.out.println("yes");
          //System.out.println("saving");
          doSave();
          //System.out.println("done saving");
          checkModified2();

        } else if (result == JOptionPane.NO_OPTION) {
          //System.out.println("no");
          checkModified2();  // though this may just quit

        } else if (result == JOptionPane.CANCEL_OPTION) {
          //System.out.println("cancel");
          // does nothing
        }

      } else {  // not quitting
        status.prompt(prompt);
      }

    } else {
      checkModified2();
    }
    //System.out.println("exiting checkmodified");
  }

  public void checkModified2() {
    switch (checking) {
    case SK_NEW: skNew2(false); break;
    case SK_OPEN: skOpen2(openingPath, openingName); break;
    case DO_QUIT: doQuit2(); break;
    }
    checking = 0;
  }


  // local vars prevent sketchName from being set
  public void skNew() {
    doStop();
    checkModified(SK_NEW);
  }


  /**
   * Does all the plumbing to create a new project
   * then calls handleOpen to load it up.
   * @param startup true if the app is starting (auto-create a sketch)
   */
  protected void skNew2(boolean startup) {
    try {
      File newbieDir = null;
      String newbieName = null;

      if (PdePreferences.getBoolean("sketchbook.prompt") && !startup) {
        // prompt for the filename and location for the new sketch

        FileDialog fd = new FileDialog(new Frame(), 
                                       "Create new sketch named", 
                                       FileDialog.SAVE);
        fd.setDirectory(PdePreferences.get("sketchbook.path"));
        fd.show();

        String newbieParentDir = fd.getDirectory();
        newbieName = fd.getFile();
        if (newbieName == null) return;

        newbieDir = new File(newbieParentDir, newbieName);

      } else {
        // use a generic name like sketch_031008a, the date plus a char
        String newbieParentDir = PdePreferences.get("sketchbook.path");

        int index = 0;
        SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd");
        String purty = formatter.format(new Date());
        do {
          newbieName = "sketch_" + purty + ((char) ('a' + index));
          newbieDir = new File(newbieParentDir, newbieName);
          index++;
        } while (newbieDir.exists());
      }

      // mkdir for new project name
      newbieDir.mkdirs();

      //new File(sketchDir, "data").mkdirs();

      // make empty pde file
      File newbieFile = new File(newbieDir, newbieName + ".pde");
      new FileOutputStream(newbieFile);

#ifdef MACOS
      // thank you apple, for changing this @#$)(*
      //com.apple.eio.setFileTypeAndCreator(String filename, int, int);

      // jdk13 on osx, or jdk11
      // though apparently still available for 1.4
      if ((PdeBase.platform == PdeBase.MACOS9) ||
          (PdeBase.platform == PdeBase.MACOSX)) {
        MRJFileUtils.setFileTypeAndCreator(newbieFile,
                                           MRJOSType.kTypeTEXT,
                                           new MRJOSType("Pde1"));
      }
#endif

      // make 'data' 'applet' dirs inside that
      // actually, don't, that way can avoid too much extra mess

      // rebuild the menu here
      sketchbook.rebuildMenu();

      // now open it up
      handleOpen(newbieName, newbieFile, newbieDir);

    } catch (IOException e) {
      // NEED TO DO SOME ERROR REPORTING HERE ***
      e.printStackTrace();
    }
  }


  public void skOpen(String path, String name) {
    doStop();
    checkModified(SK_OPEN, path, name);
  }

  protected void skOpen2(String path, String name) {
    File osketchFile = new File(path, name + ".pde");
    File osketchDir = new File(path);
    handleOpen(name, osketchFile, osketchDir);
  }


  /*
  protected void doOpen2() {
    // at least set the default dir here to the sketchbook folder

    FileDialog fd = new FileDialog(new Frame(), 
                                   "Open a PDE program...", 
                                   FileDialog.LOAD);
    if (sketchFile != null) {
      fd.setDirectory(sketchFile.getPath());
    }
    fd.show();

    String directory = fd.getDirectory();
    String filename = fd.getFile();
    if (filename == null) {
      buttons.clear();
      return; // user cancelled
    }

    handleOpen(filename, new File(directory, filename), null);
  }
  */


  protected void handleOpen(String isketchName, 
                            File isketchFile, File isketchDir) {
    if (!isketchFile.exists()) {
      status.error("no file named " + isketchName);
      return;
    }

    try {
      String program = null;

      if (isketchFile.length() != 0) {
        FileInputStream input = new FileInputStream(isketchFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuffer buffer = new StringBuffer();
        String line = null;
        while ((line = reader.readLine()) != null) {
          buffer.append(line);
          buffer.append('\n');
        }
        program = buffer.toString();
        changeText(program, true);

      } else {
        changeText("", true);
      }

      sketch.name = isketchName;
      sketch.file = isketchFile;
      sketch.directory = isketchDir;
      setSketchModified(false);

      /*
        // TODO re-enable history
      history.setPath(sketchFile.getParent(), readOnlySketch());
      history.rebuildMenu();
      history.lastRecorded = program;
      */

      header.reset();

      presentLocation = null;
      appletLocation = null;

    } catch (FileNotFoundException e1) {
      e1.printStackTrace();

    } catch (IOException e2) {
      e2.printStackTrace();
    }
    buttons.clear();
  }


  public void doSave() {
    // true if lastfile not set, otherwise false, meaning no prompt
    //handleSave(lastFile == null);
    // actually, this will always be false...
    handleSave(sketchName == null);
  }

  public void doSaveAs() {
    handleSave(true);
  }

  protected void handleSave(boolean promptUser) {
    message("Saving file...");
    String s = textarea.getText();

    String directory = sketchFile.getParent(); //lastDirectory;
    String filename = sketchFile.getName(); //lastFile;

    if (promptUser) {
      FileDialog fd = new FileDialog(new Frame(), 
                                     "Save PDE program as...", 
                                     FileDialog.SAVE);
      fd.setDirectory(directory);
      fd.setFile(filename);
      fd.show();

      directory = fd.getDirectory();
      filename = fd.getFile();
      if (filename == null) {
        message(EMPTY);
        buttons.clear();
        return; // user cancelled
      }
    }

    // TODO re-enable history
    //history.record(s, PdeHistory.SAVE);

    File file = new File(directory, filename);
    try {
      //System.out.println("handleSave: results of getText");
      //System.out.print(s);
 
      BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())));

      PrintWriter writer = 
        new PrintWriter(new BufferedWriter(new FileWriter(file)));

      String line = null;
      while ((line = reader.readLine()) != null) {
        //System.out.println("w '" + line + "'");
        writer.println(line);
      }
      writer.flush();
      writer.close();

      sketchFile = file;
      setSketchModified(false);
      message("Done saving " + filename + ".");

    } catch (IOException e) {
      e.printStackTrace();
      //message("Did not write file.");
      message("Could not write " + filename + ".");
    }
    buttons.clear();
  }


  public void skSaveAs(boolean rename) {
    doStop();

    this.renaming = rename;
    if (rename) {
      status.edit("Rename sketch to...", sketchName);
    } else {
      status.edit("Save sketch as...", sketchName);
    }
  }

  public void skSaveAs2(String newSketchName) {
    if (newSketchName.equals(sketchName)) {
      // nothing changes
      return;
    }

    File newSketchDir = new File(sketchDir.getParent() +
                                 File.separator + newSketchName);
    File newSketchFile = new File(newSketchDir, newSketchName + ".pde");

    //doSave(); // save changes before renaming.. risky but oh well
    String textareaContents = textarea.getText();
    int textareaPosition = textarea.getCaretPosition();

    // if same name, but different case, just use renameTo
    if (newSketchName.toLowerCase().
        equals(sketchName.toLowerCase())) {
      //System.out.println("using renameTo");

      boolean problem = (sketchDir.renameTo(newSketchDir) || 
                         sketchFile.renameTo(newSketchFile));
      if (problem) {
        status.error("Error while trying to re-save the sketch.");
      }

    } else {
      // make new dir
      newSketchDir.mkdirs();
      // copy the sketch file itself with new name
      PdeBase.copyFile(sketchFile, newSketchFile);

      // copy everything from the old dir to the new one
      PdeBase.copyDir(sketchDir, newSketchDir);

      // remove the old sketch file from the new dir
      new File(newSketchDir, sketchName + ".pde").delete();

      // remove the old dir (!)
      if (renaming) {
        // in case java is holding on to any files we want to delete
        System.gc();
        PdeBase.removeDir(sketchDir);
      }

      // (important!) has to be done before opening, 
      // otherwise the new dir is set to sketchDir.. 
      // remove .jar, .class, and .java files from the applet dir
      File appletDir = new File(newSketchDir, "applet");
      File oldjar = new File(appletDir, sketchName + ".jar");
      if (oldjar.exists()) oldjar.delete();
      File oldjava = new File(appletDir, sketchName + ".java");
      if (oldjava.exists()) oldjava.delete();
      File oldclass = new File(appletDir, sketchName + ".class");
      if (oldclass.exists()) oldclass.delete();
    }

    // get the changes into the sketchbook menu
    //base.rebuildSketchbookMenu();
    sketchbook.rebuildMenu();

    // open the new guy
    handleOpen(newSketchName, newSketchFile, newSketchDir);

    // update with the new junk and save that as the new code
    changeText(textareaContents, true);
    textarea.setCaretPosition(textareaPosition);
    doSave();
  }


  /*
  public void skExport() {
    doStop();
    message("Exporting for the web...");
    File appletDir = new File(sketchDir, "applet");
    handleExport(appletDir, sketchName, new File(sketchDir, "data"));
  }
  */


  /*
  public void doExport() {
    message("Exporting for the web...");
    String s = textarea.getText();
    FileDialog fd = new FileDialog(new Frame(), 
                                   "Create applet project named...", 
                                   FileDialog.SAVE);

    String directory = sketchFile.getPath(); //lastDirectory;
    String project = sketchFile.getName(); //lastFile;

    fd.setDirectory(directory);
    fd.setFile(project);
    fd.show();

    directory = fd.getDirectory();
    project = fd.getFile();
    if (project == null) {   // user cancelled
      message(EMPTY);
      buttons.clear();
      return;

    } else if (project.indexOf(' ') != -1) {  // space in filename
      message("Project name cannot have spaces.");
      buttons.clear();
      return;
    }
    handleExport(new File(directory), project, null);
  }
  */


  public void doPrint() {
    /*
    Frame frame = new Frame(); // bullocks
    int screenWidth = getToolkit().getScreenSize().width;
    frame.reshape(screenWidth + 20, 100, screenWidth + 100, 200);
    frame.show();

    Properties props = new Properties();
    PrintJob pj = getToolkit().getPrintJob(frame, "PDE", props);
    if (pj != null) {
      Graphics g = pj.getGraphics();
      // awful way to do printing, but sometimes brute force is
      // just the way. java printing across multiple platforms is
      // outrageously inconsistent.
      int offsetX = 100;
      int offsetY = 100;
      int index = 0;
      for (int y = 0; y < graphics.height; y++) {
        for (int x = 0; x < graphics.width; x++) {
          g.setColor(new Color(graphics.pixels[index++]));
          g.drawLine(offsetX + x, offsetY + y,
                     offsetX + x, offsetY + y);
        }
      }
      g.dispose();
      g = null;
      pj.end();
    }
    frame.dispose();
    buttons.clear();
    */
  }


  public void doQuit() {
    // stop isn't sufficient with external vm & quit
    // instead use doClose() which will kill the external vm
    //doStop();
    doClose();  

    //if (!checkModified()) return;
    checkModified(DO_QUIT);
    //System.out.println("exiting doquit");
  }


  protected void doQuit2() {
    storePreferences();
    preferences.save();

    sketchbook.clean();

    //System.out.println("exiting here");
    System.exit(0);
  }


  /*
  public void find() {
    if (find == null) { 
      find = new PdeEditorFind(this);
    } else {
      find.show();
    }
  }

  public void findNext() {
    if (find != null) find.find();
  }
  */


  public void doBeautify() {
    String prog = textarea.getText();

    // TODO re-enable history
    //history.record(prog, PdeHistory.BEAUTIFY);

    char program[] = prog.toCharArray();
    StringBuffer buffer = new StringBuffer();
    boolean gotBlankLine = false;
    int index = 0;
    int level = 0;

    while (index != program.length) {
      int begin = index;
      while ((program[index] != '\n') &&
             (program[index] != '\r')) {
        index++;
        if (program.length == index)
          break;
      }
      int end = index;
      if (index != program.length) {
        if ((index+1 != program.length) &&
            // treat \r\n from windows as one line
            (program[index] == '\r') && 
            (program[index+1] == '\n')) {
          index += 2;
        } else {
          index++;
        }                
      } // otherwise don't increment

      String line = new String(program, begin, end-begin);
      line = line.trim();
            
      if (line.length() == 0) {
        if (!gotBlankLine) {
          // let first blank line through
          buffer.append('\n');
          gotBlankLine = true;
        }
      } else {
        //System.out.println(level);
        int idx = -1;
        String myline = line.substring(0);
        while (myline.lastIndexOf('}') != idx) {
          idx = myline.indexOf('}');
          myline = myline.substring(idx+1);
          level--;
        }
        //for (int i = 0; i < level*2; i++) {
        for (int i = 0; i < level; i++) {
          buffer.append(' ');
        }
        buffer.append(line);
        buffer.append('\n');
        //if (line.charAt(0) == '{') {
        //level++;
        //}
        idx = -1;
        myline = line.substring(0);
        while (myline.lastIndexOf('{') != idx) {
          idx = myline.indexOf('{');
          myline = myline.substring(idx+1);
          level++;
        }
        gotBlankLine = false;
      }
    }

    // save current (rough) selection point
    int selectionEnd = textarea.getSelectionEnd();

    // replace with new bootiful text
    changeText(buffer.toString(), false);

    // make sure the caret would be past the end of the text
    if (buffer.length() < selectionEnd - 1) {
      selectionEnd = buffer.length() - 1;
    }

    // at least in the neighborhood
    textarea.select(selectionEnd, selectionEnd);

    setSketchModified(true);
    buttons.clear();
  }


  // TODO iron out bugs with this code under
  //      different platforms, especially macintosh
  public void highlightLine(int lnum) {
    if (lnum < 0) {
      textarea.select(0, 0);
      return;
    }
    //System.out.println(lnum);
    String s = textarea.getText();
    int len = s.length();
    int st = -1;
    int ii = 0;
    int end = -1;
    int lc = 0;
    if (lnum == 0) st = 0;
    for (int i = 0; i < len; i++) {
      ii++;
      //if ((s.charAt(i) == '\n') || (s.charAt(i) == '\r')) {
      boolean newline = false;
      if (s.charAt(i) == '\r') {
        if ((i != len-1) && (s.charAt(i+1) == '\n')) {
          i++; //ii--;
        }
        lc++;
        newline = true;
      } else if (s.charAt(i) == '\n') {
        lc++;
        newline = true;
      }
      if (newline) {
        if (lc == lnum)
          //st = i+1;
          st = ii;
        else if (lc == lnum+1) {
          //end = i;
          end = ii;
          break;
        }
      }
    }
    if (end == -1) end = len;

    // sometimes KJC claims that the line it found an error in is
    // the last line in the file + 1.  Just highlight the last line
    // in this case. [dmose]
    if (st == -1) st = len;

    textarea.select(st, end);
  }


  // ...................................................................


  public void error(PdeException e) {
    if (e.line >= 0) highlightLine(e.line); 

    status.error(e.getMessage());
    buttons.clearRun();
  }


  public void finished() {
    running = false;
    buttons.clearRun();
    message("Done.");
  }


  public void message(String msg) {
    status.notice(msg);
  }
  
  
  public void messageClear(String msg) {
    status.unnotice(msg);
  }


  // ...................................................................


  /**
   * Returns the edit popup menu.
   */  
  class TextAreaPopup extends JPopupMenu {
    //protected ReferenceKeys referenceItems = new ReferenceKeys();
    String currentDir = System.getProperty("user.dir");
    String referenceFile = null;

    JMenuItem cutItem, copyItem;
    JMenuItem referenceItem;


    public TextAreaPopup() {
      JMenuItem item;

      cutItem = new JMenuItem("Cut");
      cutItem.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            textarea.cut();
          }
      });
      this.add(cutItem);

      copyItem = new JMenuItem("Copy");
      copyItem.addActionListener(new ActionListener() {
	  public void actionPerformed(ActionEvent e) {
	    textarea.copy();
	  }
        });
      this.add(copyItem);

      item = new JMenuItem("Paste");
      item.addActionListener(new ActionListener() {
	  public void actionPerformed(ActionEvent e) {
	    textarea.paste();
	  }
        });
      this.add(item);

      item = new JMenuItem("Select All");
      item.addActionListener(new ActionListener() {
	public void actionPerformed(ActionEvent e) {
	  textarea.selectAll();
	}
      });
      this.add(item);

      this.addSeparator();

      referenceItem = new JMenuItem("Find in Reference");
      referenceItem.addActionListener(new ActionListener() {
	  public void actionPerformed(ActionEvent e) {
            PdeBase.showReference(referenceFile);
	  }
        });
      this.add(referenceItem);
    }

    // if no text is selected, disable copy and cut menu items
    public void show(Component component, int x, int y) {
      if (textarea.isSelectionActive()) {
        cutItem.setEnabled(true);
        copyItem.setEnabled(true);

        referenceFile = PdeKeywords.getReference(textarea.getSelectedText());
        if (referenceFile != null) {
          referenceItem.setEnabled(true);
        }
      } else {
        cutItem.setEnabled(false);
        copyItem.setEnabled(false);
        referenceItem.setEnabled(false);
      }
      super.show(component, x, y);
    }
  }
}

