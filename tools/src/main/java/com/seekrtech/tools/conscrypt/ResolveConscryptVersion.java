package com.seekrtech.tools.conscrypt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Resolves the upstream {@code org.conscrypt:conscrypt-openjdk-uber} version from the robolectric
 * POM. Robolectric declares conscrypt as a runtime dependency; the publish pipeline must publish
 * the arm64 conscrypt slice at that exact version so a substitution keeps the dependency graph
 * consistent.
 */
public final class ResolveConscryptVersion {

  private static final String GROUP_ID = "org.conscrypt";
  private static final String ARTIFACT_ID = "conscrypt-openjdk-uber";

  private ResolveConscryptVersion() {}

  public static void main(String[] args) {
    if (args.length == 2 && "--pom-url".equals(args[0])) {
      System.out.println(pomUrl(args[1]));
      return;
    }
    if (args.length == 1) {
      try {
        System.out.println(fromPomXml(Files.readString(Path.of(args[0]))));
        return;
      } catch (IOException e) {
        System.err.println("cannot read POM: " + e.getMessage());
        System.exit(1);
      }
    }
    System.err.println("usage: ResolveConscryptVersion <pom.xml> | --pom-url <robolectricVersion>");
    System.exit(2);
  }

  /** Extracts the conscrypt version from a robolectric POM; fails loudly if absent. */
  public static String fromPomXml(String pomXml) {
    try {
      DocumentBuilder builder = newBuilder();
      Element project = builder.parse(new ByteArrayInputStream(pomXml.getBytes(StandardCharsets.UTF_8)))
          .getDocumentElement();
      NodeList dependencies = project.getElementsByTagName("dependency");
      for (int i = 0; i < dependencies.getLength(); i++) {
        Element dependency = (Element) dependencies.item(i);
        if (!GROUP_ID.equals(childText(dependency, "groupId"))) {
          continue;
        }
        if (!ARTIFACT_ID.equals(childText(dependency, "artifactId"))) {
          continue;
        }
        String version = childText(dependency, "version");
        if (version != null && !version.isEmpty()) {
          return version;
        }
      }
      throw new IllegalStateException(
          "no " + GROUP_ID + ":" + ARTIFACT_ID + " dependency found in POM");
    } catch (SAXException | IOException e) {
      throw new IllegalStateException("failed to parse robolectric POM", e);
    }
  }

  /** Maven Central layout for the robolectric POM of a given robolectric version. */
  public static String pomUrl(String robolectricVersion) {
    return "https://repo1.maven.org/maven2/org/robolectric/robolectric/"
        + robolectricVersion
        + "/robolectric-"
        + robolectricVersion
        + ".pom";
  }

  private static DocumentBuilder newBuilder() {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      // Non-namespace-aware: POMs carry the Maven xmlns, but matching on local names only.
      factory.setNamespaceAware(false);
      return factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("cannot configure XML parser", e);
    }
  }

  private static String childText(Element parent, String childName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getNodeName())) {
        return child.getTextContent().trim();
      }
    }
    return null;
  }
}
