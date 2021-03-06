package edu.cmu.cs.bungee.client.query;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.cmu.cs.bungee.client.query.Query.ItemList;
import edu.cmu.cs.bungee.javaExtensions.PerspectiveObserver;
import edu.cmu.cs.bungee.javaExtensions.Util;

/**
 * @author mad A little language for tagged sequences of query components, for
 *         generating natural language descriptions
 */
public interface Markup extends List<Object> {

	/**
	 * Add an 's' to the next token
	 */
	public static final Object PLURAL_TAG = new Character('s');

	/**
	 * insert a newline
	 */
	public static final Object NEWLINE_TAG = new Character('\n');

	/**
	 * underline subsequent tokens
	 */
	public static final Object UNDERLINE_TAG = new Character('u');

	/**
	 * Don't underline subsequent tokens
	 */
	public static final Object NO_UNDERLINE_TAG = new Character('n');

	/**
	 * render subsequent tokens in the default color
	 */
	public static final Object DEFAULT_COLOR_TAG = new Character('c');

	/**
	 * render subsequent tokens in the default text style
	 */
	public static final Object DEFAULT_STYLE_TAG = new Character('b');

	/**
	 * render subsequent tokens in italics
	 */
	public static final Object ITALIC_STRING_TAG = new Character('i');

	/**
	 * insert 'images' or 'works' or whatever, as specified in
	 * globals.genericObjectLabel
	 */
	public static final Object GENERIC_OBJECT_LABEL = "Generic Object Label";
	/**
	 * The '->' symbol that goes in front of rank labels
	 */
	public static final String parentIndicatorPrefix = "\u2192"; // '\u2023'

	static final Color INCLUDED_COLOR = new // Color(0xdfc27d);
	Color(0x00ff00); // Color(0xbd0000);

	static final Color POSITIVE_ASSOCIATION_COLOR = new // Color(0xa6611a);
	Color(0x509950); // Color(0xc15151);

	static final Color EXCLUDED_COLOR = new // Color(0x80cdc1);
	Color(0xac9200); // Color(0x4a0183);

	static final Color NEGATIVE_ASSOCIATION_COLOR = new // Color(0x018571);
	Color(0x8e784f); // Color(0x663e85);

	static final Color UNASSOCIATED_COLOR = new Color(0x707070);

	/**
	 * Colors used for facets significantly positively associated with the
	 * current filters
	 */
	public static final Color[] POSITIVE_ASSOCIATION_COLORS =
	// { new Color(0x003320),
	// new Color(0x006640), new Color(0x00BB70), new Color(0x00FF90) };
	{ POSITIVE_ASSOCIATION_COLOR,
			POSITIVE_ASSOCIATION_COLOR.brighter().brighter() };

	/**
	 * Colors used for facets significantly negatively associated with the
	 * current filters
	 */
	public static final Color[] NEGATIVE_ASSOCIATION_COLORS =
	// { new Color(0x660000),
	// new Color(0x660000), new Color(0xBB0000), new Color(0xFF0000) };
	{ NEGATIVE_ASSOCIATION_COLOR,
			NEGATIVE_ASSOCIATION_COLOR.brighter().brighter() };

	/**
	 * Colors used for facets in positive filters
	 */
	public static final Color[] INCLUDED_COLORS =
	// { new Color(0x003300),
	// new Color(0x006600), new Color(0x00BB00), new Color(0x00FF00) };
	{ INCLUDED_COLOR, new Color(0xc4ffc4) };

	/**
	 * Colors used for facets in negative filters
	 */
	public static final Color[] EXCLUDED_COLORS =
	// { new Color(0x330000),
	// new Color(0x660000), new Color(0xBB0000), new Color(0xFF0000) };
	{ EXCLUDED_COLOR, EXCLUDED_COLOR.brighter().brighter() };

	/**
	 * Colors used for facets not significantly associated with the current
	 * filters
	 */
	public static final Color[] UNASSOCIATED_COLORS =
	// { new Color(0x555555),
	// new Color(0x999999), new Color(0xFFFFFF) };
	{ UNASSOCIATED_COLOR, UNASSOCIATED_COLOR.brighter().brighter() };

	/**
	 * @param genericObjectLabel
	 *            what you call items, e.g. 'image' or 'work'
	 * @return a Markup ready to render
	 */
	public Markup compile(String genericObjectLabel);

	/**
	 * @return a copy of this markup.
	 */
	public Markup copy();

	/**
	 * @return this Markup rendered as a String
	 */
	public String toText();

	/**
	 * @param _redraw
	 *            callback object when any unknown facet names are read in
	 * @return this Markup rendered as a String
	 */
	public String toText(PerspectiveObserver _redraw);

	public Markup uncolor();
}

final class MarkupImplementation extends ArrayList<Object> implements Markup {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public Markup compile(String genericObjectLabel) {
		Markup v = new MarkupImplementation();
		Object prev = null;
		for (Iterator<Object> it = iterator(); it.hasNext();) {
			Object o = it.next();
			if (o == GENERIC_OBJECT_LABEL)
				o = genericObjectLabel;
			assert o != null : Util.valueOfDeep(this);
			boolean thisIsString = o instanceof String;
			if (thisIsString && prev == PLURAL_TAG) {
				int prevIndex = v.size() - 1;
				prev = Util.pluralize((String) o);
				v.set(prevIndex, prev);
			} else if (thisIsString && prev instanceof String) {
				// Util.print("merging " + v.get(i-1) + "-" + o);
				int prevIndex = v.size() - 1;
				prev = (String) v.get(prevIndex) + (String) o;
				v.set(prevIndex, prev);
			} else {
				v.add(o);
				prev = o;
			}
		}
		return v;
	}

	static Markup tagDescription(Markup[] restrictions, boolean doTag,
			String[] patterns, String tag) {
		// Util.print("tagDescription " + restrictions + " " +
		// Util.valueOfDeep(patterns));
		int nPolaritiesUsed = 0;
		Markup result = new MarkupImplementation();
		for (int polarity = 0; polarity < restrictions.length; polarity++) {
			if (restrictions[polarity] != null) {
				List<Object> polarityDesc = new LinkedList<Object>(restrictions[polarity]);
				if (polarityDesc.size() > 0) {
					nPolaritiesUsed++;
					String pattern = patterns[polarity];
					int i = pattern.indexOf('~');
					polarityDesc.add(0, Markup.DEFAULT_COLOR_TAG);
					if (i >= 0) {
						polarityDesc.add(0, pattern.substring(0, i));
						polarityDesc.add(pattern.substring(i + 1));
					} else {
						polarityDesc.add(0, pattern);
					}
					if (doTag && result.size() == 0) {
						result.add(tag);
						if (nPolaritiesUsed == 1 && polarity == 1
								&& tag.equals("object")) {
							// negative restrictions only
							result.add(GENERIC_OBJECT_LABEL);
							nPolaritiesUsed++;
						}
					}
					if (nPolaritiesUsed == 2) {
						polarityDesc.add(0, "but");
						polarityDesc.add(")");
					}
					if (polarity > 0)
						// There's too much green. Only color-emphasize the
						// non-default case.
						// There's still too much red - better to put the color
						// commands in the pattern (e.g. only around "don't"
						polarityDesc.add(0, Perspective.filterColors[polarity]);
					if (nPolaritiesUsed == 2) {
						polarityDesc.add(0, " (");
					}
					result.addAll(polarityDesc);
				}
			}
		}
		// Util.printDeep(patterns);
		// Util.printDeep(result);
		// Util.print("");
		return result;
	}

	// public static Markup description(Perspective[] restrictions) {
	// SortedMap parentalGroups = new TreeMap();
	// for (int i = 0; i < restrictions.length; i++) {
	// Perspective child = restrictions[i];
	// Perspective parent = child.getFacetType();
	// SortedSet children = (SortedSet) parentalGroups.get(parent);
	// if (children == null) {
	// children = new TreeSet();
	// parentalGroups.put(parent, children);
	// }
	// children.add(child);
	// }
	// Markup[] phrases = new Markup[0];
	// Iterator it = parentalGroups.entrySet().iterator();
	//
	// while (it.hasNext()) {
	// Map.Entry entry = (Entry) it.next();
	// Perspective parent = (Perspective) entry.getKey();
	// SortedSet children = (SortedSet) entry.getValue();
	// Perspective[] info = (Perspective[]) (children)
	// .toArray(new Perspective[0]);
	// Markup[] descriptions = new Markup[1];
	// Markup description = new MarkupImplementation();
	// toEnglish(info, " and ", description);
	// Markup result = parent.tagDescription(descriptions, true, null);
	// if (result.size() > 0)
	// phrases = (Markup[]) Util.push(phrases, result, Markup.class);
	// }
	// // Util.print("q.getPhrases return "
	// // + Util.valueOfDeep(phrases));
	//
	// Markup summary = new MarkupImplementation();
	// descriptionNounPhrase(phrases, summary);
	// descriptionClauses(phrases, summary, null, null);
	//
	// return summary;
	// }

	static void descriptionNounPhrase(List<Markup> phrases, Markup result) {
		// Util.print("descriptionNounPhrase '" + phrases + "' '" + result+"'");
		for (Iterator<Markup> it = phrases.iterator(); it.hasNext();) {
			Markup phrase = it.next();
			if (phrase.get(0).equals("object")) {
				for (int j = 1; j < phrase.size(); j++) {
					if (phrase.get(j) instanceof Perspective)
						result.add(Markup.PLURAL_TAG);
					result.add(phrase.get(j));
				}
			}
		}
		if (result.size() == 0) {
			result.add(Markup.GENERIC_OBJECT_LABEL);
		}
		// if (onCount != 1)
		// for (int i = 0; i < objects.size(); i++)
		// objects[i] = Util.pluralize(objects[i]);
		// result.add(Util.toEnglish(result, " and "));
		// Util.print(" descriptionNounPhrase: " + result);
	}

	static void descriptionClauses(List<Markup> phrases, Markup result, Set<String> searches,
			Set<Cluster> clusters, Set<ItemList> itemLists) {
		// Util.print("\nq.descriptionClauses "
		// + Util.valueOfDeep(phrases));
		// Util.printDeep(result);
		for (Iterator<Markup> it = phrases.iterator(); it.hasNext();) {
			Markup phrase = it.next();
			if (phrase.get(0).equals("meta")
					&& !topLevelFacetClause(phrase, result)) {
				// result.add(" ");
				for (int j = 1; j < phrase.size(); j++) {
					result.add(phrase.get(j));
					// if (j == 1)
					// result.add(" ");
				}
			}
		}
		boolean first = true;
		for (Iterator<Markup> it = phrases.iterator(); it.hasNext();) {
			Markup phrase = it.next();
			if (phrase.get(0).equals("content")
					&& !topLevelFacetClause(phrase, result)) {
				if (first) {
					// result.add(" that");
					first = false;
				} else
					result.add(" and");
				for (int j = 1; j < phrase.size(); j++) {
					result.add(phrase.get(j));
					// if (j == 1)
					// result.add(" ");
				}
			}
		}
		for (Iterator<String> it = searches.iterator(); it.hasNext();) {
			String search = it.next();
			String s;
			if (Util.nOccurrences(search, ' ') > 0)
				s = "whose description mentions one of the words '";
			else
				s = "whose description mentions '";
			if (first) {
				result.add(" ");
				first = false;
			} else
				result.add(" and ");
			result.add(s);
			result.add(Markup.INCLUDED_COLORS[0]);
			result.add(search);
			result.add(Markup.DEFAULT_COLOR_TAG);
			result.add("'");
		}
		for (Iterator<Cluster> it = clusters.iterator(); it.hasNext();) {
			Cluster cluster = it.next();
			String s;
			switch (cluster.nRestrictions()) {
			case 1:
				s = "that has the tag {";
				break;
			case 2:
				s = "that has both of the tags {";
				break;
			default:
				s = " that have at least " + cluster.quorumSize() + " of the "
						+ cluster.nRestrictions() + " tags {";
				break;
			}
			if (first) {
				result.add(" ");
				first = false;
			} else
				result.add(" and ");
			result.add(s);
			// result.add(Markup.greens[2]);
			toEnglish(cluster.allRestrictions(), ", ", result);
			// result.add(Markup.DEFAULT_COLOR_TAG);
			result.add("}");
		}
		for (Iterator<ItemList> it = itemLists.iterator(); it.hasNext();) {
			ItemList itemList = it.next();
			String s = "that match the Informedia query '" + itemList + "'";
			if (first) {
				result.add(" ");
				first = false;
			} else
				result.add(" and ");
			result.add(s);
		}
		// Util.print(phrases);
	}

	private static final Set<String> emptyStringSet = Collections
			.unmodifiableSet(new HashSet<String>());
	private static final Set<Cluster> emptyClusterSet = Collections
	.unmodifiableSet(new HashSet<Cluster>());
	private static final Set<ItemList> emptyItemListSet = Collections
	.unmodifiableSet(new HashSet<ItemList>());

	static Markup facetDescription(Perspective facet) {
		Markup[] descriptions = { new MarkupImplementation(), null };
		List<Markup> phrases = new LinkedList<Markup>();
		if (facet != null) {
			descriptions[0].add(facet);
			phrases.add(facet.tagDescription(descriptions, true, null));
		}
		Markup summary = new MarkupImplementation();
		// summary.add(" "); // descriptionNounPhrase assumes there is exactly
		// one canned prefix
		descriptionNounPhrase(phrases, summary);
		descriptionClauses(phrases, summary, emptyStringSet, emptyClusterSet, emptyItemListSet);
		return summary;
	}

	// static Markup clusterDescription(Cluster facet) {
	// return description(facet.allRestrictions());
	// }

	static Markup facetSetDescription(SortedSet<Perspective> facets) {
		Markup content = Query.emptyMarkup();
		Perspective aRestriction = facets.first();
		// Perspective parent = aRestriction.getParent();
		// String prefix = parent != null ? parent.namePrefix() : "";
		// if (prefix.length() > 0)
		content.add(aRestriction.namePrefix());
		Query.toEnglish(facets, " and ", content);

		Markup[] descriptions = new Markup[2];
		boolean[] reqtTypes = { true, false };
		for (int type = 0; type < 2; type++) {
			boolean reqtType = reqtTypes[type];

			SortedSet<Perspective> info = new TreeSet<Perspective>();
			for (Iterator<Perspective> it = facets.iterator(); it.hasNext();) {
				Perspective p = it.next();
				info.addAll(p.getRestrictionFacetInfos(true, reqtType));
			}

			if (!info.isEmpty()) {
				descriptions[type] = Query.emptyMarkup();
				Query.toEnglish(info, " or ", descriptions[type]);
			}
		}
		Markup result = aRestriction.tagDescription(descriptions, false, Util
				.splitSemicolon(" ; NOT "));

		if (result.size() > 0) {
			content.add(Markup.NEWLINE_TAG);
			content.add(aRestriction.namePrefix());
			content.addAll(result);
		}
		return content;
	}

	private static boolean topLevelFacetClause(Markup phrase, Markup result) {
		for (int j = 1; j < phrase.size(); j++) {
			Object o = phrase.get(j);
			if (o instanceof Perspective) {
				Perspective facet = (Perspective) o;
				if (facet.getParent() == null) {
					result
							.add(" having"
									+ Util.indefiniteArticle(facet
											.getNameIfPossible()));
					result.add(facet);
					return true;
				}
			}
		}
		return false;
	}

	public String toText() {
		return toText(null);
	}

	public String toText(PerspectiveObserver _redraw) {
		boolean plural = false;
		StringBuffer result = new StringBuffer();
		for (Iterator<Object> iterator = iterator(); iterator.hasNext();) {
			Object o = iterator.next();
			if (o == Markup.PLURAL_TAG) {
				assert !plural;
				plural = true;
			} else if (o == Markup.NEWLINE_TAG) {
				result.append("\n");
			} else if (o instanceof String) {
				assert !plural : this + " '" + o + "' " + result;
				result.append(o);
			} else if (o instanceof ItemPredicate) {
				ItemPredicate facet = (ItemPredicate) o;
				String name = _redraw == null ? facet.getNameIfPossible()
						: facet.getName(_redraw);
				result.append(name);
				if (plural) {
					Util.pluralize(result);
					plural = false;
				}
			} else {
				assert o == Markup.DEFAULT_COLOR_TAG
						|| o == Markup.DEFAULT_STYLE_TAG
						|| o == Markup.ITALIC_STRING_TAG || o instanceof Color : o;
			}
		}
		return result.toString();
	}

	/**
	 * @param info
	 *            Set of Markup arguments
	 * @return List of Markup arguments, including Perspective ranges
	 */
	static List<Object> coalescePerspectiveSequences(SortedSet<?> info) {
		if (!isCoalescable(info))
			// speed up common case
			return new ArrayList<Object>(info);
		// Util.print("coalescePerspectiveSequences " + Util.valueOfDeep(info));
		List<Object> result = new LinkedList<Object>();
		Object prev = null;
		for (Iterator<?> it = info.iterator(); it.hasNext();) {
			Object o = it.next();
			if (prev != null) {
				Object oPrime = coalesce(prev, o);
				if (oPrime == null)
					result.add(prev);
				else
					o = oPrime;
			}
			prev = o;
		}
		if (prev != null)
			result.add(prev);
		// Util.print("coalescePerspectiveSequences return "
		// + Util.valueOfDeep(result));
		return result;
	}

	/**
	 * @param prev
	 *            Markup argument or range
	 * @param next
	 *            Markup argument
	 * @return range of the form [perspective, perspective], or null if range
	 *         can't be found
	 */
	static Object coalesce(Object prev, Object next) {
		Object result = null;
		if (next instanceof Perspective) {
			Perspective nextPerspective = (Perspective) next;
			Perspective prevSibling = nextPerspective.previousSibling(false);
			if (prevSibling != null && prev == prevSibling) {
				Perspective[] temp = { prevSibling, nextPerspective };
				result = temp;
			} else if (prev instanceof Perspective[]) {
				Perspective[] range = (Perspective[]) prev;
				if (range[1] == prevSibling) {
					range[1] = nextPerspective;
					result = range;
				}
			}
		}
		return result;
	}

	// static Collection coalescePerspectiveSequencesx(Collection info) {
	// if (!isCoalescable(info))
	// // speed up common case
	// return info;
	// Util.print("coalescePerspectiveSequences " + Util.valueOfDeep(info));
	// List result = new LinkedList();
	// Perspective[] range = null;
	// for (Iterator it = info.iterator(); it.hasNext();) {
	// Object o = it.next();
	// if (o instanceof Perspective) {
	// Perspective p = (Perspective) o;
	// if (range != null) {
	// if (range[1].nextSibling() == p
	// && p.getParent().isOrdered()) {
	// range[1] = p;
	// } else {
	// result.add(range);
	// range = null;
	// }
	// }
	// if (range == null) {
	// Perspective[] temp = { p, p };
	// range = temp;
	// }
	// } else {
	// if (range != null) {
	// result.add(range);
	// range = null;
	// }
	// result.add(o);
	// }
	// }
	// if (range != null)
	// result.add(range);
	// // Util.print("coalescePerspectiveSequences return "
	// // + Util.valueOfDeep(result));
	// return result;
	// }

	static boolean isCoalescable(Collection<?> info) {
		Perspective prev = null;
		for (Iterator<?> it = info.iterator(); it.hasNext();) {
			Object o = it.next();
			if (o instanceof Perspective) {
				Perspective p = (Perspective) o;
				Perspective previousSibling = p.previousSibling(false);
				if (previousSibling != null && previousSibling == prev)
					return true;
				prev = p;
			}
		}
		return false;
	}

	static void toEnglish(SortedSet<? extends Object> info, String connector, Markup descriptions) {
		int len = info.size();
		if (len > 0) {
			List<Object> coalescedInfo = coalescePerspectiveSequences(info);
			// if (len > 1)
			// Arrays.sort(info); // , Perspective.indexComparator);
			boolean first = true;
			for (Iterator<Object> it = coalescedInfo.iterator(); it.hasNext();) {
				Object o = it.next();
				if (first) {
					first = false;
				} else if (!it.hasNext()) {
					descriptions.add(connector);
				} else {
					descriptions.add(", ");
				}
				if (o instanceof Perspective[]) {
					Perspective[] range = (Perspective[]) o;
					boolean isFirst = range[0].previousSibling() == null;
					boolean isLast = range[1].nextSibling() == null;
					if (range[0] == range[1]) {
						descriptions.add(range[0]);
					} else if (isFirst == isLast) {
						descriptions.add(range[0]);
						descriptions.add(" - ");
						descriptions.add(range[1]);
					} else if (isFirst) {
						descriptions.add("less than ");
						descriptions.add(range[1]);
					} else if (isLast) {
						descriptions.add("greater than ");
						descriptions.add(range[0]);
					}
				} else
					descriptions.add(o);
			}
		}
	}

	// static boolean toEnglishInternal(Object o, Markup descriptions,
	// boolean first) {
	// if (first) {
	// first = false;
	// } else {
	// descriptions.add(", ");
	// }
	// if (o instanceof Perspective[]) {
	// Perspective[] range = (Perspective[]) o;
	// boolean isFirst = range[0].previousSibling() == null;
	// boolean isLast = range[1].nextSibling() == null;
	// if (range[0] == range[1]) {
	// descriptions.add(range[0]);
	// } else if (isFirst == isLast) {
	// descriptions.add(range[0]);
	// descriptions.add(" - ");
	// descriptions.add(range[1]);
	// } else if (isFirst) {
	// descriptions.add("less than ");
	// descriptions.add(range[1]);
	// } else if (isLast) {
	// descriptions.add("greater than ");
	// descriptions.add(range[0]);
	// }
	// } else
	// descriptions.add(o);
	// return first;
	// }

	public Markup copy() {
		Markup result = new MarkupImplementation();
		result.addAll(this);
		return result;
	}

	public Markup uncolor() {
		for (Iterator<Object> it = iterator(); it.hasNext();) {
			Object o = it.next();
			if (o instanceof Color) {
				it.remove();
			}
		}
		return this;
	}

}
