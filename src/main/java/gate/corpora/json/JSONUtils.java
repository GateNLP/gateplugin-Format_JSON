/*
 *  Copyright (c) 1995-2018, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 3, June 2007 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 */
package gate.corpora.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import gate.Factory;
import gate.FeatureMap;

/* REFERENCES
 * Jackson API
 * http://wiki.fasterxml.com/JacksonHome
 * Standard: RFC 4627
 * https://tools.ietf.org/html/rfc4627
 * */

public class JSONUtils  {
  
  public static final String PATH_SEPARATOR = ":";
  public static final String DEFAULT_ENCODING = "UTF-8";
  public static final String ANNOTATION_TYPE = "Object";
  public static final String DEFAULT_TEXT_ATTRIBUTE = "text";
  public static final String ENTITIES_ATTRIBUTE = "entities";
  
  public static List<JSONDocument> readJSONObjects(String string, String textPath) throws IOException {
    if (string.startsWith("[")) {
      return readJSONObjectList(string, textPath);
    }
  
    // implied else
    return readJSONObjectLines(string, textPath);
  }
 
  public static List<JSONDocument>readJSONObjectLines(String string, String textPath) throws IOException {
    String[] lines = string.split("[\\n\\r]+");
    return readJSONObjectStrings(lines, textPath);
  }
  

  public static List<JSONDocument>readJSONObjectStrings(String[] lines, String textPath) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    List<JSONDocument> tweets = new ArrayList<JSONDocument>();
    
    for (String line : lines) {
      if (line.length() > 0) {
        JsonNode jnode = mapper.readTree(line);
        tweets.add(new JSONDocument(jnode,textPath,true));
      }
    }
    
    return tweets;
  }

  
  public static List<JSONDocument>readJSONObjectStrings(List<String> lines, String textPath) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    List<JSONDocument> tweets = new ArrayList<JSONDocument>();
    
    for (String line : lines) {
      if (line.length() > 0) {
        JsonNode jnode = mapper.readTree(line);
        tweets.add(new JSONDocument(jnode, textPath, true));
      }
    }
    
    return tweets;
  }

  
  public static List<JSONDocument> readJSONObjectList(String string, String textPath) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    List<JSONDocument> tweets = new ArrayList<JSONDocument>();
    ArrayNode jarray = (ArrayNode) mapper.readTree(string);
    for (JsonNode jnode : jarray) {
      tweets.add(new JSONDocument(jnode, textPath, true));
    }
    return tweets;
  }


  public static Object process(JsonNode node) {
    /* JSON types: number, string, boolean, array, object (dict/map),
     * null.  All map keys are strings.
     */

    if (node.isBoolean()) {
      return node.asBoolean();
    }
    if (node.isIntegralNumber()) {
      // use Long even if the number is representable as an Integer,
      // since Long is better supported in JAPE etc.
      if(node.canConvertToLong()) {
        return node.asLong();
      } else {
        return node.bigIntegerValue();
      }
    }
    if (node.isNumber()) {
      // fractional number, as integers would have been caught by
      // the previous test.  The numberValue will be a Double
      // unless the parser was specifically configured to use
      // BigDecimal instead
      return node.numberValue();
    }
    if (node.isTextual()) {
      return node.asText();
    }
      
    if (node.isNull()) {
      return null;
    }
    
    if (node.isArray()) {
      List<Object> list = new ArrayList<Object>();
      for (JsonNode item : node) {
        list.add(process(item));
      }
      return list;
    }

    if (node.isObject()) {
      FeatureMap map = Factory.newFeatureMap();
      Iterator<String> keys = node.fieldNames();
      while (keys.hasNext()) {
        String key = keys.next();
        map.put(key, process(node.get(key)));
      }
      return map;
    }

    return node.toString();
  }

  

  public static FeatureMap process(JsonNode node, List<String> keepers) {
    FeatureMap found = Factory.newFeatureMap();
    for (String keeper : keepers) {
      String[] keySequence = StringUtils.split(keeper, PATH_SEPARATOR);
      Object value = dig(node, keySequence, 0);
      if (value != null) {
        found.put(keeper, value);
      }
    }
    return found;
  }
  
  
  /**
   * Dig through a JSON object, key-by-key (recursively).
   * @param node
   * @param keySequence
   * @return the value held by the last key in the sequence; this will
   * be a FeatureMap if there is further nesting
   */
  public static Object dig(JsonNode node, String[] keySequence, int index) {
    if ( (index >= keySequence.length) || (node == null) ) {
      return null;
    }
    
    if (node.has(keySequence[index])) {
      JsonNode value = node.get(keySequence[index]); 
      if (keySequence.length == (index + 1)) {
        // Found last key in sequence; convert the JsonNode
        // value to a normal object (possibly FeatureMap)
        return process(value);
      }
      else if (value != null){
        // Found current key; keep digging for the rest
        return dig(value, keySequence, index + 1);
      }
    }
    
    return null;
  }

  

}
