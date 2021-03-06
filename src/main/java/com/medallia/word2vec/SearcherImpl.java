package com.medallia.word2vec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Doubles;
import com.medallia.word2vec.Searcher.Match;
import com.medallia.word2vec.Searcher.SemanticDifference;
import com.medallia.word2vec.Searcher.UnknownWordException;
import com.medallia.word2vec.util.Pair;

/** Implementation of {@link Searcher} */
class SearcherImpl implements Searcher {
	/** Implementation of {@link Match} */
	private static class MatchImpl extends Pair<String, Double> implements Match {
		private MatchImpl(String first, Double second) {
			super(first, second);
		}

		@Override
		public double distance() {
			return second;
		}

		@Override
		public String match() {
			return first;
		}

		@Override
		public String toString() {
			return String.format("%s [%s]", first, second);
		}
	}

	private final NormalizedWord2VecModel model;

	private final Map<String, Integer> word2vectorOffset;

	SearcherImpl(final NormalizedWord2VecModel model) {
		this.model = model;

		word2vectorOffset = new HashMap<String, Integer>();

		for (int i = 0; i < model.vocab.size(); i++) {
			word2vectorOffset.put(model.vocab.get(i), i);
		}
	}

	SearcherImpl(final Word2VecModel model) {
		this(NormalizedWord2VecModel.fromWord2VecModel(model));
	}

	private double calculateDistance(double[] otherVec, double[] vec) {
		double d = 0;
		for (int i = 0; i < model.layerSize; i++)
			d += vec[i] * otherVec[i];
		return d;
	}

	@Override
	public boolean contains(String word) {
		return word2vectorOffset.containsKey(word);
	}

	@Override
	public double cosineDistance(String s1, String s2) throws UnknownWordException {
		return calculateDistance(getVector(s1), getVector(s2));
	}

	/**
	 * @return Vector difference from v1 to v2
	 */
	private double[] getDifference(double[] v1, double[] v2) {
		double[] diff = new double[model.layerSize];
		for (int i = 0; i < model.layerSize; i++)
			diff[i] = v1[i] - v2[i];
		return diff;
	}

	@Override
	public List<Match> getMatches(final double[] vec, int maxNumMatches) {
		return Match.ORDERING.greatestOf(Iterables.transform(model.vocab, new Function<String, Match>() {
			@Override
			public Match apply(String other) {
				double[] otherVec = getVectorOrNull(other);
				double d = calculateDistance(otherVec, vec);
				return new MatchImpl(other, d);
			}
		}), maxNumMatches);
	}

	@Override
	public List<Match> getMatches(String s, int maxNumMatches) throws UnknownWordException {
		return getMatches(getVector(s), maxNumMatches);
	}

	@Override
	public ImmutableList<Double> getRawVector(String word) throws UnknownWordException {
		return ImmutableList.copyOf(Doubles.asList(getVector(word)));
	}

	/**
	 * @return Vector for the given word
	 * @throws UnknownWordException
	 *             If word is not in the model's vocabulary
	 */
	public double[] getVector(String word) {
		double[] result = getVectorOrNull(word);
		if (result == null) {
			// throw new UnknownWordException(word);
			result = new double[0];
		}

		return result;
	}

	private double[] getVectorOrNull(final String word) {
		final Integer index = word2vectorOffset.get(word);
		if (index == null)
			return null;

		return model.vectors[index];
	}

	@Override
	public SemanticDifference similarity(String s1, String s2) throws UnknownWordException {
		double[] v1 = getVector(s1);
		double[] v2 = getVector(s2);
		final double[] diff = getDifference(v1, v2);

		return new SemanticDifference() {
			@Override
			public List<Match> getMatches(String word, int maxMatches) throws UnknownWordException {
				double[] target = getDifference(getVector(word), diff);
				return SearcherImpl.this.getMatches(target, maxMatches);
			}
		};
	}

	@Override
	public Word2VecModel getModel() {
		return model;
	}
}
