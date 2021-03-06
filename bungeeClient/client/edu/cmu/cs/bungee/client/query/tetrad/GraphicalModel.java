package edu.cmu.cs.bungee.client.query.tetrad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import edu.cmu.cs.bungee.client.query.ItemPredicate;
import edu.cmu.cs.bungee.javaExtensions.PerspectiveObserver;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.graph.Edge;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph;
import edu.cmu.cs.bungee.javaExtensions.graph.Node;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph.GraphWeigher;

//import edu.cmu.cs.bungee.lbfgs.LBFGS;

public class GraphicalModel extends Distribution {

	/**
	 * If weights get too big, predicted probabilities go to zero, and KL and z
	 * go to infinity.
	 */
	protected static final double MAX_WEIGHT = 15;

	private boolean edgesFixed = false;

	private final static boolean isSymmetric = true;

	/**
	 * {node1, node2) => weight
	 */
	private final double[] weights;

	/**
	 * Non-existent edges are coded -2; uncached are coded -1
	 */
	private final double[] expWeights;

	private double[] energies;

	private double[] expEnergies;

	private final int[][] edgeIndexes;

	private final int[][] edgeStates;

	// private final int[][][] stateEdges;

	private final double[] logDistribution;

	private double z = -1;
	private final int nEdges;
	private final int nEdgesPlusBiases;

	GraphicalModel(List<ItemPredicate> facets, Set<SimpleEdge> edges,
			boolean isSymmetric, int count) {
		super(facets, count);
		assert isSymmetric;
		// this.isSymmetric = isSymmetric;
		// int nFacets = nFacets();

		energies = new double[nStates()];
		expEnergies = new double[nStates()];
		Arrays.fill(expEnergies, 1);
		logDistribution = new double[nStates()];
		edgeIndexes = getEdgeIndexes();

		if (edges == null)
			edges = allEdges(facets, facets);
		assert !isSymmetric || isEdgesCanonical(edges);
		setEdges(edges);

		nEdges = edges.size();
		nEdgesPlusBiases = nEdges + nFacets;
		weights = new double[nEdgesPlusBiases];
		expWeights = new double[nEdgesPlusBiases];
		Arrays.fill(expWeights, 1);

		edgeStates = edgeStates();
		resetWeights();
		// Util.print("GraphicalModel " + getEdges());
		// for (Iterator it = facets.iterator(); it.hasNext();) {
		// Perspective facet = (Perspective) it.next();
		// addEdge(facet, facet);
		// }
		// Util.print("jj "+Util.valueOfDeep(expWeights));
	}

	public double[] getLogDistribution() {
		return logDistribution;
	}

	private boolean isEdgesCanonical(Set<SimpleEdge> edges) {
		for (Iterator<SimpleEdge> it = edges.iterator(); it.hasNext();) {
			SimpleEdge edge = it.next();
			assert edge.p1.compareTo(edge.p2) < 0 : edges + " " + edge;
		}
		return true;
	}

	int nUsedFacets() {
		int result = 0;
		for (Iterator<ItemPredicate> it = facets.iterator(); it.hasNext();) {
			ItemPredicate p = it.next();
			if (nEdges(p) > 0)
				result++;
		}
		// Util.print("nUsedFacets " + result);
		return result;
	}

	List<ItemPredicate> unusedFacets() {
		List<ItemPredicate> result = new LinkedList<ItemPredicate>();
		for (Iterator<ItemPredicate> it = facets.iterator(); it.hasNext();) {
			ItemPredicate p = it.next();
			if (nEdges(p) == 0)
				result.add(p);
		}
		// Util.print("usedFacets " + result);
		return result;
	}

	int nEdges(ItemPredicate p) {
		int result = 0;
		for (Iterator<SimpleEdge> it = getEdges(false).iterator(); it.hasNext();) {
			SimpleEdge edge = it.next();
			if (edge.p1 == p || edge.p2 == p) {
				result++;
			}
		}
		return result;
	}

	protected boolean hasEdge(ItemPredicate cause, ItemPredicate caused) {
		int causeNode = facetIndexOrNot(cause);
		if (causeNode < 0)
			return false;
		int causedNode = facetIndexOrNot(caused);
		if (causedNode < 0)
			return false;
		return edgeIndexes[causeNode][causedNode] >= 0;
	}

	protected boolean hasEdge(int causeNode, int causedNode) {
		return edgeIndexes[causeNode][causedNode] >= 0;
	}

	// double getExpWeight(Perspective cause, Perspective caused) {
	// // Util.print("getWeight " + cause + " => " + caused + " "
	// // + getWeight(getEdge(cause, caused)));
	// return getExpWeight(facetIndex(cause), facetIndex(caused));
	// }

	double getExpWeightOrZero(ItemPredicate cause, ItemPredicate caused) {
		// Util.print("getWeight " + cause + " => " + caused + " "
		// + getWeight(getEdge(cause, caused)));
		return hasEdge(cause, caused) ? getExpWeight(facetIndex(cause),
				facetIndex(caused)) : 1;
	}

	private double getExpWeight(int i, int j) {
		// Util.print("getWeight " + cause + " => " + caused + " "
		// + getWeight(getEdge(cause, caused)));
		double result = expWeights[edgeIndexes[i][j]];
		// if (result < 0) {
		// assert result == -1 : result;
		// result = Math.exp(getWeight(i, j));
		// expWeights[i][j] = result;
		// expWeights[j][i] = result;
		// // NonAlchemyModel.nExpWeight++;
		// }
		assert result >= 0 : result + " " + i + " " + j + " " + getWeight(i, j);
		assert !Double.isNaN(result);
		assert !Double.isInfinite(result);
		// Util.print("getExpWeight " + i + " " + j + " " + result + " old="
		// + Math.exp(getWeight(i, j)));
		return result;
	}

	protected double getWeight(ItemPredicate caused, ItemPredicate caused2) {
		// Util.print("getWeight " + cause + " => " + caused + " "
		// + getWeight(getEdge(cause, caused)));
		return getWeight(facetIndex(caused), facetIndex(caused2));
	}

	double getWeightOrZero(ItemPredicate cause, ItemPredicate caused) {
		// Util.print("getWeight " + cause + " => " + caused + " "
		// + getWeight(getEdge(cause, caused)));
		return hasEdge(cause, caused) ? getWeight(facetIndex(cause),
				facetIndex(caused)) : 0;
	}

	protected double getWeight(int cause, int caused) {
		// Util.print("getWeight " + cause + " => " + caused + " "
		// + getWeight(getEdge(cause, caused)));
		assert hasEdge(cause, caused) : cause + " " + caused + " " + facets()
				+ " " + Util.valueOfDeep(expWeights);
		double result = weights[edgeIndexes[cause][caused]];
		return result;
	}

	// private double getRNormalizedWeightOrZero(
	// Distribution observedDistribution, Perspective cause,
	// Perspective caused) {
	// // Util.print("getWeight " + cause + " => " + caused + " "
	// // + getWeight(getEdge(cause, caused)));
	// return hasEdge(cause, caused) ? getRNormalizedWeight(
	// observedDistribution, cause, caused) : 0;
	// }

	// protected double getRNormalizedWeight(Distribution observedDistribution,
	// Perspective cause, Perspective caused) {
	// Distribution obs = LimitedCausesDistribution.getInstance(facets,
	// observedDistribution.getCounts(), getEdges(false));
	// return obs.averageSumR2(cause, caused, this)
	// * Util.sgn(getWeight(cause, caused));
	// }

	protected double getStdDevNormalizedWeight(ItemPredicate p1,
			ItemPredicate p2) {
		return getWeight(p1, p2) * stdDev(p1);
	}

	protected double[] getWeights() {
		return Util.copy(weights);
	}

	protected boolean setWeights(double[] argument) {
		boolean result = false;
		decacheOdds();
		Arrays.fill(energies, 0);
		Arrays.fill(expEnergies, 1);

		for (int edgeIndex = 0; edgeIndex < nEdgesPlusBiases; edgeIndex++) {
			double weight = argument[edgeIndex];
			if (weights[edgeIndex] != weight) {
				result = true;
			}
			double expWeight = Math.exp(weight);
			assert expWeight > 0 && !Double.isNaN(expWeight)
					&& !Double.isInfinite(expWeight) : weights[edgeIndex]
					+ " => " + weight + "\n" + Util.valueOfDeep(argument);
			weights[edgeIndex] = weight;
			expWeights[edgeIndex] = expWeight;

			int[] statesAffected = edgeStates[edgeIndex];
			int nsa = statesAffected.length;
			for (int j = 0; j < nsa; j++) {
				int state = statesAffected[j];
				energies[state] += weight;
				expEnergies[state] *= expWeight;
			}
		}
		if (result)
			updateLogPredictedDistribution();
		return result;
	}

	protected boolean setWeight(ItemPredicate p, ItemPredicate p2, double weight) {
		return setWeight(facetIndex(p), facetIndex(p2), weight);
	}

	protected void resetWeights() {
		setWeights(getWeights());
		updateLogPredictedDistribution();
	}

	/**
	 * This just "remembers" the weight; MUST call resetWeights afterwards to
	 * cache other info.
	 */
	protected boolean setWeight(int cause, int caused, double weight) {
		assert !Double.isInfinite(weight);
		assert !Double.isNaN(weight);

		int edgeIndex = edgeIndexes[cause][caused];
		boolean result = weights[edgeIndex] != weight;

		weights[edgeIndex] = weight;
		return result;
	}

	private void addEdge(ItemPredicate cause, ItemPredicate caused) {
		addEdge(facetIndex(cause), facetIndex(caused));
	}

	private void addEdge(int cause, int caused) {
		// Util.print("addEdge " + cause + " " + caused + " " + isSymmetric);
		assert !edgesFixed;
		assert cause != caused : cause + " => " + caused + " " + this;
		assert !hasEdge(cause, caused);
		edgeIndexes[cause][caused] = 1;
		edgeIndexes[caused][cause] = 1;
	}

	/**
	 * Just add biases; setEdges will set the others
	 */
	int[][] getEdgeIndexes() {
		int[][] edgeIndexes1 = new int[nFacets][];
		for (int cause = 0; cause < nFacets; cause++) {
			edgeIndexes1[cause] = new int[nFacets];
			Arrays.fill(edgeIndexes1[cause], -1);
			edgeIndexes1[cause][cause] = cause;
		}
		return edgeIndexes1;
	}

	// private void removeEdge(Perspective cause, Perspective caused) {
	// removeEdge(facetIndex(cause), facetIndex(caused));
	// }

	void removeEdge(int cause, int caused) {
		// Util.print("removeEdge " + cause + " " + caused);
		assert hasEdge(cause, caused);
		assert !edgesFixed;
		edgeIndexes[cause][caused] = -1;
		edgeIndexes[caused][cause] = -1;
	}

	// protected List getCauses(Perspective caused) {
	// List result = new LinkedList();
	// int causedNode = facetIndex(caused);
	// if (causedNode >= 0) {
	// for (int causeNode = 0; causeNode < nFacets(); causeNode++) {
	// if (causeNode != causedNode && hasEdge(causeNode, causedNode))
	// result.add(getFacet(causeNode));
	// }
	// }
	// // Util.print("getCauses " + caused + " " + causes);
	// return result;
	// }

	/**
	 * @return [cause, caused] in this order [0, 1], [0, 2], [0, 3], [1, 2], [1,
	 *         3], [2, 3]
	 * 
	 *         i.e. for (int cause = 0; cause < nFacets; cause++) { for (int
	 *         caused = cause; caused <nFacets; caused++) {
	 * 
	 *         for xvec, these follow the bias weights
	 */
	protected EdgeIterator getEdgeIterator() {
		return new EdgeIterator();
	}

	class EdgeIterator implements Iterator<int[]> {

		// current edge
		int cause = 0;
		int caused = -1;

		// next edge
		int nextCause = -1;
		int nextCaused = -1;

		public boolean hasNext() {
			// int nFacets = nFacets();
			if (nextCause < 0) {
				nextCause = cause;
				nextCaused = caused + 1;
				for (; nextCause < nFacets; nextCause++) {
					for (; nextCaused < nFacets; nextCaused++) {
						if (nextCause != nextCaused
								&& hasEdge(nextCause, nextCaused)
								&& (!isSymmetric || nextCause < nextCaused))
							return true;
					}
					nextCaused = 0;
				}
			}
			// Util.print("EdgeIterator " + nextCause + " " + nFacets() + " "
			// + cause + " " + caused + " " + nextCaused);
			return nextCause < nFacets;
		}

		public int[] next() {
			if (hasNext()) {
				cause = nextCause;
				caused = nextCaused;
				nextCause = -1;
				int[] edge = { cause, caused };
				// Util.print("next " + Util.valueOfDeep(edge));
				return edge;
			} else {
				throw new NoSuchElementException();
			}
		}

		public void remove() {
			assert false;
			if (caused > 0) {
				removeEdge(cause, caused);
			} else {
				throw new IllegalStateException();
			}
		}
	}

	private int[][] stateWeights;

	protected int[][] stateWeights() {
		if (stateWeights == null) {
			stateWeights = new int[nStates()][];
			// int nFacets = nFacets();
			for (int state = 0; state < stateWeights.length; state++) {
				int[] wts = new int[0];
				int argIndex = nFacets;
				for (int cause = 0; cause < nFacets; cause++) {
					for (int caused = cause; caused < nFacets; caused++) {
						if (hasEdge(cause, caused)) {
							if (Util.isBit(state, caused)) {
								if (cause == caused) {
									wts = Util.push(wts, cause);
								} else if (Util.isBit(state, cause)) {
									wts = Util.push(wts, argIndex);
								}
							}
							if (caused > cause)
								argIndex++;
						}
					}
				}
				stateWeights[state] = wts;
			}
			// Util.print("sw " + Util.valueOfDeep(stateWeights));
		}
		return stateWeights;
	}

	// private int[][][] stateEdges() {
	// int[][][] stateEdges1 = new int[nStates()][][];
	// int nFacets = nFacets();
	// for (int state = 0; state < stateEdges1.length; state++) {
	// int[][] wts = new int[0][];
	// for (int cause = 0; cause < nFacets; cause++) {
	// for (int caused = cause; caused < nFacets; caused++) {
	// if (hasEdge(cause, caused)) {
	// if (Util.isBit(state, cause)
	// && Util.isBit(state, caused)) {
	// int[] edge = { cause, caused };
	// wts = (int[][]) Util.push(wts, edge, int[].class);
	// }
	// }
	// }
	// }
	// stateEdges1[state] = wts;
	// }
	// return stateEdges1;
	// }

	private int[][] edgeStates() {
		// int nFacets = nFacets();
		int[][] edgeStates1 = new int[nEdgesPlusBiases][];
		int nStates = nStates();
		int[] tempStates = new int[nStates];
		for (int cause = 0; cause < nFacets; cause++) {
			for (int caused = cause; caused < nFacets; caused++) {
				if (hasEdge(cause, caused)) {
					int stateIndex = 0;
					for (int state = 0; state < nStates; state++) {
						if (Util.isBit(state, cause)
								&& Util.isBit(state, caused)) {
							tempStates[stateIndex++] = state;
						}
					}
					int[] es = new int[stateIndex];
					System.arraycopy(tempStates, 0, es, 0, stateIndex);
					int edgeIndex = edgeIndexes[cause][caused];
					edgeStates1[edgeIndex] = es;
				}
			}
		}
		return edgeStates1;
	}

	// /**
	// * @param smallerObserved only needed if fastMax=false
	// * @param observed only needed if fastMax=false
	// * @param fastMax don't bother comparing R-normalized weights
	// * @return distance in displayed parameter space from smallerModel over
	// * edges among facetsOfInterest.
	// *
	// * For learning the model, parent facets are relevant and fastMax is
	// * always true; for evaluating it, only primary facets are relevant,
	// * and if the change is large with fastMax, it is called again with
	// * fastMax=false.
	// */
	// protected double weightSpaceChange(GraphicalModel smallerModel,
	// List facetsOfInterest, Distribution smallerObserved,
	// Distribution observed, boolean fastMax) {
	// // assert primaryFacets.equals(smallerModel.facets) : primaryFacets +
	// // " "
	// // + smallerModel.facets;
	// // Util.print("wsc "+primaryFacets);
	// double delta2 = 0;
	// // double initial = 0;
	// for (Iterator causedIt = facetsOfInterest.iterator(); causedIt
	// .hasNext();) {
	// Perspective caused = (Perspective) causedIt.next();
	// for (Iterator causeIt = facetsOfInterest.iterator(); causeIt
	// .hasNext();) {
	// Perspective cause = (Perspective) causeIt.next();
	// if (cause.compareTo(caused) < 0) {
	// double change = weightSpaceChange(smallerModel,
	// smallerObserved, observed, fastMax, cause, caused);
	// double smoothChange = Math.sqrt(change * change
	// + NonAlchemyModel.WEIGHT_STABILITY_SMOOTHNESS)
	// // - Math
	// // .sqrt(NonAlchemyModel.WEIGHT_STABILITY_SMOOTHNESS)
	// ;
	// // Util.print("wsc " + smoothChange +" "+cause+" "+caused);
	// delta2 += smoothChange;
	// }
	// }
	// }
	// if (Explanation.PRINT_LEVEL > 1) {
	// Util.print("weightSpaceChange " + delta2);
	// }
	// // Util.print("weightSpaceChange " + this);
	// // printGraph(null);
	// // printGraph(false);
	// // Util.print("");
	// // reference.printGraph(false);
	// // Util.print("weightSpaceChange done\n");
	// assert !Double.isNaN(delta2);
	// // double delta = Math.sqrt(delta2);
	// return delta2;
	// }
	//
	// private double weightSpaceChange(GraphicalModel smallerModel,
	// Distribution smallerObserved, Distribution observed,
	// boolean fastMax, Perspective cause, Perspective caused) {
	// assert cause != caused;
	//
	// // Don't normalize, because a single strong predictor will
	// // change the proportions, but not the underlying
	// // dependency.
	// double diff = Math.abs(effectiveWeight(cause, caused)
	// - smallerModel.effectiveWeight(cause, caused));
	// double diffN = diff;
	//
	// if (!fastMax) {
	// double weight0Nforward = smallerModel.getRNormalizedWeightOrZero(
	// smallerObserved, cause, caused);
	// double weightNforward = getRNormalizedWeightOrZero(observed, cause,
	// caused);
	// // Util.print("wsc "+weight0N+" "+weightN);
	// diffN = Math.abs(weightNforward - weight0Nforward) / 2;
	// if (diffN < diff) {
	//
	// double weight0Nbackward = smallerModel
	// .getRNormalizedWeightOrZero(smallerObserved, caused,
	// cause);
	// double weightNbackward = getRNormalizedWeightOrZero(observed,
	// caused, cause);
	// // Util.print("wsc "+weight0N+" "+weightN);
	//
	// diffN += Math.abs(weightNbackward - weight0Nbackward) / 2;
	// }
	// }
	//
	// if (Explanation.PRINT_LEVEL > 1) {
	// printWSC(cause, caused, fastMax, smallerModel, smallerObserved,
	// observed, diff, diffN);
	// }
	// if (diffN < diff)
	// diff = diffN;
	//
	// // diffN + " " + diffU + " "
	// // + sigmoid(weight0U) + " " + sigmoid(weightU) + " " + weight0U
	// // + " " + weightU + " " + cause + " " + caused
	// // // + " norm W0=" + weight0N + " norm W=" + weightN
	// // ;
	//
	// return diff;
	// }
	//
	// private void printWSC(Perspective cause, Perspective caused,
	// boolean fastMax, GraphicalModel smallerModel,
	// Distribution smallerObserved, Distribution observed, double diff,
	// double diffN) {
	// StringBuffer buf = new StringBuffer();
	// boolean normalized = diffN < diff;
	// if (diffN < diff)
	// diff = diffN;
	// buf.append("weightSpaceChange ").append(normalized ? "N " : "U ")
	// .append(diff).append(" ").append(cause).append(" => ").append(
	// caused).append(" ");
	// printW(cause, caused, fastMax, buf, observed).append(" => ");
	// smallerModel.printW(cause, caused, fastMax, buf, smallerObserved)
	// .append(" ");
	// // if (!fastMax)
	// // buf.append(" (").append(diffN).append(")");
	// Util.print(buf.toString());
	// }
	//
	// private StringBuffer printW(Perspective cause, Perspective caused,
	// boolean fastMax, StringBuffer buf, Distribution observed) {
	// double w = getWeightOrZero(cause, caused);
	// if (buf == null)
	// buf = new StringBuffer();
	// if (NonAlchemyModel.USE_SIGMOID) {
	// double expw = getExpWeightOrZero(cause, caused);
	// double sig = expw / (expw + 1);
	// buf.append("sigmoid(").append(w).append(") = ").append(sig);
	// } else {
	// buf.append(w);
	// }
	// if (!fastMax) {
	// double weightN = (getRNormalizedWeightOrZero(observed, cause,
	// caused) + getRNormalizedWeightOrZero(observed, caused,
	// cause)) / 2.0;
	// buf.append(" (").append(weightN).append(")");
	// }
	// return buf;
	// }

	// double sigmoid(double w) {
	// // return w;
	// return 1.0 / (1.0 + Math.exp(-w));
	// }

	double effectiveWeight(ItemPredicate cause, ItemPredicate caused) {
		if (NonAlchemyModel.USE_SIGMOID) {
			double expw = getExpWeightOrZero(cause, caused);
			assert Util.approxEquals(expw, Math.exp(getWeightOrZero(cause,
					caused))) : expw + " "
					+ Math.exp(getWeightOrZero(cause, caused));
			return expw / (expw + 1);
		} else {
			return getWeightOrZero(cause, caused);
		}
	}

	public static String formatWeight(double weight) {
		if (Double.isNaN(weight))
			return "?";
		return Integer.toString((int) Math.rint(100 * weight));
	}

	// public double[] getDistribution() {
	// if (Double.isNaN(distribution[0])) {
	// // NonAlchemyModel.nGetDistribution++;
	// z();
	// }
	// return super.getDistribution();
	// }

	protected void updateLogPredictedDistribution() {
		int nStates = nStates();
		double logZ = Math.log(z());
		// logDistribution = new double[nStates];
		for (int state = 0; state < nStates; state++) {
			logDistribution[state] = energy(state) - logZ;
			// Util.print("state " + state + " " + energy(state) + " "
			// + p[state]);
		}

		// Util.print("logZ=" + logZ);
		// double[] old = getDistribution();
		// for (int state = 0; state < logP.length; state++) {
		// Util.print(state + " " + energy(state) + " old=" + old[state]
		// + " new=" + Math.exp(logP[state]) + " log=" + logP[state]);
		// }
		// NonAlchemyModel.nLogPredictedDistribution++;
	}

	/**
	 * Computes distribution as a side effect
	 */
	private double z() {
		int nStates = nStates();
		// for (int state = 0; state < nStates; state++) {
		// distribution[state] = expEnergy(state);
		// // z += ee;
		// }
		// Util.print("z " + Util.valueOfDeep(expEnergies));
		z = Util.kahanSum(expEnergies);
		if (Double.isInfinite(z) || Double.isNaN(z)) {
			z = 0;
			for (int state = 0; state < nStates; state++) {
				// Util.print(state + " " + expEnergy(state));
				if (Double.isInfinite(expEnergies[state])) {
					z++;
					distribution[state] = 1;
				} else {
					distribution[state] = 0;
				}
			}
			// assert false : "infinite z " + z + " "
			// + Util.valueOfDeep(getWeights());
			for (int state = 0; state < nStates; state++) {
				distribution[state] /= z;
			}
		} else
			// Util.print(Util.valueOfDeep(getWeights()));
			// Util.print(z);
			for (int state = 0; state < nStates; state++) {
				distribution[state] = expEnergies[state] / z;
				// Util.print("state " + state + " " + energy(state) + " "
				// + expEnergy(state) + " " + distribution[state]);
			}
		assert checkDist(distribution);
		assert z >= 0 : z;
		// NonAlchemyModel.nZ++;
		return z;
	}

	// private double energyOLD(int state) {
	// // NonAlchemyModel.nEnergy++;
	// int[][] edges = stateEdges()[state];
	// double result = 0;
	// int nEdges = edges.length;
	// for (int e = 0; e < nEdges; e++) {
	// int[] edge = edges[e];
	// int cause = edge[0];
	// int caused = edge[1];
	// result += getWeight(cause, caused);
	// }
	// // assert Util.approxEquals(result, energyNEW(state)) : result + " "
	// // + energyNEW(state) + " " + state + " "
	// // + Util.valueOfDeep(getWeights())+ " "
	// // + Util.valueOfDeep(energies)+ " "
	// // + Util.valueOfDeep(edgeStates);
	// return result;
	// }
	//
	// private double expEnergyOLD(int state) {
	// // NonAlchemyModel.nExpEnergy++;
	// int[][] edges = stateEdges()[state];
	// double result = 1;
	// int nEdges = edges.length;
	// for (int e = 0; e < nEdges; e++) {
	// int[] edge = edges[e];
	// int cause = edge[0];
	// int caused = edge[1];
	// result *= getExpWeight(cause, caused);
	// }
	// return result;
	// }

	// private double expEnergy(int state) {
	// assert !Double.isNaN(expEnergies[state])
	// && !Double.isInfinite(expEnergies[state]) : Util
	// .valueOfDeep(expEnergies)
	// + " " + Util.valueOfDeep(getWeights());
	// return expEnergies[state];
	// }

	private double energy(int state) {
		assert !Double.isNaN(energies[state])
				&& !Double.isInfinite(energies[state]) : Util
				.valueOfDeep(energies);
		return energies[state];
	}

	// double linkEnergy(int state, int causeNode, int causedNode) {
	// return hasEdge(causeNode, causedNode) && Util.isBit(state, causedNode)
	// && Util.isBit(state, causeNode) ? getWeight(causeNode,
	// causedNode) : 0;
	// }

	// protected Distribution getMarginalDistribution(List subFacets) {
	// int nSubFacets = subFacets.size();
	// if (nSubFacets == nFacets())
	// return this;
	// Set subedges = getEdgesAmong(subFacets);
	// return LimitedCausesDistribution.getInstance(subFacets,
	// getMarginalCounts(subFacets), subedges);
	// }

	@Override
	public Distribution getMarginalDistribution(List<ItemPredicate> subFacets) {
		// Override to avoid cacheing
		return new Distribution(subFacets, getMarginalCounts(subFacets));
	}

	private void setEdges(Set<SimpleEdge> edges) {
		// Util.print("setEdges " + edges);
		for (Iterator<SimpleEdge> it = edges.iterator(); it.hasNext();) {
			SimpleEdge edge = it.next();
			ItemPredicate cause = edge.p1;
			ItemPredicate caused = edge.p2;
			assert cause != caused : "Biases are implicit";
			addEdge(cause, caused);
		}
		int edgeIndex = nFacets;
		for (int cause = 0; cause < nFacets; cause++) {
			for (int caused = cause + 1; caused < nFacets; caused++) {
				if (hasEdge(cause, caused)) {
					edgeIndexes[caused][cause] = edgeIndex;
					edgeIndexes[cause][caused] = edgeIndex++;
				}
			}
		}
		edgesFixed = true;
	}

	// void removeEdges(Set edges) {
	// for (Iterator it = edges.iterator(); it.hasNext();) {
	// List edge = (List) it.next();
	// removeEdge((Perspective) edge.get(0), (Perspective) edge.get(1));
	// }
	// }

	// void clearEdges() {
	// for (Iterator it = getEdgeIterator(); it.hasNext();) {
	// int[] edge = (int[]) it.next();
	// removeEdge(edge[0], edge[1]);
	// }
	// }

	/**
	 * @param nullModel
	 *            this is just to label the "before" weights.
	 * @param debug
	 */
	protected Graph<ItemPredicate> buildGraph(double[] Rs,
			double[][] RnormalizedWeights, double KL, Explanation nullModel,
			PerspectiveObserver redrawer, boolean debug) {
		Graph<ItemPredicate> graph = new Graph<ItemPredicate>(
				(GraphWeigher<ItemPredicate>) null);
		Map<ItemPredicate, Node<ItemPredicate>> nodeMap = new HashMap<ItemPredicate, Node<ItemPredicate>>();
		for (Iterator<ItemPredicate> it = facets.iterator(); it.hasNext();) {
			ItemPredicate p = it.next();
			ensureNode(Rs, graph, nodeMap, p, redrawer);
		}
		for (Iterator<int[]> it = getEdgeIterator(); it.hasNext();) {
			int[] edge = it.next();
			ItemPredicate cause = getFacet(edge[0]);
			// ensureNode(observedDistForNormalization, graph, nodeMap, cause);
			ItemPredicate caused = getFacet(edge[1]);
			// ensureNode(observedDistForNormalization, graph, nodeMap, caused);
			addEdge(Rs, RnormalizedWeights, graph, nodeMap, cause, caused,
					nullModel, redrawer, debug);
			if (isSymmetric)
				addEdge(Rs, RnormalizedWeights, graph, nodeMap, caused, cause,
						nullModel, redrawer, debug);
		}
		assert !graph.getNodes().isEmpty() : printGraph(Rs, RnormalizedWeights,
				KL);
		return graph;
	}

	protected String printGraph(double[] Rs, double[][] RnormalizedWeights,
			double KL) {
		Util.print("printGraph " + this + " KL=" + KL
		// + " sumR="
				// + observedDistForNormalization.sumR(this)
				);

		// Util.print("pred=");
		// printCounts();
		// Util.print(" obs=");
		// observedDistForNormalization.printCounts();

		for (Iterator<ItemPredicate> it = facets().iterator(); it.hasNext();) {
			ItemPredicate caused = it.next();
			// if (facetsOfInterest == null)
			Util.print(getWeight(caused, caused) + " ("
					+ Rs[facetIndex(caused)] + ") " + caused);
		}
		for (Iterator<int[]> it = getEdgeIterator(); it.hasNext();) {
			int[] edge = it.next();
			ItemPredicate cause = getFacet(edge[0]);
			ItemPredicate caused = getFacet(edge[1]);
			double weight = effectiveWeight(cause, caused);
			// if (weight != 0 && cause.compareTo(caused) > 0)
			// if (facetsOfInterest == null
			// || (facetsOfInterest.contains(cause) && facetsOfInterest
			// .contains(caused)))
			Util.print(weight + " ("
					+ RnormalizedWeights[facetIndex(caused)][facetIndex(cause)]
					+ ") " + cause + " => ("
					+ RnormalizedWeights[facetIndex(cause)][facetIndex(caused)]
					+ ") " + caused);
		}
		Util.print("");
		return ""; // suitable for assert messages
	}

	private Node<ItemPredicate> ensureNode(double[] Rs,
			Graph<ItemPredicate> graph,
			Map<ItemPredicate, Node<ItemPredicate>> nodeMap,
			ItemPredicate facet, PerspectiveObserver redrawer) {
		assert graph != null;
		assert facet != null;
		Node<ItemPredicate> result = nodeMap.get(facet);
		if (result == null) {
			String label = redrawer == null ? facet.toString() : facet
					.toString(redrawer);
			// if (observedDistForNormalization != null)
			label = formatWeight(Rs[facetIndex(facet)]) + " " + label;
			// label = formatWeight(getWeight(facet, facet)) + " " + label;

			// Prefix with space so edge line doesn't merge with any minus sign
			result = graph.addNode(facet, " " + label);
			nodeMap.put(facet, result);
		}
		assert result != null : facet;
		return result;
	}

	// This is called twice; once for each direction.
	private void addEdge(double[] Rs, double[][] RnormalizedWeights,
			Graph<ItemPredicate> graph,
			Map<ItemPredicate, Node<ItemPredicate>> nodeMap,
			ItemPredicate cause, ItemPredicate caused, Explanation nullModel,
			PerspectiveObserver redrawer, boolean debug) {
		// Util.print("addRule " + negLiteral + " " + posLiteral);
		Node<ItemPredicate> posNode = ensureNode(Rs, graph, nodeMap, caused,
				redrawer);
		// if (cause != null) {
		Node<ItemPredicate> negNode = ensureNode(Rs, graph, nodeMap, cause,
				redrawer);
		// Util.print("addEdge " + posNode + " " + negNode);
		Edge<ItemPredicate> edge = graph.getEdge(posNode, negNode);
		if (edge == null)
			edge = graph.addEdge((String) null, posNode, negNode);
		int causeIndex = facetIndex(cause);
		int causedIndex = facetIndex(caused);
		double forwardWeight = RnormalizedWeights[causeIndex][causedIndex];
		if (debug) {
			String label = formatWeight(forwardWeight);
			if (nullModel.facets().contains(cause)
					&& nullModel.facets().contains(caused))
				label = formatWeight(nullModel.getRNormalizedWeight(cause,
						caused))
						+ " > " + label;
			edge.setLabel("        " + label + "        ", posNode);
			edge.setLabel(formatWeight(getWeight(cause, caused)) + " ("
					+ formatWeight(effectiveWeight(cause, caused)) + ")",
					Edge.CENTER_LABEL);
		} else {
			double backwardWeight = RnormalizedWeights[causedIndex][causeIndex];
			double averageWeight = (forwardWeight + backwardWeight) / 2;
			edge.setLabel(formatWeight(averageWeight), Edge.CENTER_LABEL);
		}
	}

	/**
	 * @param causes
	 * @param causeds
	 * @return [[cause1, caused1], ... Does not return biases
	 */
	protected static Set<SimpleEdge> allEdges(Collection<ItemPredicate> causes,
			Collection<ItemPredicate> causeds) {
		// Util.print("addEdges " + causes+" "+causeds);
		HashSet<SimpleEdge> result = new HashSet<SimpleEdge>();
		for (Iterator<ItemPredicate> it1 = causeds.iterator(); it1.hasNext();) {
			ItemPredicate caused = it1.next();
			for (Iterator<ItemPredicate> it2 = causes.iterator(); it2.hasNext();) {
				ItemPredicate cause = it2.next();
				if (caused != cause) {
					// assume symmetric and canonicalize so cache lookup will
					// always work
					// Perspective smaller = cause;
					// Perspective larger = caused;
					// if (smaller.compareTo(larger) > 0) {
					// smaller = caused;
					// larger = cause;
					// }
					// result.add(getEdge(smaller, larger));
					result.add(SimpleEdge.getInstance(cause, caused));
				}
			}
		}
		return result;
	}

	protected int[][] edgesToIndexes(Set<SimpleEdge> edges) {
		List<int[]> intEdges = new ArrayList<int[]>(edges.size());
		for (Iterator<SimpleEdge> it = edges.iterator(); it.hasNext();) {
			SimpleEdge edge = it.next();
			int[] intEdge = new int[2];
			intEdge[0] = facetIndex(edge.p1);
			intEdge[1] = facetIndex(edge.p2);
			intEdges.add(intEdge);
		}
		return intEdges.toArray(new int[0][]);
	}

	protected Set<SimpleEdge> getEdges(boolean includeBiases) {
		Set<SimpleEdge> result = getEdgesAmong(facets);
		if (includeBiases) {
			for (Iterator<ItemPredicate> it = facets.iterator(); it.hasNext();) {
				ItemPredicate p = it.next();
				// result.add(getEdge(p, p));
				result.add(SimpleEdge.getInstance(p, p));
			}
		}
		return result;
	}

	private Set<SimpleEdge> getEdgesAmong(List<ItemPredicate> prevfacets) {
		// Util.print("addEdges " + causes+" "+causeds);
		HashSet<SimpleEdge> result = new HashSet<SimpleEdge>();
		for (Iterator<int[]> it = getEdgeIterator(); it.hasNext();) {
			int[] edge = it.next();
			ItemPredicate cause = getFacet(edge[0]);
			ItemPredicate caused = getFacet(edge[1]);
			assert caused != cause;
			if (prevfacets.contains(cause) && prevfacets.contains(caused)) {
				// result.add(getEdge(cause, caused));
				result.add(SimpleEdge.getInstance(cause, caused));
			}
		}
		// Util.print("getEdges " + result);
		return result;
	}

	static class SimpleEdge {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((p1 == null) ? 0 : p1.hashCode());
			result = prime * result + ((p2 == null) ? 0 : p2.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SimpleEdge other = (SimpleEdge) obj;
			if (p1 == null) {
				if (other.p1 != null)
					return false;
			} else if (!p1.equals(other.p1))
				return false;
			if (p2 == null) {
				if (other.p2 != null)
					return false;
			} else if (!p2.equals(other.p2))
				return false;
			return true;
		}

		ItemPredicate p1;
		ItemPredicate p2;

		static SimpleEdge getInstance(ItemPredicate cause, ItemPredicate caused) {
			// List result = new ArrayList(2);
			if (cause.compareTo(caused) < 0) {
				// result.add(cause);
				// result.add(caused);
				return new SimpleEdge(cause, caused);
			} else {
				// result.add(caused);
				// result.add(cause);
				return new SimpleEdge(caused, cause);
			}
			// return result;
		}

		SimpleEdge(ItemPredicate cause, ItemPredicate caused) {
			this.p1 = cause;
			this.p2 = caused;
		}

		@Override
		public String toString() {
			return "<SimpleEdge " + p1 + ", " + p2 + ">";
		}
	}

	protected static SimpleEdge getEdge(ItemPredicate cause,
			ItemPredicate caused) {
		// List result = new ArrayList(2);
		if (cause.compareTo(caused) < 0) {
			// result.add(cause);
			// result.add(caused);
			return new SimpleEdge(cause, caused);
		} else {
			// result.add(caused);
			// result.add(cause);
			return new SimpleEdge(caused, cause);
		}
		// return result;
	}

	protected static Collection<SimpleEdge> getEdgesTo(
			Collection<ItemPredicate> x, ItemPredicate caused) {
		Collection<SimpleEdge> result = new ArrayList<SimpleEdge>(x.size());
		for (Iterator<ItemPredicate> it = x.iterator(); it.hasNext();) {
			ItemPredicate p = it.next();
			// result.add(getEdge(p, caused));
			result.add(SimpleEdge.getInstance(p, caused));
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("<").append(Util.shortClassName(this)).append(" ").append(
				facets);
		if (true) {
			buf.append(" nEdges=").append(nEdges);
		} else {
			buf.append(getEdges(false));
		}
		buf.append(" ").append(Util.valueOfDeep(getCounts())).append(">");
		return buf.toString();
	}

	protected int nEdges() {
		return nEdges;
	}

	protected int getNumEdgesPlusBiases() {
		return nEdgesPlusBiases;
	}

	protected double bigWeightPenalty() {
		double result = 0;
		for (int edgeIndex = 0; edgeIndex < nEdgesPlusBiases; edgeIndex++) {
			double excess = Math.abs(weights[edgeIndex]) - MAX_WEIGHT;
			if (excess > 0)
				result += excess * excess;
		}
		// if (result>0)System.err.println("BWP="+result);
		return result;
	}

	protected void bigWeightGradient(double[] gradient) {
		int nWeights = weights.length;
		for (int edgeIndex = 0; edgeIndex < nWeights; edgeIndex++) {
			double w = weights[edgeIndex];
			double excess = Math.abs(w) - MAX_WEIGHT;
			gradient[edgeIndex] = excess > 0 ? 2 * excess * Util.sgn(w) : 0;
			assert !Double.isNaN(gradient[edgeIndex]) : excess + " " + w;
		}
	}

}
