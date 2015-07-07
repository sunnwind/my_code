package ohs.lucene.common;

import java.util.ArrayList;
import java.util.List;

import ohs.types.Counter;
import ohs.types.common.StrCounter;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;

public class AnalyzerUtils {
	public static BooleanQuery getQuery(Counter<String> wordCounts) throws Exception {
		BooleanQuery ret = new BooleanQuery();
		List<String> words = wordCounts.getSortedKeys();
		for (int i = 0; i < words.size() && i < BooleanQuery.getMaxClauseCount(); i++) {
			String word = words.get(i);
			double cnt = wordCounts.getCount(word);
			TermQuery tq = new TermQuery(new Term(IndexFieldName.CONTENT, word));
			tq.setBoost((float) cnt);
			ret.add(tq, Occur.SHOULD);
		}
		return ret;
	}

	public static BooleanQuery getQuery(List<String> words) throws Exception {
		BooleanQuery ret = new BooleanQuery();
		for (int i = 0; i < words.size(); i++) {
			String word = words.get(i);
			TermQuery tq = new TermQuery(new Term(IndexFieldName.CONTENT, word));
			ret.add(tq, Occur.SHOULD);
		}
		return ret;
	}

	public static BooleanQuery getQuery(String text, Analyzer analyzer) throws Exception {
		BooleanQuery ret = new BooleanQuery();

		Counter<String> c = getWordCounts(text, analyzer);
		List<String> words = c.getSortedKeys();

		for (int i = 0; i < words.size() && i < BooleanQuery.getMaxClauseCount(); i++) {
			String word = words.get(i);
			double cnt = c.getProbability(word);
			TermQuery tq = new TermQuery(new Term(IndexFieldName.CONTENT, word));
			tq.setBoost((float) cnt);
			ret.add(tq, Occur.SHOULD);
		}
		return ret;
	}

	public static Counter<String> getWordCounts(String text, Analyzer analyzer) throws Exception {
		Counter<String> ret = new Counter<String>();

		TokenStream ts = analyzer.tokenStream(IndexFieldName.CONTENT, text);
		CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
		ts.reset();

		while (ts.incrementToken()) {
			String word = attr.toString();
			ret.incrementCount(word, 1);
		}
		ts.end();
		ts.close();
		return ret;
	}

	public static List<String> getWords(String text, Analyzer analyzer) throws Exception {
		TokenStream ts = analyzer.tokenStream(IndexFieldName.CONTENT, text);
		CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
		ts.reset();

		List<String> ret = new ArrayList<String>();
		while (ts.incrementToken()) {
			String word = attr.toString();
			ret.add(word);
		}
		ts.end();
		ts.close();
		return ret;
	}
}