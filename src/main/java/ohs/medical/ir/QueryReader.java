package ohs.medical.ir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import ohs.io.IOUtils;
import ohs.io.TextFileReader;
import ohs.lucene.common.AnalyzerUtils;
import ohs.lucene.common.MedicalEnglishAnalyzer;
import ohs.types.Counter;
import ohs.types.common.StrCounterMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class QueryReader {

	public static void main(String[] args) throws Exception {
		String fileName = MIRPath.CLEF_EHEALTH_QUERY_2014_FILE;

		List<BaseQuery> bqs = readClefEHealthQueries(fileName, null);
		double cnt_sum = 0;
		for (int i = 0; i < bqs.size(); i++) {
			BaseQuery bq = bqs.get(i);
			System.out.println(bq);
			Counter<String> c = AnalyzerUtils.getWordCounts(bq.getSearchText(), MedicalEnglishAnalyzer.getAnalyzer());
			cnt_sum += c.totalCount();
			System.out.println(c.totalCount());
		}

		System.out.println(cnt_sum / bqs.size());

	}

	public static List<BaseQuery> readTrecCdsQueries(String fileName) throws Exception {
		List<BaseQuery> ret = new ArrayList<BaseQuery>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder parser = dbf.newDocumentBuilder();

		Document xmlDoc = parser.parse(new InputSource(new StringReader(IOUtils.readText(fileName))));

		Element docElem = xmlDoc.getDocumentElement();
		NodeList nodeList = docElem.getElementsByTagName("topic");

		String[] nodeNames = { "description", "summary" };

		for (int i = 0; i < nodeList.getLength(); i++) {
			Element topicElem = (Element) nodeList.item(i);

			String id = topicElem.getAttribute("number");
			String type = topicElem.getAttribute("type");

			String[] values = new String[nodeNames.length];

			values[0] = topicElem.getAttribute(nodeNames[0]);
			for (int j = 0; j < nodeNames.length; j++) {
				NodeList nodes = topicElem.getElementsByTagName(nodeNames[j]);
				if (nodes.getLength() > 0) {
					values[j] = nodes.item(0).getTextContent();
				}
			}

			String description = values[0];
			String summary = values[1];

			description = description.replace("&quot;", "\"");

			// id = new DecimalFormat("00").format(Integer.parseInt(id));

			TrecCdsQuery query = new TrecCdsQuery(id, description, summary, type);
			ret.add(query);
		}

		System.out.printf("read [%d] queries from [%s]\n", ret.size(), fileName);
		return ret;
	}

	public static List<BaseQuery> filter(List<BaseQuery> baseQueries, StrCounterMap relevanceData) {
		List<BaseQuery> ret = new ArrayList<BaseQuery>();
		for (int i = 0; i < baseQueries.size(); i++) {
			BaseQuery q = baseQueries.get(i);
			if (relevanceData.getCounter(q.getId()).size() > 0) {
				ret.add(q);
			}
		}
		System.out.printf("filter out queries which have no relevance judgements [%d -> %d].\n", baseQueries.size(), ret.size());
		return ret;
	}

	public static List<BaseQuery> readOhsumedQueries(String fileName) throws Exception {
		/*
		 * .I Sequential identifier
		 * 
		 * .B Patient information
		 * 
		 * .W Information request
		 */

		List<BaseQuery> ret = new ArrayList<BaseQuery>();
		Map<String, String> map = new HashMap<String, String>();
		TextFileReader reader = new TextFileReader(fileName);

		while (reader.hasNext()) {
			String line = reader.next();

			if (line.startsWith(".I")) {
				String[] parts = line.split("[\\s]+");
				String key = parts[0];
				String value = parts[1];

				if (map.size() == 0) {
					map.put(key, value);
				} else {
					String id = map.get(".I");
					String patientInfo = map.get(".B");
					String infoRequest = map.get(".W");

					ret.add(new OhsumedQuery(id, patientInfo, infoRequest));

					map = new HashMap<String, String>();
					map.put(key, value);
				}
			} else {
				reader.hasNext();
				String value = reader.next();
				map.put(line, value);
			}
		}
		reader.close();

		String id = map.get(".I");
		String patientInfo = map.get(".B");
		String infoRequest = map.get(".W");

		ret.add(new OhsumedQuery(id, patientInfo, infoRequest));

		System.out.printf("read [%d] queries from [%s]\n", ret.size(), fileName);

		return ret;
	}

	public static List<BaseQuery> readClefEHealthQueries(String queryFileName) throws Exception {
		return readClefEHealthQueries(queryFileName, null);
	}

	public static List<BaseQuery> readClefEHealthQueries(String queryFileName, String dischargeDirName) throws Exception {
		List<BaseQuery> ret = new ArrayList<BaseQuery>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder parser = dbf.newDocumentBuilder();

		Document xmlDoc = parser.parse(new InputSource(new StringReader(IOUtils.readText(queryFileName))));

		Element docElem = xmlDoc.getDocumentElement();
		NodeList nodeList = null;

		int year = 2013;

		if (queryFileName.contains("clef2014")) {
			year = 2014;
		} else if (queryFileName.contains("clef2015")) {
			year = 2015;
		}

		if (year == 2015) {
			nodeList = docElem.getElementsByTagName("top");

			String[] nodeNames = { "num", "query" };

			for (int i = 0; i < nodeList.getLength(); i++) {
				Element queryElem = (Element) nodeList.item(i);

				String[] values = new String[nodeNames.length];

				for (int j = 0; j < nodeNames.length; j++) {
					NodeList nodes = queryElem.getElementsByTagName(nodeNames[j]);
					if (nodes.getLength() > 0) {
						values[j] = nodes.item(0).getTextContent();
					}
				}

				String id = values[0];
				String dischargeFileName = "";
				String discharge = "";
				String title = "";
				String description = values[1];
				String profile = "";
				String narrative = "";

				ClefEHealthQuery cq = new ClefEHealthQuery(id, discharge, title, description, profile, narrative);
				ret.add(cq);
			}
		} else {
			if (year == 2013) {
				nodeList = docElem.getElementsByTagName("query");
			} else if (year == 2014) {
				nodeList = docElem.getElementsByTagName("topic");
			}

			Map<String, File> dischargeFileMap = new TreeMap<String, File>();

			if (dischargeDirName != null) {
				File[] files = new File(dischargeDirName).listFiles();
				for (File file : files) {
					dischargeFileMap.put(file.getName(), file);
				}
			}

			String[] nodeNames = { "id", "discharge_summary", "title", "desc", "profile", "narr" };

			for (int i = 0; i < nodeList.getLength(); i++) {
				Element queryElem = (Element) nodeList.item(i);

				String[] values = new String[nodeNames.length];

				values[0] = queryElem.getAttribute(nodeNames[0]);
				for (int j = 0; j < nodeNames.length; j++) {
					NodeList nodes = queryElem.getElementsByTagName(nodeNames[j]);
					if (nodes.getLength() > 0) {
						values[j] = nodes.item(0).getTextContent();
					}
				}

				String id = values[0];
				String dischargeFileName = values[1].trim();

				File dischargeFile = dischargeFileMap.get(dischargeFileName);
				String discharge = "";

				if (dischargeFile != null) {
					discharge = IOUtils.readText(dischargeFile.getPath());
				} else {
					new FileNotFoundException(dischargeFileName);
				}

				String title = values[2];
				String description = values[3];
				String profile = values[4];
				String narrative = values[5];

				ClefEHealthQuery cq = new ClefEHealthQuery(id, discharge, title, description, profile, narrative);
				ret.add(cq);
			}
		}

		System.out.printf("read [%d] queries from [%s]\n", ret.size(), queryFileName);

		return ret;
	}
}