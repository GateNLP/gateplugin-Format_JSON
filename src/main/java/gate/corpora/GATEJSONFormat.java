/*
 *  Copyright (c) 1995-2018, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *  
 *  $Id: JSONTweetFormat.java 19779 2016-11-24 10:18:32Z markagreenwood $
 */
package gate.corpora;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.activation.MimeTypeParseException;

import org.apache.commons.lang.StringUtils;

import gate.DocumentContent;
import gate.GateConstants;
import gate.Resource;
import gate.corpora.json.PreAnnotation;
import gate.corpora.json.JSONUtils;
import gate.corpora.json.JSONDocument;
import gate.corpora.json.JSONDocumentStreamIterator;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleResource;
import gate.util.DocumentFormatException;
import gate.util.InvalidOffsetException;
import gate.util.Out;


/** Document format for handling JSON documnts: either one 
 *  object {...} or a list [{document...}, {document...}, ...].
 *  
 *  This format produces one GATE document from one JSON file.
 */
@CreoleResource(name = "GATE JSON Document Format", isPrivate = true,
    autoinstances = {@AutoInstance(hidden = true)},
    comment = "Format parser for Twitter JSON files",
    helpURL = "http://gate.ac.uk/userguide/sec:social:twitter:format")

public class GATEJSONFormat extends TextualDocumentFormat {
  private static final long serialVersionUID = 6878020036304333918L;

  
  /** Default construction */
  public GATEJSONFormat() { super();}

  /** Initialise this resource, and return it. */
  public Resource init() throws ResourceInstantiationException{
    // Register ad hoc MIME-type
    // There is an application/json mime type, but I don't think
    // we want everything to be handled this way?
    MimeType mime = new MimeType("text","json");
    // Register the class handler for this MIME-type
    mimeString2ClassHandlerMap.put(mime.getType()+ "/" + mime.getSubtype(), this);
    // Register the mime type with string
    mimeString2mimeTypeMap.put(mime.getType() + "/" + mime.getSubtype(), mime);
    // Register file suffixes for this mime type
    suffixes2mimeTypeMap.put("json", mime);
    // Register magic numbers for this mime type
    //magic2mimeTypeMap.put("Subject:",mime);
    // Set the mimeType for this language resource
    setMimeType(mime);
    return this;
  }
  
  @Override
  public void cleanup() {
    super.cleanup();
    
    MimeType mime = getMimeType();
    
    mimeString2ClassHandlerMap.remove(mime.getType()+ "/" + mime.getSubtype());
    mimeString2mimeTypeMap.remove(mime.getType() + "/" + mime.getSubtype());
    suffixes2mimeTypeMap.remove("json");
  }

  @Override
  public void unpackMarkup(gate.Document doc) throws DocumentFormatException{
    if ( (doc == null) || (doc.getSourceUrl() == null && doc.getContent() == null) ) {
      throw new DocumentFormatException("GATE document is null or no content found. Nothing to parse!");
    }
    
    String textPath = JSONUtils.DEFAULT_TEXT_ATTRIBUTE;
    
    if (doc instanceof DocumentImpl) {
      try {
        MimeType mimeType = new MimeType(((DocumentImpl)doc).getMimeType());
        String paramValue = mimeType.getParameterValue("text-path");
        
        if (paramValue != null && !paramValue.trim().isEmpty())
          textPath = paramValue;
        
      } catch(MimeTypeParseException | RuntimeException e) {
        e.printStackTrace();
      }
    }
    
    setNewLineProperty(doc);
    String jsonString = StringUtils.trimToEmpty(doc.getContent().toString());
    try {
      // Parse the String
      Iterator<JSONDocument> tweetSource = new JSONDocumentStreamIterator(jsonString, textPath);
      Map<JSONDocument, Long> tweetStarts = new LinkedHashMap<JSONDocument, Long>();
      
      // Put them all together to make the unpacked document content
      StringBuilder concatenation = new StringBuilder();
      while(tweetSource.hasNext()) {
        JSONDocument tweet = tweetSource.next();
        if(tweet != null) {
          // TweetStreamIterator can return null even when hasNext is true,
          // for search result style JSON.  This is not a problem, just ignore
          // and check hasNext again.
          tweetStarts.put(tweet, (long) concatenation.length());
          concatenation.append(tweet.getString()).append("\n\n");
        }
      }

      // Set new document content 
      DocumentContent newContent = new DocumentContentImpl(concatenation.toString());
      doc.edit(0L, doc.getContent().size(), newContent);

      // Create Original markups annotations for each tweet
      for (Map.Entry<JSONDocument, Long> entry: tweetStarts.entrySet()) {
        for (PreAnnotation preAnn : entry.getKey().getAnnotations()) {
          preAnn.toAnnotation(doc, entry.getValue());
        }
      }
    }
    catch (InvalidOffsetException | IOException | RuntimeException e) {
      doc.getFeatures().put("parsingError", Boolean.TRUE);

      Boolean bThrow =
              (Boolean)doc.getFeatures().get(
                      GateConstants.THROWEX_FORMAT_PROPERTY_NAME);

      if(bThrow != null && bThrow.booleanValue()) {
        // the next line is commented to avoid Document creation fail on
        // error
        throw new DocumentFormatException(e);
      }
      else {
        Out.println("Warning: Document remains unparsed. \n"
                + "\n  Stack Dump: ");
        e.printStackTrace(Out.getPrintWriter());
      } // if
    }
  }

}
