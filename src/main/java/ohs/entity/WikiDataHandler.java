package ohs.entity;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ohs.io.TextFileReader;
import ohs.io.TextFileWriter;
import ohs.lucene.common.IndexFieldName;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;

public class WikiDataHandler {
	public void extractRedirects() throws Exception {

		TextFileReader reader = new TextFileReader(ENTPath.KOREAN_WIKI_TEXT_FILE);
		TextFileWriter writer = new TextFileWriter(ENTPath.KOREAN_WIKI_REDIRECT_FILE);

		reader.setPrintNexts(false);

		MediaWikiParser parser = new MediaWikiParserFactory().createParser();

		String regex = "#REDIRECT \\[\\[([^\\[\\]]+)\\]\\]";
		Pattern p = Pattern.compile(regex);

		writer.write("FROM\tTO\n");

		while (reader.hasNext()) {
			reader.print(100000);
			String line = reader.next();
			String[] parts = line.split("\t");

			if (parts.length != 2) {
				continue;
			}
			String title = parts[0];
			String wikiText = parts[1].replace("<NL>", "\n").trim();

			Matcher m = p.matcher(wikiText);

			if (m.find()) {
				String redirect = m.group(1).trim();
				if (redirect.length() > 0) {
					writer.write(title + "\t" + redirect + "\n");
				}
			}
		}
		reader.printLast();
		reader.close();
		writer.close();

	}

	public static void main(String[] args) throws Exception {
		System.out.println("process begins.");

		WikiDataHandler dh = new WikiDataHandler();
		// dh.makeTextDump();
		dh.extractRedirects();

		System.out.println("process ends.");
	}

	public static String[] parse(String text) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder p1 = dbf.newDocumentBuilder();

		Document xmlDoc = p1.parse(new InputSource(new StringReader(text)));

		Element docElem = xmlDoc.getDocumentElement();

		String[] nodeNames = { "title", "text" };

		String[] values = new String[nodeNames.length];

		for (int j = 0; j < nodeNames.length; j++) {
			NodeList nodes = docElem.getElementsByTagName(nodeNames[j]);
			if (nodes.getLength() > 0) {
				values[j] = nodes.item(0).getTextContent().trim();
			}
		}
		return values;
	}

	public void makeTextDump() throws Exception {
		TextFileReader reader = new TextFileReader(ENTPath.KOREAN_WIKI_XML_FILE);
		TextFileWriter writer = new TextFileWriter(ENTPath.KOREAN_WIKI_TEXT_FILE);

		reader.setPrintNexts(false);

		StringBuffer sb = new StringBuffer();
		boolean isPage = false;
		int num_docs = 0;

		while (reader.hasNext()) {
			reader.print(100000);
			String line = reader.next();

			if (line.trim().startsWith("<page>")) {
				isPage = true;
				sb.append(line + "\n");
			} else if (line.trim().startsWith("</page>")) {
				sb.append(line);

				// System.out.println(sb.toString() + "\n\n");

				String[] values = parse(sb.toString());

				boolean isFilled = true;

				for (String v : values) {
					if (v == null) {
						isFilled = false;
						break;
					}
				}

				if (isFilled) {
					String title = values[0].trim();
					String wikiText = values[1].replaceAll("\n", "<NL>").trim();
					String output = String.format("%s\t%s", title, wikiText);
					writer.write(output + "\n");
				}

				sb = new StringBuffer();
				isPage = false;
			} else {
				if (isPage) {
					sb.append(line + "\n");
				}
			}
		}
		reader.printLast();
		reader.close();
		writer.close();

		System.out.printf("# of documents:%d\n", num_docs);

		// MediaWikiParserFactory pf = new MediaWikiParserFactory();
		// MediaWikiParser parser = pf.createParser();
		// ParsedPage pp = parser.parse(wikiText);
		//
		// ParsedPage pp2 = new ParsedPage();

		//
		// // get the sections
		// for (Section section : pp.getSections()) {
		// System.out.println("section : " + section.getTitle());
		// System.out.println(" nr of paragraphs      : " +
		// section.nrOfParagraphs());
		// System.out.println(" nr of tables          : " +
		// section.nrOfTables());
		// System.out.println(" nr of nested lists    : " +
		// section.nrOfNestedLists());
		// System.out.println(" nr of definition lists: " +
		// section.nrOfDefinitionLists());
		// }
	}
}