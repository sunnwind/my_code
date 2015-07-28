package ohs.medical.ir.trec.cds_2015;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ohs.io.IOUtils;
import ohs.io.TextFileWriter;
import ohs.ir.eval.Performance;
import ohs.ir.eval.PerformanceEvaluator;
import ohs.lucene.common.AnalyzerUtils;
import ohs.lucene.common.IndexFieldName;
import ohs.lucene.common.MedicalEnglishAnalyzer;
import ohs.math.ArrayMath;
import ohs.math.ArrayUtils;
import ohs.math.VectorMath;
import ohs.math.VectorUtils;
import ohs.matrix.DenseVector;
import ohs.matrix.SparseVector;
import ohs.matrix.Vector;
import ohs.medical.ir.BaseQuery;
import ohs.medical.ir.DocumentIdMapper;
import ohs.medical.ir.DocumentSearcher;
import ohs.medical.ir.HyperParameter;
import ohs.medical.ir.KLDivergenceScorer;
import ohs.medical.ir.MIRPath;
import ohs.medical.ir.QueryReader;
import ohs.medical.ir.RelevanceModelBuilder;
import ohs.medical.ir.RelevanceReader;
import ohs.medical.ir.WordCountBox;
import ohs.medical.ir.esa.ESA;
import ohs.types.Counter;
import ohs.types.Indexer;
import ohs.types.common.StrBidMap;
import ohs.types.common.StrCounterMap;
import ohs.utils.KoreanUtils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import edu.stanford.nlp.io.EncodingPrintWriter.out;

/**
 * 
 * @author Heung-Seon Oh
 * 
 */
public class TrecSearcher {

	public static void evalute() throws Exception {
		StrBidMap docIdMap = DocumentIdMapper.readDocumentIdMap(MIRPath.TREC_CDS_DOCUMENT_ID_MAP_FILE);
		StrCounterMap relevanceData = RelevanceReader.readTrecCdsRelevances(MIRPath.TREC_CDS_RELEVANCE_JUDGE_2014_FILE);

		List<File> files = IOUtils.getFilesUnder(MIRPath.TREC_CDS_OUTPUT_RESULT_2015_DIR);

		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < files.size(); i++) {
			File file = files.get(i);

			if (file.getName().contains("_log")) {
				continue;
			}

			StrCounterMap res = PerformanceEvaluator.readSearchResults(file.getPath());
			StrCounterMap resultData = DocumentIdMapper.mapIndexIdsToDocIds(res, docIdMap);

			PerformanceEvaluator eval = new PerformanceEvaluator();
			eval.setTopNs(new int[] { 10 });
			List<Performance> perfs = eval.evalute(resultData, relevanceData);

			System.out.println(file.getPath());
			sb.append(file.getPath());
			for (int j = 0; j < perfs.size(); j++) {
				sb.append("\n" + perfs.get(j).toString());
			}
			sb.append("\n\n");
		}

		IOUtils.write(MIRPath.TREC_CDS_OUTPUT_RESULT_2015_PERFORMANCE_FILE, sb.toString().trim());
	}

	public static void main(String[] args) throws Exception {
		System.out.println("process begins.");
		TrecSearcher tc = new TrecSearcher();
		// tc.searchByQLD();
		// tc.searchByKLD();
		tc.searchByKLDMultiFieldFB();
		// tc.searchByKLDProximityFB();
		// tc.searchByCBEEM();
		evalute();

		System.out.println("process ends.");
	}

	private List<BaseQuery> bqs;

	private IndexSearcher indexSearcher;

	private IndexReader indexReader;

	private Analyzer analyzer = MedicalEnglishAnalyzer.getAnalyzer();

	public TrecSearcher() throws Exception {
		bqs = QueryReader.readTrecCdsQueries(MIRPath.TREC_CDS_QUERY_2014_FILE);

		indexSearcher = DocumentSearcher.getIndexSearcher(MIRPath.TREC_CDS_INDEX_DIR);

		indexReader = indexSearcher.getIndexReader();
	}

	public void searchByCBEEM() throws Exception {
		System.out.println("search by CBEEM.");

		String[] indexDirNames = { MIRPath.TREC_CDS_INDEX_DIR, MIRPath.CLEF_EHEALTH_INDEX_DIR, MIRPath.OHSUMED_INDEX_DIR };

		String[] docPriorFileNames = MIRPath.DocPriorFileNames;

		String[] docMapFileNames = MIRPath.DocIdMapFileNames;

		IndexSearcher[] indexSearchers = DocumentSearcher.getIndexSearchers(indexDirNames);
		IndexSearcher wikiIndexSearcher = DocumentSearcher.getIndexSearcher(MIRPath.WIKI_INDEX_DIR);

		DenseVector[] docPriorData = new DenseVector[indexSearchers.length];

		for (int i = 0; i < indexDirNames.length; i++) {
			File inputFile = new File(docPriorFileNames[i]);
			DenseVector docPriors = null;
			if (inputFile.exists()) {
				docPriors = DenseVector.read(inputFile.getPath());
				double uniform_prior = 1f / docPriors.size();
				for (int j = 0; j < docPriors.size(); j++) {
					if (docPriors.value(j) == 0) {
						docPriors.set(j, uniform_prior);
					}
				}
			} else {
				docPriors = new DenseVector(indexSearchers[i].getIndexReader().maxDoc());
				double uniform_prior = 1f / docPriors.size();
				docPriors.setAll(uniform_prior);
			}
			docPriorData[i] = docPriors;
		}

		HyperParameter hyperParameter = new HyperParameter();
		hyperParameter.setTopK(1000);
		hyperParameter.setMixtureForAllCollections(0.5);
		// hyperParameter.setNumFBDocs(10);
		// hyperParameter.setNumFBWords(10);

		String resultFileName = MIRPath.TREC_CDS_OUTPUT_RESULT_2015_DIR + String.format("cbeem.txt");
		// String logFileName = logDirName
		// + String.format("cbeem_%s.txt", hyperParameter.toString(true));

		System.out.printf("process for [%s].\n", resultFileName);

		TrecCbeemDocumentSearcher ds = new TrecCbeemDocumentSearcher(indexSearchers, docPriorData, hyperParameter, analyzer,
				wikiIndexSearcher, false);
		ds.search(0, bqs, null, resultFileName, null);

	}

	public void searchByKLD() throws Exception {
		System.out.println("search by KLD.");

		String resultFileName = MIRPath.TREC_CDS_OUTPUT_RESULT_2015_DIR + "kld.txt";

		TextFileWriter writer = new TextFileWriter(resultFileName);

		for (int i = 0; i < bqs.size(); i++) {
			BaseQuery bq = bqs.get(i);
			System.out.println(bq);

			Counter<String> wordCounts = AnalyzerUtils.getWordCounts(bq.getSearchText(), analyzer);

			BooleanQuery lbq = AnalyzerUtils.getQuery(bq.getSearchText(), analyzer);

			SparseVector docScores = DocumentSearcher.search(lbq, indexSearcher, 1000);
			docScores.normalizeAfterSummation();

			Indexer<String> wordIndexer = new Indexer<String>();
			SparseVector queryModel = VectorUtils.toSparseVector(wordCounts, wordIndexer, true);
			queryModel.normalize();

			WordCountBox wcb = WordCountBox.getWordCountBox(indexSearcher.getIndexReader(), docScores, wordIndexer);

			KLDivergenceScorer scorer = new KLDivergenceScorer();
			docScores = scorer.scoreDocuments(wcb, queryModel);
			ResultWriter.write(writer, bq.getId(), docScores);
		}
		writer.close();
	}

	public void searchByKLDMultiFieldFB() throws Exception {
		System.out.println("search by KLD Multi-fields FB.");

		double[][] mixture_for_field_rms = { { 0, 0, 100 }, { 0, 100, 0 }, { 100, 0, 0 }, { 50, 50, 50 }, { 50, 30, 20 }, { 20, 30, 50 } };
		int[] num_fb_iters = { 1, 2 };
		double[] mixture_for_fb_model = { 0.5 };
		int[] num_fb_docs = { 5, 10, 15 };
		int[] num_fb_words = { 10, 15, 20 };

		for (int l1 = 0; l1 < num_fb_iters.length; l1++) {
			for (int l2 = 0; l2 < mixture_for_fb_model.length; l2++) {
				for (int l3 = 0; l3 < mixture_for_field_rms.length; l3++) {
					for (int l4 = 0; l4 < num_fb_docs.length; l4++) {
						for (int l5 = 0; l5 < num_fb_docs.length; l5++) {
							run(num_fb_iters[l1], mixture_for_fb_model[l2], mixture_for_field_rms[l3], num_fb_docs[l4], num_fb_words[l5]);
						}
					}
				}
			}
		}
	}

	public void run(int num_fb_iters, double mixture_for_fb_model, double[] mixtures_for_field_rms, int num_fb_docs, int num_fb_words)
			throws Exception {
		IndexSearcher wikiIndexSearcher = DocumentSearcher.getIndexSearcher(MIRPath.WIKI_INDEX_DIR);
		String outputFileName = null;

		{
			StringBuffer sb = new StringBuffer("kld_fb");
			sb.append(String.format("_%d", num_fb_iters));
			sb.append(String.format("_%s", mixture_for_fb_model + ""));

			for (int i = 0; i < mixtures_for_field_rms.length; i++) {
				sb.append(String.format("_%d", (int) mixtures_for_field_rms[i]));
			}

			sb.append(String.format("_%d", num_fb_docs));
			sb.append(String.format("_%d", num_fb_words));
			sb.append(".txt");
			outputFileName = MIRPath.TREC_CDS_OUTPUT_RESULT_2015_DIR + sb.toString();
		}

		System.out.println(outputFileName);

		mixtures_for_field_rms = ArrayUtils.copy(mixtures_for_field_rms);
		ArrayMath.normalize(mixtures_for_field_rms);

		TextFileWriter writer = new TextFileWriter(outputFileName);

		for (int i = 0; i < bqs.size(); i++) {
			BaseQuery bq = bqs.get(i);

			Indexer<String> wordIndexer = new Indexer<String>();
			StringBuffer qBuf = new StringBuffer(bq.getSearchText());
			Counter<String> qWordCounts = AnalyzerUtils.getWordCounts(qBuf.toString(), analyzer);

			SparseVector queryModel = VectorUtils.toSparseVector(qWordCounts, wordIndexer, true);
			queryModel.normalize();

			SparseVector expQueryModel = queryModel.copy();
			SparseVector docScores = null;

			for (int j = 0; j < 1; j++) {
				BooleanQuery lbq = AnalyzerUtils.getQuery(VectorUtils.toCounter(expQueryModel, wordIndexer));
				docScores = DocumentSearcher.search(lbq, indexSearcher, 1000);

				// SparseVector wikiScores = DocumentSearcher.search(lbq, wikiIndexSearcher, 50);

				WordCountBox wcb1 = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.TITLE);
				WordCountBox wcb2 = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.ABSTRACT);
				WordCountBox wcb3 = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.CONTENT);
				// WordCountBox wcb4 = WordCountBox.getWordCountBox(wikiIndexSearcher.getIndexReader(), wikiScores, wordIndexer,
				// IndexFieldName.CONTENT);

				// KLDivergenceScorer kldScorer = new KLDivergenceScorer();
				// docScores = kldScorer.scoreDocuments(wcb3, expQueryModel);

				RelevanceModelBuilder rmb = new RelevanceModelBuilder(num_fb_docs, num_fb_words, 2000);
				SparseVector rm1 = rmb.getRelevanceModel(wcb1, docScores);
				SparseVector rm2 = rmb.getRelevanceModel(wcb2, docScores);
				SparseVector rm3 = rmb.getRelevanceModel(wcb3, docScores);
				// SparseVector rm4 = rmb.getRelevanceModel(wcb4, wikiScores);

				SparseVector rm = VectorMath.addAfterScale(new Vector[] { rm1, rm2, rm3 }, mixtures_for_field_rms);
				rm.removeZeros();
				rm.normalize();

				expQueryModel = VectorMath.addAfterScale(queryModel, rm, 1 - mixture_for_fb_model, mixture_for_fb_model);
			}

			BooleanQuery lbq = AnalyzerUtils.getQuery(VectorUtils.toCounter(expQueryModel, wordIndexer));
			docScores = DocumentSearcher.search(lbq, indexSearcher, 1000);

			WordCountBox wcb = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.CONTENT);

			KLDivergenceScorer kldScorer = new KLDivergenceScorer();
			docScores = kldScorer.scoreDocuments(wcb, expQueryModel);

			// System.out.println(bq);
			// System.out.printf("QM1:\t%s\n", VectorUtils.toCounter(queryModel, wordIndexer));
			// System.out.printf("QM2:\t%s\n", VectorUtils.toCounter(expQueryModel, wordIndexer));
			// System.out.printf("RM1:\t%s\n", VectorUtils.toCounter(rm1, wordIndexer));
			// System.out.printf("RM2:\t%s\n", VectorUtils.toCounter(rm2, wordIndexer));
			// System.out.printf("RM3:\t%s\n", VectorUtils.toCounter(rm3, wordIndexer));
			// System.out.printf("RM4:\t%s\n", VectorUtils.toCounter(rm4, wordIndexer));
			// System.out.printf("RM:\t%s\n", VectorUtils.toCounter(rm, wordIndexer));
			// System.out.println();

			ResultWriter.write(writer, bq.getId(), docScores);
		}
		writer.close();
	}

	public void searchByKLDProximityFB() throws Exception {
		System.out.println("search by KLD Proximity FB.");

		IndexSearcher wikiIndexSearcher = DocumentSearcher.getIndexSearcher(MIRPath.WIKI_INDEX_DIR);

		String resultFileName = MIRPath.TREC_CDS_OUTPUT_RESULT_2015_DIR + "kld_fb_proximity.txt";

		TextFileWriter writer = new TextFileWriter(resultFileName);

		for (int i = 0; i < bqs.size(); i++) {
			BaseQuery bq = bqs.get(i);

			Indexer<String> wordIndexer = new Indexer<String>();
			StringBuffer qBuf = new StringBuffer(bq.getSearchText());
			Counter<String> qWordCounts = AnalyzerUtils.getWordCounts(qBuf.toString(), analyzer);

			SparseVector queryModel = VectorUtils.toSparseVector(qWordCounts, wordIndexer, true);
			queryModel.normalize();

			SparseVector expQueryModel = queryModel.copy();
			SparseVector docScores = null;

			for (int j = 0; j < 1; j++) {
				BooleanQuery lbq = AnalyzerUtils.getQuery(VectorUtils.toCounter(expQueryModel, wordIndexer));
				docScores = DocumentSearcher.search(lbq, indexSearcher, 1000);

				// SparseVector wikiScores = DocumentSearcher.search(lbq, wikiIndexSearcher, 50);

				WordCountBox wcb1 = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.TITLE);
				WordCountBox wcb2 = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.ABSTRACT);
				WordCountBox wcb3 = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.CONTENT);

				// WordCountBox wcb4 = WordCountBox.getWordCountBox(wikiIndexSearcher.getIndexReader(), wikiScores, wordIndexer,
				// IndexFieldName.CONTENT);

				// KLDivergenceScorer kldScorer = new KLDivergenceScorer();
				// docScores = kldScorer.scoreDocuments(wcb3, expQueryModel);

				ProximityRelevanceModelBuilder rmb = new ProximityRelevanceModelBuilder(wordIndexer, 10, 15, 2000, 3, false);
				rmb.computeWordProximities(wcb1, expQueryModel);
				SparseVector rm1 = rmb.getRelevanceModel(wcb1, docScores);

				rmb.computeWordProximities(wcb2, expQueryModel);
				SparseVector rm2 = rmb.getRelevanceModel(wcb2, docScores);

				rmb.computeWordProximities(wcb3, expQueryModel);
				SparseVector rm3 = rmb.getRelevanceModel(wcb3, docScores);

				// SparseVector rm4 = rmb.getRelevanceModel(wcb4, wikiScores);

				double mixture = 0.5;

				double[] mixtures = { 50, 50, 50 };

				ArrayMath.normalize(mixtures);

				SparseVector rm = VectorMath.addAfterScale(new Vector[] { rm1, rm2, rm3 }, mixtures);
				rm.removeZeros();
				rm.normalize();

				expQueryModel = VectorMath.addAfterScale(queryModel, rm, 1 - mixture, mixture);
			}

			BooleanQuery lbq = AnalyzerUtils.getQuery(VectorUtils.toCounter(expQueryModel, wordIndexer));
			docScores = DocumentSearcher.search(lbq, indexSearcher, 1000);

			WordCountBox wcb = WordCountBox.getWordCountBox(indexReader, docScores, wordIndexer, IndexFieldName.CONTENT);

			KLDivergenceScorer kldScorer = new KLDivergenceScorer();
			docScores = kldScorer.scoreDocuments(wcb, expQueryModel);

			System.out.println(bq);
			System.out.printf("QM1:\t%s\n", VectorUtils.toCounter(queryModel, wordIndexer));
			System.out.printf("QM2:\t%s\n", VectorUtils.toCounter(expQueryModel, wordIndexer));
			// System.out.printf("RM1:\t%s\n", VectorUtils.toCounter(rm1, wordIndexer));
			// System.out.printf("RM2:\t%s\n", VectorUtils.toCounter(rm2, wordIndexer));
			// System.out.printf("RM3:\t%s\n", VectorUtils.toCounter(rm3, wordIndexer));
			// System.out.printf("RM4:\t%s\n", VectorUtils.toCounter(rm4, wordIndexer));
			// System.out.printf("RM:\t%s\n", VectorUtils.toCounter(rm, wordIndexer));
			// System.out.println();

			ResultWriter.write(writer, bq.getId(), docScores);
		}
		writer.close();
	}

	public void searchByQLD() throws Exception {
		System.out.println("search by QLD.");

		TextFileWriter writer = new TextFileWriter(MIRPath.TREC_CDS_OUTPUT_RESULT_2015_DIR + "qld.txt");

		for (int i = 0; i < bqs.size(); i++) {
			BaseQuery bq = bqs.get(i);
			BooleanQuery lbq = AnalyzerUtils.getQuery(bq.getSearchText(), analyzer);
			SparseVector docScores = DocumentSearcher.search(lbq, indexSearcher, 1000);
			ResultWriter.write(writer, bq.getId(), docScores);
		}
		writer.close();
	}

}
