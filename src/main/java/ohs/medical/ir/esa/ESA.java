package ohs.medical.ir.esa;

import java.io.BufferedReader;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;

import ohs.io.IOUtils;
import ohs.lucene.common.AnalyzerUtils;
import ohs.lucene.common.MedicalEnglishAnalyzer;
import ohs.math.VectorUtils;
import ohs.matrix.SparseMatrix;
import ohs.matrix.SparseVector;
import ohs.medical.ir.MIRPath;
import ohs.medical.ir.query.BaseQuery;
import ohs.medical.ir.query.QueryReader;
import ohs.types.Counter;
import ohs.types.Indexer;
import ohs.types.common.IntCounter;
import ohs.types.common.IntCounterMap;
import ohs.types.common.StrIndexer;
import ohs.utils.StrUtils;

public class ESA {

	public static void main(String[] args) throws Exception {

		ESA esa = new ESA(MedicalEnglishAnalyzer.getAnalyzer());
		esa.read(MIRPath.ICD10_ESA_FILE);

		List<BaseQuery> bqs = QueryReader.readTrecCdsQueries(MIRPath.TREC_CDS_QUERY_2015_A_FILE);

		for (int i = 0; i < bqs.size(); i++) {
			BaseQuery bq = bqs.get(i);
			System.out.println(bq.toString());
			Counter<String> c = esa.getConceptVectorAsCounter(bq.getSearchText());
			System.out.println(c.toStringSortedByValues(true, true, 20));
			System.out.println();
		}

	}

	private StrIndexer wordIndexer;

	private StrIndexer conceptIndexer;

	private SparseMatrix wordConceptWeights;

	private Analyzer analyzer;

	public ESA(Analyzer analyzer) throws Exception {
		this.analyzer = analyzer;
	}

	public Indexer<String> getConceptIndexer() {
		return conceptIndexer;
	}

	public SparseVector getConceptVector(Counter<String> c) {
		Counter<String> ret = new Counter<String>();

		SparseVector q = VectorUtils.toSparseVector(c, wordIndexer);
		IntCounter cc = new IntCounter();

		for (int i = 0; i < q.size(); i++) {
			int w = q.indexAtLoc(i);
			double prob = q.probAtLoc(i);
			SparseVector cws = wordConceptWeights.rowAlways(w);

			for (int j = 0; j < cws.size(); j++) {
				int cptId = cws.indexAtLoc(j);
				double weight = cws.valueAtLoc(j);
				cc.incrementCount(cptId, prob * weight);
			}
		}

		return VectorUtils.toSparseVector(cc);
	}

	public SparseVector getConceptVector(String s) {
		Counter<String> c = new Counter<String>();
		try {
			c = AnalyzerUtils.getWordCounts(s, analyzer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return getConceptVector(c);
	}

	public Counter<String> getConceptVectorAsCounter(String s) {
		return VectorUtils.toCounter(getConceptVector(s), conceptIndexer);
	}

	public Indexer<String> getWordIndexer() {
		return wordIndexer;
	}

	public void read(String esaModelFileName) throws Exception {
		wordIndexer = new StrIndexer();
		conceptIndexer = new StrIndexer();

		IntCounterMap cm = new IntCounterMap();

		// TextFileReader reader = new TextFileReader(esaModelFileName);

		BufferedReader reader = IOUtils.openBufferedReader(esaModelFileName);

		{
			String line = reader.readLine();
			String[] parts = line.split("\t");
			int num_read = Integer.parseInt(parts[1]);

			for (int i = 0; i < num_read; i++) {
				line = reader.readLine();
				conceptIndexer.add(line);
			}

		}

		{
			reader.readLine();
			String line = reader.readLine();
			String[] parts = line.split("\t");
			int num_read = Integer.parseInt(parts[1]);

			for (int i = 0; i < num_read; i++) {
				line = reader.readLine();
				wordIndexer.add(line);
			}
		}

		{
			reader.readLine();
			reader.readLine();
			int num_read = conceptIndexer.size();

			for (int i = 0; i < num_read; i++) {
				String line = reader.readLine();
				String[] parts = line.split("\t");

				IntCounter c = new IntCounter();

				int cpt = Integer.parseInt(parts[0]);

				for (int j = 1; j < parts.length; j++) {
					String[] two = StrUtils.split2Two(":", parts[j]);
					int w = Integer.parseInt(two[0]);
					double weight = Double.parseDouble(two[1]);
					c.setCount(cpt, weight);
				}
				cm.setCounter(cpt, c);
			}
		}

		reader.close();

		wordConceptWeights = VectorUtils.toSpasreMatrix(cm);

	}

}
