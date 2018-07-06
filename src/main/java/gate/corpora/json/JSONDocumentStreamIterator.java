/*
 * Copyright (c) 1995-2014, The University of Sheffield. See the file
 * COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 * 
 * $Id: TweetStreamIterator.java 18420 2014-10-30 19:26:45Z ian_roberts $
 */
package gate.corpora.json;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONDocumentStreamIterator implements Iterator<JSONDocument> {

  private ObjectMapper objectMapper;

  private JsonParser jsonParser;

  private MappingIterator<JsonNode> iterator;

  private JsonNode nextNode;

  private boolean handleEntities;

  private String textPath;

  public JSONDocumentStreamIterator(String json, String textPath)
      throws JsonParseException, IOException {
    this(json, textPath, true);
  }

  public JSONDocumentStreamIterator(String json, String textPath,
      boolean handleEntities) throws JsonParseException, IOException {
    this.handleEntities = handleEntities;
    objectMapper = new ObjectMapper();
    jsonParser = objectMapper.getFactory().createParser(json);
    this.textPath = textPath;
    init();
  }

  public JSONDocumentStreamIterator(InputStream input, String textPath,
      boolean gzip) throws JsonParseException, IOException {
    this(input, textPath, gzip, true);
  }

  public JSONDocumentStreamIterator(InputStream input, String textPath,
      boolean gzip, boolean handleEntities)
      throws JsonParseException, IOException {
    this.handleEntities = handleEntities;
    this.textPath = textPath;

    InputStream workingInput;

    // Following borrowed from gcp JSONStreamingInputHandler
    objectMapper = new ObjectMapper();

    if(gzip) {
      workingInput = new GZIPInputStream(input);
    } else {
      workingInput = input;
    }

    jsonParser = objectMapper.getFactory().createParser(workingInput)
        .enable(Feature.AUTO_CLOSE_SOURCE);
    init();
  }

  private void init() throws JsonParseException, IOException {
    // If the first token in the stream is the start of an array ("[")
    // then assume the stream as a whole is an array of objects
    // To handle this, simply clear the token - The MappingIterator
    // returned by readValues will cope with the rest in either form.
    if(jsonParser.nextToken() == JsonToken.START_ARRAY) {
      jsonParser.clearCurrentToken();
    }
    iterator = objectMapper.readValues(jsonParser, JsonNode.class);
  }

  @Override
  public boolean hasNext() {
    return this.iterator.hasNext();
  }

  @Override
  public JSONDocument next() {
    JSONDocument result = null;

    if(this.iterator.hasNext()) {
      this.nextNode = this.iterator.next();
      result = new JSONDocument(nextNode, textPath, handleEntities);
    }

    return result;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException(
        "The JSON document stream is read-only.");
  }

  public void close() throws IOException {
    iterator.close();
    jsonParser.close();
  }

}
