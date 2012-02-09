/* LanguageTool, a natural language style checker 
 * Copyright (C) 2006 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.dev;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.language.Contributor;
import org.languagetool.tools.LanguageIdentifierTools;
import org.languagetool.tools.StringTools;
import org.languagetool.tools.Tools;
import org.apache.tika.language.LanguageIdentifier;

/**
 * Command line tool to list supported languages and their number of rules.
 * 
 * @author Daniel Naber
 */
public final class RuleOverview {

  public static void main(final String[] args) throws IOException {
    final RuleOverview prg = new RuleOverview();
    prg.run();
  }
  
  private RuleOverview() {
    // no public constructor
  }
  
  private void run() throws IOException {
    System.out.println("<b>Rules in LanguageTool " + JLanguageTool.VERSION + "</b><br />");
    System.out.println("Date: " + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "<br /><br />\n");
    System.out.println("<table class=\"tablesorter sortable\">");
    System.out.println("<thead>");
    System.out.println("<tr>");
    System.out.println("  <th valign='bottom' width=\"70\">Language</th>");
    System.out.println("  <th valign='bottom' align=\"left\" width=\"60\">XML<br/>rules</th>");
    System.out.println("  <th></th>");
    System.out.println("  <th align=\"left\" width=\"60\">Java<br/>rules</th>");
    System.out.println("  <th align=\"left\" width=\"60\">False<br/>friends</th>");
    System.out.println("  <th valign='bottom' width=\"65\">Auto-<br/>detected</th>");
    System.out.println("  <th valign='bottom' align=\"left\">Rule Maintainers</th>");
    System.out.println("</tr>");
    System.out.println("</thead>");
    System.out.println("<tbody>");
    final List<String> sortedLanguages = getSortedLanguages();

    //setup false friends counting
    final String falseFriendFile = JLanguageTool.getDataBroker().getRulesDir() + File.separator + "false-friends.xml";
    final URL falseFriendUrl = this.getClass().getResource(falseFriendFile);
    final String falseFriendRules = StringTools.readFile(Tools.getStream(falseFriendFile))
      .replaceAll("(?s)<!--.*?-->", "")
      .replaceAll("(?s)<rules.*?>", "");

    int overallJavaCount = 0;
    for (final String langName : sortedLanguages) {
      final Language lang = Language.getLanguageForName(langName);
      System.out.print("<tr>");
      final File webDir = new File("website", "www");
      final File langSpecificWebsite = new File(webDir, lang.getShortName());
      if (langSpecificWebsite.isDirectory()) {
        System.out.print("<td valign=\"top\"><a href=\"../" + lang.getShortName() + "/\">" + lang.getName() + "</a></td>");
      } else {
        System.out.print("<td valign=\"top\">" + lang.getName() + "</td>");
      }
      final String xmlFile = JLanguageTool.getDataBroker().getRulesDir() + File.separator + lang.getShortName() + File.separator + "grammar.xml";
      final URL url = this.getClass().getResource(xmlFile);    
      if (url == null) {
        System.out.println("<td valign=\"top\" align=\"right\">0</td>");
      } else {
        // count XML rules:
        String xmlRules = StringTools.readFile(Tools.getStream(xmlFile));
        xmlRules = xmlRules.replaceAll("(?s)<!--.*?-->", "");
        xmlRules = xmlRules.replaceAll("(?s)<rules.*?>", "");
        final int count = countXmlRules(xmlRules);
        final int countInRuleGroup = countXmlRuleGroupRules(xmlRules);
        System.out.print("<td valign=\"top\" align=\"right\">" + (count + countInRuleGroup) + "</td>");
        System.out.print("<td valign=\"top\" align=\"right\">" +
            "<a href=\"http://languagetool.svn.sourceforge.net/viewvc/languagetool/trunk/JLanguageTool/src/rules/" + lang.getShortName() + "/grammar.xml?content-type=text%2Fplain" +
            "\">show</a>/" +
            "<a href=\"http://community.languagetool.org/rule/list?lang=" +
            lang.getShortName() + "\">browse</a>" +
            "</td>");
      }

      // count Java rules:
      final File dir = new File("src/java/org/languagetool" + 
              JLanguageTool.getDataBroker().getRulesDir() + "/" + lang.getShortName());
      if (!dir.exists()) {
        System.out.print("<td valign=\"top\" align=\"right\">0</td>");
      } else {
        final File[] javaRules = dir.listFiles(new JavaFilter());
        final int javaCount = javaRules.length - 1;   // minus 1: one is always "<Language>Rule.java"
        System.out.print("<td valign=\"top\" align=\"right\">" + javaCount + "</td>");
        overallJavaCount++;
      }

      // false friends
      if (falseFriendUrl == null) {
        System.out.println("<td valign=\"top\" align=\"right\">0</td>");
      } else {
        final int count = countFalseFriendRules(falseFriendRules, lang);
        System.out.print("<td valign=\"top\" align=\"right\">" + count + "</td>");

        System.out.print("<td valign=\"top\">" + (isAutoDetected(lang.getShortName()) ? "yes" : "-") + "</td>");
        
        // maintainer information:
        final StringBuilder maintainerInfo = getMaintainerInfo(lang);
        System.out.print("<td valign=\"top\" align=\"left\">" + maintainerInfo.toString() + "</td>");
      }
      
      System.out.println("</tr>");    
    }
      
    if (overallJavaCount == 0) {
      throw new RuntimeException("No Java rules found");
    }

    System.out.println("</tbody>");
    System.out.println("</table>");
  }

  private List<String> getSortedLanguages() {
    final List<String> sortedLanguages = new ArrayList<String>();
    for (Language element : Language.LANGUAGES) {
      if (element == Language.DEMO) {
        continue;
      }
      sortedLanguages.add(element.getName());
    }
    Collections.sort(sortedLanguages);
    return sortedLanguages;
  }

  private int countXmlRules(String xmlRules) {
    int pos = 0;
    int count = 0;
    while (true) {
      pos = xmlRules.indexOf("<rule ", pos + 1);
      if (pos == -1) {
        break;
      }
      count++;
    }
    return count;
  }

  private int countXmlRuleGroupRules(String xmlRules) {
    int pos = 0;
    int countInRuleGroup = 0;
    while (true) {
      pos = xmlRules.indexOf("<rule>", pos + 1);
      if (pos == -1) {
        break;
      }
      countInRuleGroup++;
    }
    return countInRuleGroup;
  }

  private int countFalseFriendRules(String falseFriendRules, Language lang) {
    int pos = 0;
    int count = 0;
    while (true) {
      pos = falseFriendRules.indexOf("<pattern lang=\"" + lang.getShortName(), pos + 1);
      if (pos == -1) {
        break;
      }
      count++;
    }
    return count;
  }

  private StringBuilder getMaintainerInfo(Language lang) {
    final StringBuilder maintainerInfo = new StringBuilder();
    if (lang.getMaintainers() != null) {
      for (Contributor contributor : lang.getMaintainers()) {
        if (!StringTools.isEmpty(maintainerInfo.toString())) {
          maintainerInfo.append(", ");
        }
        if (contributor.getUrl() != null) {
          maintainerInfo.append("<a href=\"");
          maintainerInfo.append(contributor.getUrl());
          maintainerInfo.append("\">");
        }
        maintainerInfo.append(contributor.getName());
        if (contributor.getUrl() != null) {
          maintainerInfo.append("</a>");
        }
        if (contributor.getRemark() != null) {
          maintainerInfo.append("&nbsp;(" + contributor.getRemark() + ")");
        }
      }
    }
    return maintainerInfo;
  }

  private boolean isAutoDetected(String code) {
    if (LanguageIdentifier.getSupportedLanguages().contains(code)) {
      return true;
    }
    final Set<String> additionalCodes = new HashSet<String>(Arrays.asList(LanguageIdentifierTools.ADDITIONAL_LANGUAGES));
    if (additionalCodes.contains(code)) {
      return true;
    }
    return false;
  }

}

class JavaFilter implements FileFilter {

  public boolean accept(final File f) {
    if (f.getName().endsWith(".java")) {
      return true;
    }
    return false;
  }

}
