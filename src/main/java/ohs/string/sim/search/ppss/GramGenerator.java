package ohs.string.sim.search.ppss;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ohs.string.sim.search.ppss.Gram.Type;

/**
 * 
 * Generating q-gram from a given string
 * 
 * @author Heung-Seon Oh
 */
public class GramGenerator implements Serializable {

	public static List<Gram[]> generate(GramGenerator gramGenerator, List<StringRecord> ss) {
		StringSorter.sortByLength(ss);

		List<Gram[]> ret = new ArrayList<Gram[]>();
		for (int i = 0; i < ss.size(); i++) {
			String name = ss.get(i).getString();
			if (name.length() == 0 || name.length() < gramGenerator.getQ()) {
				continue;
			}
			ret.add(gramGenerator.generate(name));
		}
		return ret;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("process begins.");
		GramGenerator p = new GramGenerator(2);

		String[] entities = { "imyouteca", "ubuntucom", "utubbecou", "youtbecom", "yoytubeca" };
		for (int i = 0; i < entities.length; i++) {
			String r = entities[i];
			Gram[] grams = p.generate(r);

			System.out.println(r);
			System.out.println(GramUtils.toString(grams) + "\n");
		}
		System.out.println("process ends.");
	}

	/**
	 * size of q-grams
	 */
	private int q = 2;

	private boolean isCaseInsensitive = false;

	public GramGenerator(int q) {
		this.q = q;
	}

	public Gram[] generate(String s) {
		int len = s.length();
		int size = len - q + 1;

		Gram[] ret = null;

		if (len < q) {
			ret = new Gram[0];
		} else {
			ret = new Gram[size];
			for (int i = 0; i < len - q + 1; i++) {
				String substr = s.substring(i, i + q);
				ret[i] = new Gram(s.substring(i, i + q), i, Type.NONE);
			}
		}

		return ret;
	}

	public int getQ() {
		return q;
	}
}
