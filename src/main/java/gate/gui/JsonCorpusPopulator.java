/*
 * Copyright (c) 1995-2018, The University of Sheffield. See the file
 * COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */

package gate.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import javax.activation.MimeTypeParseException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import gate.Corpus;
import gate.Document;
import gate.DocumentFormat;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.corpora.MimeType;
import gate.creole.Plugin;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleResource;
import gate.event.PluginListener;
import gate.swing.SpringUtilities;

@CreoleResource(name = "JSON Corpus Populator", tool = true, autoinstances = @AutoInstance)
public class JsonCorpusPopulator extends ResourceHelper
                                 implements PluginListener {

  private static final Logger logger =
      Logger.getLogger(JsonCorpusPopulator.class.getName());

  private static final long serialVersionUID = -1712269859711281005L;

  private JDialog dialog = null;

  private JComboBox<String> cboMimeType;

  private JTextField txtIDPath;

  private JFileChooser fileChooser;

  private int returnValue;

  private static final int ERROR = -1;

  private static final int CANCEL = 0;

  private static final int APPROVE = 1;

  private void buildGUI() {

    dialog = new JDialog(MainFrame.getInstance(), "Populate from JSON...",
        Dialog.DEFAULT_MODALITY_TYPE);
    dialog.getContentPane().setLayout(new BorderLayout());
    dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

    fileChooser = new JFileChooser();
    
    MainFrame.getGuiRoots().add(dialog);

    JPanel options = new JPanel(new SpringLayout());
    options.setBorder(new TitledBorder("Options:"));

    cboMimeType = new JComboBox<String>();
    updateMimeTypeList();
    Gate.getCreoleRegister().addPluginListener(this);

    cboMimeType.setEditable(true);
    txtIDPath = new JTextField();

    options.add(new JLabel("Mime Type:"));
    options.add(cboMimeType);

    options.add(new JLabel("Doc ID Field"));
    options.add(txtIDPath);

    SpringUtilities.makeCompactGrid(options, 2, 2, 5, 5, 3, 3);

    dialog.getContentPane().add(options, BorderLayout.NORTH);
    dialog.getContentPane().add(fileChooser, BorderLayout.CENTER);

    dialog.pack();

    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        returnValue = CANCEL;
      }
    });

    fileChooser.addActionListener((e) -> {
      if(JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {

        cboMimeType.getEditor().getEditorComponent()
            .setBackground(UIManager.getColor("TextField.background"));
        txtIDPath.setBackground(UIManager.getColor("TextField.background"));

        try {
          DocumentFormat format = DocumentFormat.getDocumentFormat(
              new MimeType(cboMimeType.getSelectedItem().toString()));
          if(format == null) {
            cboMimeType.requestFocusInWindow();
            cboMimeType.getEditor().getEditorComponent()
                .setBackground(new Color(255, 186, 186));
            System.err.println("no handler for mime type");
            return;
          }

        } catch(Exception ex) {
          cboMimeType.requestFocusInWindow();
          cboMimeType.getEditor().getEditorComponent()
              .setBackground(new Color(255, 186, 186));
          cboMimeType.setBackground(new Color(255, 186, 186));
          System.err.println("invalid mime type");
          return;
        }

        try {
          if(!txtIDPath.getText().trim().isEmpty()) {
            @SuppressWarnings("unused")
            JsonPointer idPointer =
                JsonPointer.compile(txtIDPath.getText().trim());
          }

        } catch(RuntimeException ex) {

          System.err.println("invalid JSON path to ID element");
          txtIDPath.requestFocusInWindow();
          txtIDPath.setBackground(new Color(255, 186, 186));
          txtIDPath.setSelectionEnd(txtIDPath.getText().length());
          txtIDPath.setSelectionStart(0);
          return;
        }

        returnValue = APPROVE;
      } else {
        returnValue = CANCEL;
      }

      dialog.setVisible(false);
    });
  }

  @Override
  protected List<Action> buildActions(NameBearerHandle handle) {
    List<Action> actions = new ArrayList<Action>();

    if(!(handle.getTarget() instanceof Corpus)) return actions;

    actions.add(new AbstractAction("Populate from JSON...") {

      private static final long serialVersionUID = -8992528339144621526L;

      @Override
      public void actionPerformed(ActionEvent event) {
        
        if (dialog == null) buildGUI();

        cboMimeType.getEditor().getEditorComponent()
            .setBackground(UIManager.getColor("TextField.background"));
        txtIDPath.setBackground(UIManager.getColor("TextField.background"));
        dialog.setLocationRelativeTo(dialog.getParent());

        returnValue = ERROR;

        dialog.setVisible(true);

        if(returnValue != APPROVE) return;

        final String mimeType = cboMimeType.getSelectedItem().toString().trim();
        final String idPath = txtIDPath.getText().trim();

		new Thread(() -> {
			try {

				try (InputStream in = new FileInputStream(fileChooser.getSelectedFile());
						GZIPInputStream gzin = new GZIPInputStream(in)) {
					populate((Corpus) handle.getTarget(), gzin, mimeType, idPath.isEmpty() ? null : idPath);
				} catch (ZipException zippy) {
					try (InputStream in = new FileInputStream(fileChooser.getSelectedFile())) {
						populate((Corpus) handle.getTarget(), in, mimeType, idPath.isEmpty() ? null : idPath);
					}
				}
			} catch (IOException bungle) {
				bungle.printStackTrace();
			}
		}).start();
      }
    });

    return actions;
  }

  public void populate(Corpus corpus, InputStream inputStream, String mimeType,
      String idPath) throws IOException {

    ObjectMapper objectMapper;

    JsonParser jsonParser;

    JsonPointer idPointer = null;

    if(idPath != null) idPointer = JsonPointer.compile(idPath);

    objectMapper = new ObjectMapper();
    jsonParser = objectMapper.getFactory().createParser(inputStream)
        .enable(Feature.AUTO_CLOSE_SOURCE);

    // If the first token in the stream is the start of an array ("[") then
    // assume the stream as a whole is an array of objects, one per document.
    // To handle this, simply clear the token - The MappingIterator returned by
    // readValues will cope with the rest in either form.
    if(jsonParser.nextToken() == JsonToken.START_ARRAY) {
      jsonParser.clearCurrentToken();
    }

    MappingIterator<JsonNode> docIterator =
        objectMapper.readValues(jsonParser, JsonNode.class);

    while(docIterator.hasNext()) {
      JsonNode json = docIterator.next();

      String docID = null;

      if(idPointer != null) {
        docID = json.at(idPointer).asText();
        if(docID == null || docID.trim().isEmpty()) continue;
      }

      FeatureMap docParams = Factory.newFeatureMap();
      docParams.put(Document.DOCUMENT_STRING_CONTENT_PARAMETER_NAME,
          json.toString());
      if(mimeType != null) {
        docParams.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, mimeType);
      }
      try {
        Document document =
            (Document)Factory.createResource("gate.corpora.DocumentImpl",
                docParams, Factory.newFeatureMap(), docID);

        corpus.add(document);

        if(corpus.getLRPersistenceId() != null) {
          corpus.unloadDocument(document);
          Factory.deleteResource(document);
        }
      } catch(Exception e) {
        // TODO should this be a warning or an exception etc.
        logger.warn("Failed to correctly parse JSON document", e);
      }
    }
  }

  @Override
  public void pluginLoaded(Plugin plugin) {
    updateMimeTypeList();
  }

  @Override
  public void pluginUnloaded(Plugin plugin) {
    updateMimeTypeList();
  }

  private void updateMimeTypeList() {
    DefaultComboBoxModel<String> model =
        (DefaultComboBoxModel<String>)cboMimeType.getModel();

    Object selected = cboMimeType.getSelectedItem();

    model.removeAllElements();

    Set<String> knownMimeTypes = new TreeSet<String>();

    for(String mimeType : DocumentFormat.getSupportedMimeTypes()) {
      if(mimeType.toLowerCase(Locale.ENGLISH).indexOf("json") != -1) {
        knownMimeTypes.add(mimeType);
      } else {
        try {
          MimeType mimeTypeObj = new MimeType(mimeType);

          DocumentFormat docFormat =
              DocumentFormat.getDocumentFormat(mimeTypeObj);

          if(docFormat != null && docFormat.getName()
              .toLowerCase(Locale.ENGLISH).indexOf("json") != -1) {
            knownMimeTypes.add(mimeType);
          }
        } catch(MimeTypeParseException e) {
          // should be impossible
        }
      }
    }

    for(String known : knownMimeTypes) {
      model.addElement(known);
    }

    if(selected != null && knownMimeTypes.contains(selected)) {
      cboMimeType.setSelectedItem(selected);
    }
  }
}
