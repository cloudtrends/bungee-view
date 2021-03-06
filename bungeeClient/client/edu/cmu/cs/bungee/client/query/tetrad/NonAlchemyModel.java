package edu.cmu.cs.bungee.client.query.tetrad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.math.LBFGSMinimizer;
import edu.cmu.cs.bungee.client.query.ItemPredicate;
import edu.cmu.cs.bungee.client.query.Perspective;
import edu.cmu.cs.bungee.client.query.Query;
import edu.cmu.cs.bungee.client.query.tetrad.GraphicalModel.SimpleEdge;
import edu.cmu.cs.bungee.javaExtensions.PerspectiveObserver;
import edu.cmu.cs.bungee.javaExtensions.Util;
import edu.cmu.cs.bungee.javaExtensions.graph.Graph;

//import edu.cmu.cs.bungee.lbfgs.LBFGS;
//import edu.cmu.cs.bungee.lbfgs.LBFGS.ExceptionWithIflag;

public class NonAlchemyModel extends Explanation
// implements MultivariateFunction MFWithGradient
		implements DifferentiableFunction {
	private static final int BURN_IN = 0;
	// private static final int PRE_BURN_IN = 0;
	// static double machinePrecision = 1E-15;
	private static double lbfgsAccuracy = 1e-9;
	// private static int[] printInterval = { Explanation.PRINT_LEVEL > 2 ? 1 :
	// -1,
	// 3 };
	static final double WEIGHT_STABILITY_SMOOTHNESS = 1e-9;
	static final boolean USE_SIGMOID = true;

	// static int nSetWeights = 0;
	// static int nNoopSetWeights = 0;
	// static int nSetWeight = 0;
	// static int nExpWeight = 0;
	// static int nLogPredictedDistribution = 0;
	// static int nGetDistribution = 0;
	// static int expectedW = 0;
	// static int expectedS = 0;
	// static int nEnergy;
	// static int nExpEnergy;
	// static int nZ;
	// static int nOW;
	// static int nEvalNgrad;
	// static int nNoOpGrad;
	// static int nNoOpEval;
	private double cachedEval = Double.NaN;
	private double[] cachedGradient;
	//
	// void stats() {
	// Util.print("nSetWeights " + nSetWeights);
	// Util.print("nNoopSetWeights " + nNoopSetWeights);
	// Util.print("nSetWeight " + nSetWeight);
	// Util.print("nExpWeight " + nExpWeight);
	// Util.print("nLogPredictedDistribution " + nLogPredictedDistribution);
	// Util.print("nGetDistribution " + nGetDistribution);
	// Util.print("expectedW " + expectedW);
	// Util.print("expectedS " + expectedS);
	// Util.print("nEnergy " + nEnergy);
	// Util.print("nExpEnergy " + nExpEnergy);
	// Util.print("nZ " + nZ);
	// Util.print("nOW " + nOW);
	// Util.print("nEvalNgrad " + nEvalNgrad);
	// Util.print("nNoOpGrad " + nNoOpGrad);
	// Util.print("nNoOpEval " + nNoOpEval);
	// }

	private static Map<Object, NonAlchemyModel> explanations = new HashMap<Object, NonAlchemyModel>();

	private static Explanation getInstance(List<ItemPredicate> facets, Set<SimpleEdge> edges,
			Explanation base, List<ItemPredicate> likelyCandidates) {
		if (edges == null)
			edges = GraphicalModel.allEdges(facets, facets);
		Object args = args(facets, edges, DEFAULT_EDGE_COST);
		Explanation prev = explanations.get(args);
		if (prev == null)
			prev = new NonAlchemyModel(facets, edges, base, likelyCandidates);
		// if (base != null)
		// Util.print("gi " + prev + " " + " " + prev.nullModel + " " + base
		// + " " + base.nullModel);
		// assert Double.isNaN(((NonAlchemyModel)prev).cachedEval);
		return prev;
	}

	// static Explanation getInstance(List facets, Set edges) {
	// // Util.print("Expl.getInst " + facets);
	// Explanation prev = null;
	// if (edges == null)
	// edges = GraphicalModel.allEdges(facets, facets);
	// List args = args(facets, facets, edges);
	// args = args.subList(1, 4);
	// for (Iterator it = explanations.entrySet().iterator(); it.hasNext()
	// && prev == null;) {
	// Map.Entry entry = (Map.Entry) it.next();
	// List prevArgs = (List) entry.getKey();
	// // if (args.equals(prevArgs.subList(1, 3)))
	// if (args.equals(prevArgs.subList(1, 4)))
	// prev = (Explanation) entry.getValue();
	// }
	// if (prev == null)
	// prev = new NonAlchemyModel(facets, edges, null, null);
	// return prev;
	// }

	public static Explanation getExplanation(Perspective popupFacet) {
		Explanation result = null;
		List<ItemPredicate> primaryFacets = Explanation.relevantFacets(popupFacet);
		if (primaryFacets.size() > 1 /* && primaryFacets.size() <= 32 */) {
			// Can't get a distribution over more than 32 variables, because we
			// represent
			// the possible states as an int.
			result = getExplanationForFacets(primaryFacets);
			// result.writeVennMasterFile(primaryFacets);
		}
		Util.print("getExplanation " + popupFacet + " " + result);
		return result;
	}

	private static Explanation getExplanationForFacets(List<ItemPredicate> facets) {
//		Util.print("getExplanationForFacets "+facets);
		long start = (new Date()).getTime();
		totalNumFuns = 0;
		// totalNumGrad = 0;
		// totalNumLineSearches = 0;

		List<ItemPredicate> candidates = candidateFacets(facets, true);
		if (candidates.size() == 0) {
			if (PRINT_LEVEL > NOTHING)
				Util.err("No candidates for " + facets);
			return null;
		}
		Distribution.cacheCandidateDistributions(facets, candidates);
		Explanation nullModel = NonAlchemyModel.getInstance(facets, null, null,
				null);
		Explanation result = nullModel.getExplanation(candidates);

		if (PRINT_CANDIDATES_TO_FILE)
			nullModel.printToFile(nullModel);
		if (PRINT_LEVEL >= Explanation.GRAPH) {
			nullModel.printGraph();
			result.printGraph();
			// result.printToFile(nullModel);
			// This will already have been printed, when it was guessed
			// result.printToFile();
		}
		if (PRINT_LEVEL >= Explanation.WEIGHTS)
			for (Iterator<ItemPredicate> it = facets.iterator(); it.hasNext();) {
				ItemPredicate p = it.next();
				nullModel.printTable(p);
				result.printTable(p);
			}
		if (PRINT_LEVEL >= Explanation.STATISTICS) {
			result.printStats(nullModel);
			long duration = (new Date()).getTime() - start;
			Util.print("getExplanation duration=" + (duration / 1000)
					+ " nEdges=" + result.predicted.nEdges() + "\n");
		}
		assert result.facets().containsAll(facets) : result;
		return result;
	}

	@Override
	Explanation getAlternateExplanation(List<ItemPredicate> facets) {
		return NonAlchemyModel.getInstance(facets, null, this, null);
	}

	@Override
	Explanation getAlternateExplanation(Set<SimpleEdge> edges) {
		// Util.print("getAlternateExplanation " + this + " " + edges);
		return NonAlchemyModel.getInstance(facets(), edges, this, null);
	}

	/**
	 * @param facets
	 * @param edges
	 * @param base
	 *            NOT USED
	 * @param likelyCandidates
	 */
	protected NonAlchemyModel(List<ItemPredicate> facets, Set<SimpleEdge> edges, Explanation base,
			List<ItemPredicate> likelyCandidates) {
		super(facets, edges, likelyCandidates);
		learnWeights();
		cache();
	}

	private void cache() {
		Object args = args(facets(), predicted.getEdges(false), edgeCost);
		Explanation prev = explanations.get(args);
		if (prev == null) {
			// Util.print("caching " + facets());

			// int n = base == null ? nFacets() : base.nFacets();
			// double[] rs2 = getRs();
			// double[] brs2 = new double[n];
			// int brs2i = 0;
			// List nbfs = new ArrayList(nFacets() - n);
			// for (int i = 0; i < rs2.length; i++) {
			// ItemPredicate f = (ItemPredicate) facets().get(i);
			// if (base == null || base.facets().contains(f)) {
			// brs2[brs2i++] = rs2[i];
			// } else {
			// nbfs.add(f);
			// }
			// }
			// Util.print("c " + nbfs + " " + Util.sum(brs2) + " "
			// + Util.valueOfDeep(brs2));

			explanations.put(args, this);
		} else if (!approxEquals(prev)) {
			printGraph();
			prev.printGraph();
			assert false;
		}
	}

	/**
	 * The cache should be a member of Query, but for now we just keep one
	 * global cache.
	 */
	public static void decacheExplanations() {
		// Util.print("decacheDistributions");
		explanations.clear();
	}

	/**
	 * force fewer/additional facets for this model
	 */
	public Explanation addFacets(List<ItemPredicate> primary, int delta) {
		int n = nUsedFacets() + delta;
		if (n < 0)
			return this;
		assert edgeCost >= 0 : edgeCost;
		double minEdgeCost = delta > 0 ? 0 : edgeCost;
		double maxEdgeCost = delta > 0 ? edgeCost : Double.POSITIVE_INFINITY;
		Explanation result = this;
		List<ItemPredicate> candidates = candidateFacets(primary, true);
		Distribution.cacheCandidateDistributions(primary, candidates);
		Explanation nullModel = getInstance(primary, null, null, null);
		Explanation best = null;
		do {
			double currentEdgeCost = maxEdgeCost == Double.POSITIVE_INFINITY ? minEdgeCost * 2
					: (minEdgeCost + maxEdgeCost) / 2;

			result = FacetSelection.selectFacets(nullModel, candidates,
					currentEdgeCost);
			result = EdgeSelection.selectEdges(result, currentEdgeCost,
					nullModel);
			result.edgeCost = currentEdgeCost;

			Util.print("currentEdgeCost => " + currentEdgeCost + " ("
					+ minEdgeCost + "-" + maxEdgeCost + "); nUsedFacets = "
					+ result.nUsedFacets() + "; goal = " + n);

			if ((result.nUsedFacets() - nUsedFacets()) * delta > 0
					&& (best == null || (result.nUsedFacets() - best
							.nUsedFacets())
							* delta < 0))
				best = result;
			if (result.nUsedFacets() < n)
				maxEdgeCost = currentEdgeCost;
			else if (result.nUsedFacets() > n)
				minEdgeCost = currentEdgeCost;
		} while (result.nUsedFacets() != n
				&& !Util.approxEquals(maxEdgeCost, minEdgeCost));
		if (best == null)
			best = this;
		Util.print(" EdgeCost " + DEFAULT_EDGE_COST + " => " + best.edgeCost
				+ "; nUsedFacets = " + result.nUsedFacets() + "; goal = " + n);
		return best;
	}

	private static List<Object> args(List<ItemPredicate> facets, Set<SimpleEdge> edges, double edgeCost) {
		List<Object> args = new ArrayList<Object>(3);
		// List nf = new ArrayList(baseFacets);
		// Collections.sort(nf);
		// args.add(nf);
		List<ItemPredicate> f = new ArrayList<ItemPredicate>(facets);
		Collections.sort(f);
		args.add(f);
		Set<SimpleEdge> e = new HashSet<SimpleEdge>(edges);
		args.add(e);
		args.add(new Double(edgeCost));

		// We're no longer using anything about base to evaluate models
		// args.add(base);

		return args; // Collections.unmodifiableList(args);
	}

	/*
	 * base is just used to initialize weights
	 */
	@Override
	protected void learnWeights() {
		boolean debug = false;
		// base = null;

		// debug = predicted.nEdges() == 10
		// && facets().contains(
		// ((Perspective) facets().get(0)).query()
		// .findPerspective(374));
		long start = debug || PRINT_LEVEL >= GRAPH ? new Date().getTime() : 0;

		// Better results if you don't
		// if (base != null)
		// initializeWeights(base);

		if (debug)
			Util.print("\ninitial weights  KL "
					+
					// observed.unnormalizedKLdivergence(predicted
					// .logPredictedDistribution())
					klDivergence() + " "
					+ Util.valueOfDeep(predicted.getWeights()) + "\n"
					+ Util.valueOfDeep(observed.getCounts()));

		Set<SimpleEdge> edges = predicted.getEdges(true);
		int[][] allEdges = predicted.edgesToIndexes(edges);
		for (int i = 0; i < BURN_IN; i++) {
			if (!optimizeWeight(allEdges)) {
				// Util.err("stop2 at "+i);
				break;
			}
			// double fx = observed.unnormalizedKLdivergence(predicted
			// .logPredictedDistribution());
			// if (Math.abs(fx - prev) < epsilon)
			// break;
			// prev = fx;
		}
		double[] sw = predicted.getWeights();

		if (debug && BURN_IN > 0) {
			double kl = klDivergence();
			Util.print("burned-in weights " + (new Date().getTime() - start)
					+ "ms, " + BURN_IN + " iterations, KL " + kl + " "
					+ Util.valueOfDeep(sw));
		}

		// double[] weights = cgSearch(sw);
		double[] weights = lbfgsSearch(sw);
		setWeights(weights);

		if (debug || PRINT_LEVEL >= Explanation.WEIGHTS) {
			double kl = klDivergence();
			Util.print("final weights " + (new Date().getTime() - start)
					+ "ms, KL " + kl + " " + Util.valueOfDeep(weights) + " "
					+ predicted);
		}
	}

	// private double[] cgSearch(double[] sw) {
	// ConjugateGradientSearch search = new ConjugateGradientSearch();
	// double[] weights = search.findMinimumArgs(this, sw, epsilon, epsilon);
	//
	// // if (debug) {
	// // Util.print("final weights  " + (new Date().getTime() - start)
	// // + "ms, " + (search.numFun + BURN_IN) + " iterations, KL "
	// // + klDivergence() + " " + Util.valueOfDeep(weights));
	// // Util.print("wsi " + NonAlchemyModel.WEIGHT_STABILITY_IMPORTANCE
	// // + "\n" + this + " " + base);
	// // }
	//
	// totalNumFuns += search.numFun + BURN_IN;
	// totalNumGrad += search.numGrad;
	// return weights;
	// }

	private double[] lbfgsSearch(double[] sw) {
		double[] startW = new double[sw.length];
		System.arraycopy(sw, 0, startW, 0, startW.length);

		// int historyLength = 4;
		// boolean useDiag = false;
		// double[] grad = new double[sw.length];
		// double[] diag = new double[sw.length];
		// double f = evaluate(sw, grad);
		// int[] iFlag = { 0 };

		// if (useDiag)
		// hessianDiagonal(diag);
		// if (nFacets()>6)printInterval[0]=1;
		// LBFGS.maxfev = 100;
		// if (predicted.nEdges()==0&&nFacets()==4
		// && facets().contains(
		// ((Perspective) facets().get(0)).query()
		// .findPerspective(150))) {
		// printInterval[0] = 1;
		// // LBFGS.gtol=0.1;
		// } else {
		// printInterval[0] = -1;
		// // LBFGS.gtol=0.9;
		// }

		// try {
		// LBFGS.lbfgs(sw.length, historyLength, sw, f, grad, useDiag, diag,
		// printInterval, lbfgsAccuracy, machinePrecision, iFlag);
		// while (iFlag[0] > 0) {
		// // for (int i = 0; i < sw.length; i++) {
		// // sw[i] = Util.constrain(sw[i], -100, 100);
		// // }
		// // if (iFlag[0] == 2) {
		// // assert useDiag;
		// // hessianDiagonal(diag);
		// // } else {
		// assert iFlag[0] == 1;
		// f = evaluate(sw, grad);
		// LBFGS.lbfgs(sw.length, historyLength, sw, f, grad, useDiag,
		// diag, printInterval, lbfgsAccuracy, machinePrecision,
		// iFlag);
		// }
		// } catch (ExceptionWithIflag e) {
		// if (PRINT_LEVEL > 0) {
		// Util.err(e);
		// // Util.err(Util.valueOfDeep(sw) + " "
		// // + Util.valueOfDeep(predicted.getWeights()));
		// }
		// } catch (Exception e) {
		// Util.err(LBFGS.nfevaluations() + " " + Util.valueOfDeep(startW));
		// Util.err(e);
		// e.printStackTrace();
		// if (printInterval[0] < 0) {
		// printInterval[0] = 1;
		// lbfgsSearch(startW);
		// }
		//
		// // else {
		// // if (Mcsrch.x0 != null) {
		// // Util.print("\nx0,xstep,searchDir:\n"
		// // + Util.valueOfDeep(Mcsrch.x0) + "\n"
		// // + Util.valueOfDeep(Mcsrch.xstep) + "\n"
		// // + Util.valueOfDeep(Mcsrch.searchDir));
		// // Util
		// //
		// .print("\nstep\tf(step)\t\t\tdelta f(step)\t\tg(step)\t\t\tdelta g(step)\t\tx(step)\tgrad(step)");
		// // double[] iw = new double[sw.length];
		// // double lastFstp = f;
		// // double lastDg = 0;
		// // for (int i = 0; i < 101; i++) {
		// // float zeroToOne = i / 100.0f;
		// // for (int j = 0; j < sw.length; j++) {
		// // iw[j] = Util.interpolate(Mcsrch.x0[j],
		// // Mcsrch.xstep[j], zeroToOne);
		// // }
		// // double fstp = evaluate(iw, grad);
		// // double dg = 0;
		// // for (int j = 0; j < iw.length; j++) {
		// // dg = dg + grad[j] * Mcsrch.searchDir[j];
		// // }
		// // Util.print(zeroToOne + "\t" + fstp + "\t"
		// // + (fstp - lastFstp) + "\t" + dg + "\t"
		// // + (dg - lastDg) + "\t" + Util.valueOfDeep(iw)
		// // + "\t" + Util.valueOfDeep(grad));
		// // lastFstp = fstp;
		// // lastDg = dg;
		// // }
		// // }
		// // System.exit(0);
		// // }
		// }
		// if (iFlag[0] < 0) {
		// // Util.err("LBFGS barfed: " + iFlag[0]);
		// }
		//
		// totalNumFuns += LBFGS.nfevaluations() + BURN_IN * predicted.nEdges();
		// // totalNumGrad += LBFGS.nfevaluations();
		// // totalNumLineSearches += LBFGS.nLineEvaluations();

		double[] result = new LBFGSMinimizer().minimize(this, sw,
				lbfgsAccuracy, false);

		return result;
	}

	private boolean setWeights(double[] argument) {
		boolean result = true;
		try {
			result = predicted.setWeights(argument);
		} catch (Error e) {
			Util.err("While setting weights for\n" + observed + "\n"
					+ predicted);
			// e.printStackTrace();
			throw (e);
		}
		if (result) {
			cachedEval = Double.NaN;
			getCachedGradient()[0] = Double.NaN;
		}
		return result;
	}

	private double evaluate(double[] argument) {
		setWeights(argument);
		if (!Double.isNaN(cachedEval))
			return cachedEval;
		// predicted.printGraph(getObservedDistribution());
		// Util.print("eval "+nullModel.facets()+" "+nullModel+" "+this);

		double result = klDivergence() + predicted.bigWeightPenalty();
		// if (WEIGHT_STABILITY_IMPORTANCE > 0) {
		// double change = weightSpaceChange(parentModel,
		// parentModel.facets(), true);
		// change = Math.log(Math.E + change * WEIGHT_STABILITY_IMPORTANCE);
		// result *= change;
		// }
		cachedEval = result;
		return result;
	}

	@Override
	/*
	 * Cases:
	 * 
	 * FacetSelection: previous has 1 fewer facet, and its parent is this.
	 * 
	 * EdgeSelection: previous has 1 additional edge. This model's parent may be
	 * largerModel, or one of its ancestors.
	 * 
	 * @return the weightSpaceChange minus the worsening of the kl divergence
	 * compared to the previous over the previous's facets (scaled by
	 * NULL_MODEL_ACCURACY_IMPORTANCE)
	 */
	double improvement(Explanation previous, double threshold1,
			List<ItemPredicate> primaryFacets) {
		// double divergence = previous.observed.klDivergence(predicted
		// .getMarginal(previous.facets()));
		// double previousDivergence = previous.klDivergence();
		// double divergenceIncrease = (divergence - previousDivergence)
		// * NULL_MODEL_ACCURACY_IMPORTANCE;

//		Util.print("NAM.improvement " + interactionInformationHacked(previous)
//				+ " " + facets());

		double fastWeighSpaceChange = weightSpaceChange(previous,
				primaryFacets, true);

		// Util.print("fastImprov " + weightSpaceChange + " " + threshold1);
		double weightSpaceChange = fastWeighSpaceChange;
		double sgn = Util.sgn(threshold1);
		double result = weightSpaceChange /* - sgn * divergenceIncrease */;
		boolean isSlow = result > threshold1 * sgn;
		if (isSlow) {
			weightSpaceChange = weightSpaceChange(previous, primaryFacets,
					false);
			result = weightSpaceChange /* - divergenceIncrease */;
			// Util.print(" slowImprov " + weightSpaceChange + " " +
			// threshold1);
		}

		if (PRINT_LEVEL >= Explanation.WEIGHTS) {
			Util.print("      "
					+ (isSlow ? "*" : " ")
					+ "improvement "
					+ result
					// + " = ("
					// + weightSpaceChange
					// + " (wc) - ("
					// + divergence
					// + " (current) - "
					// + previousDivergence
					// + "(previous)) * "
					// + NULL_MODEL_ACCURACY_IMPORTANCE
					// + " = "
					// + divergenceIncrease
					// + ", "
					// + divergenceIncreaseOverParent
					+ " (dKL))"
					+ (!isSlow ? "" : " (fast wc was " + fastWeighSpaceChange
							+ ")")
					// + " nullDivergence "
					// +
					// nullModel.observed.KLdivergence(largerModel.
					// predicted
					// .getMarginal(nullModel.facets()))
					// + " - "
					// + nullModel.KLdivergence(
					// + " = "
					// + deltaNullDivergence
					// + (largerModel.nFacets() == nFacets() ?
					// " fullDivergence ("
					// + largerModel.klDivergence()
					// + " - "
					// + klDivergence()
					// + ") * "
					// + FULL_MODEL_ACCURACY_IMPORTANCE
					// + " = "
					// + divergenceIncreaseOverParent
					+ "\nprev predicted="
					+ Util.valueOfDeep(previous.predicted.getDistribution())
					+ "\n     predicted="
					+ Util
							.valueOfDeep(predicted.getMarginal(previous
									.facets())) + "\n      observed="
					+ Util.valueOfDeep(previous.observed.getDistribution())
			// + " deltaNullDivergence=" + deltaNullDivergence
					// + " deltaFullDivergence=" + deltaFullDivergence
					);
		}
		return result;
	}

	/**
	 * @param fastMax
	 *            don't bother comparing R-normalized weights
	 * @return distance in displayed parameter space from smallerModel over
	 *         edges among facetsOfInterest.
	 */
	private double weightSpaceChange(Explanation control,
			List<ItemPredicate> facetsOfInterest, boolean fastMax) {
		double delta2 = 0;
		for (Iterator<ItemPredicate> causedIt = facetsOfInterest.iterator(); causedIt
				.hasNext();) {
			ItemPredicate caused = causedIt.next();
			for (Iterator<ItemPredicate> causeIt = facetsOfInterest.iterator(); causeIt
					.hasNext();) {
				ItemPredicate cause = causeIt.next();
				if (cause.compareTo(caused) <= 0) {
					double change = weightSpaceChange(control, fastMax, cause,
							caused);
					double smoothChange = Math.sqrt(change * change
							+ NonAlchemyModel.WEIGHT_STABILITY_SMOOTHNESS)
					// - Math
					// .sqrt(NonAlchemyModel.WEIGHT_STABILITY_SMOOTHNESS)
					;
					// Util.print("wsc " + smoothChange +" "+cause+" "+caused);
					delta2 += smoothChange;
					assert !Double.isNaN(delta2) && delta2 >= 0 : delta2 + " "
							+ change;
				}
			}
		}
		// Util.print("weightSpaceChange " + this);
		// printGraph(null);
		// printGraph(false);
		// Util.print("");
		// reference.printGraph(false);
		// Util.print("weightSpaceChange done\n");
		// double delta = Math.sqrt(delta2);
		return delta2;
	}

	private double weightSpaceChange(Explanation control, boolean fastMax,
			ItemPredicate cause, ItemPredicate caused) {
		// assert cause != caused;

		// Don't normalize, because a single strong predictor will
		// change the proportions, but not the underlying
		// dependency.
		double diffU = Math.abs(predicted.effectiveWeight(cause, caused)
				- control.predicted.effectiveWeight(cause, caused));
		assert !Double.isNaN(diffU) : predicted.effectiveWeight(cause, caused)
				+ " " + control.predicted.effectiveWeight(cause, caused);
		double diffN = Double.POSITIVE_INFINITY;

		if (!fastMax) {
			double weight0Nforward = control.getRNormalizedWeightOrZero(cause,
					caused);
			double weightNforward = getRNormalizedWeightOrZero(cause, caused);
			// Util.print("wsc "+weight0Nforward+" "+weightNforward);
			diffN = Math.abs(weightNforward - weight0Nforward) / 2;
			if (diffN < diffU) {

				double weight0Nbackward = control.getRNormalizedWeightOrZero(
						caused, cause);
				double weightNbackward = getRNormalizedWeightOrZero(caused,
						cause);
				// Util.print("wsc "+weight0N+" "+weightN);

				diffN += Math.abs(weightNbackward - weight0Nbackward) / 2;
			}
		}

		if (Explanation.PRINT_LEVEL >= Explanation.WEIGHTS) {
			printWSC(cause, caused, fastMax, control, diffU, diffN);
		}

		return Math.min(diffU, diffN);
	}

	private void printWSC(ItemPredicate cause, ItemPredicate caused,
			boolean fastMax, Explanation control, double diffU, double diffN) {
		StringBuffer buf = new StringBuffer();
		buf.append("weightSpaceChange ");
		if (diffN < diffU)
			buf.append("N ").append(diffN).append(" (U=").append(diffU).append(
					")");
		else {
			buf.append("U ").append(diffU);
			if (diffN < Double.POSITIVE_INFINITY)
				buf.append(" (N=").append(diffN).append(")");
		}
		buf.append(" ").append(cause).append(" => ").append(caused).append(" ");
		((NonAlchemyModel) control).printW(cause, caused, fastMax, buf).append(
				" => ");
		printW(cause, caused, fastMax, buf).append(" ");
		// if (!fastMax)
		// buf.append(" (").append(diffN).append(")");
		Util.print(buf.toString());
	}

	private StringBuffer printW(ItemPredicate cause, ItemPredicate caused,
			boolean fastMax, StringBuffer buf) {
		double w = predicted.getWeightOrZero(cause, caused);
		if (buf == null)
			buf = new StringBuffer();
		if (NonAlchemyModel.USE_SIGMOID) {
			double expw = predicted.getExpWeightOrZero(cause, caused);
			double sig = expw / (expw + 1);
			buf.append("sigmoid(").append(w).append(") = ").append(sig);
		} else {
			buf.append(w);
		}
		if (!fastMax) {
			double weightN = (getRNormalizedWeightOrZero(cause, caused) + getRNormalizedWeightOrZero(
					caused, cause)) / 2.0;
			buf.append(" (").append(weightN).append(")");
		}
		return buf;
	}

	// private double evaluate(double[] argument, double[] gradient) {
	// // nEvalNgrad++;
	// // Util.print("evalNgrad");
	// // if (PRINT_LEVEL > 2)
	// // Util.print("evaluate " + Util.valueOfDeep(argument));
	// double result = evaluate(argument);
	// computeGradient(gradient);
	// // if (PRINT_LEVEL > 2)
	// // Util.print(" ... eval=" + result + " (KL=" + klDivergence()
	// // + " + BigWeightPenalty=" + predicted.bigWeightPenalty()
	// // + ") " + "\ngrad: " + Util.valueOfDeep(gradient));
	// return result;
	// }

	private double[] getCachedGradient() {
		if (cachedGradient == null) {
			cachedGradient = new double[predicted.getNumEdgesPlusBiases()];
			cachedGradient[0] = Double.NaN;
		}
		return cachedGradient;
	}

	private void computeGradient(double[] gradient) {
		if (!Double.isNaN(getCachedGradient()[0])) {
			System.arraycopy(getCachedGradient(), 0, gradient, 0,
					gradient.length);
			return;
		}
		double[] predictedDistribution = predicted.getDistribution();
		double[] observedDistribution = observed.getDistribution();
		predicted.bigWeightGradient(gradient);

		// Util.print("\n.."+Util.valueOfDeep(predictedDistribution));
		// Util.print(".."+Util.valueOfDeep(observedDistribution));
		// Util.print(".."+Util.valueOfDeep(gradient));

		int[][] sw = predicted.stateWeights();
		int nStates = sw.length;

		// double[][] addends = new double[nWeights][];
		// for (int w = 0; w < nWeights; w++) {
		// addends[w] = new double[nStates * 2 + 1];
		// addends[w][nStates * 2] = gradient[w];
		// }
		for (int state = 0; state < nStates; state++) {
			double q = predictedDistribution[state];
			double p = observedDistribution[state];
			int[] weights = sw[state];
			int nWeights = weights.length;
			for (int w = 0; w < nWeights; w++) {
				gradient[weights[w]] += q - p;
				assert !Double.isNaN(gradient[weights[w]]) : q + " " + p;
				// addends[weights[w]][state * 2] = q;
				// addends[weights[w]][state * 2 + 1] = -p;
			}
		}
		// for (int w = 0; w < nWeights; w++) {
		// gradient[w] = Util.kahanSum(addends[w]);
		// }
		System.arraycopy(gradient, 0, getCachedGradient(), 0, gradient.length);
		// Util.print("cg "+Util.valueOfDeep(gradient)+" "+Util.valueOfDeep(
		// predicted.getWeights()));
	}

	// public void compute2ndGradient(double[] argument, double[] gradient) {
	// setWeights(argument);
	// hessianDiagonal(gradient);
	// }
	//
	// private void hessianDiagonal(double[] gradient) {
	// double denom = predicted.z() * predicted.z();
	// int w = 0;
	// for (int cause = 0; cause < nFacets(); cause++) {
	// for (int caused = cause; caused < nFacets(); caused++) {
	// if (predicted.hasEdge(cause, caused)) {
	// double expEon = 0;
	// double expEoff = 0;
	// int nStates = predicted.nStates();
	// for (int state = 0; state < nStates; state++) {
	// double expE = predicted.expEnergy(state);
	// if (Util.isBit(state, cause)
	// && Util.isBit(state, caused)) {
	// expEon += expE;
	// } else {
	// expEoff += expE;
	// }
	// }
	// gradient[w++] = expEon * expEoff / denom;
	// }
	// }
	// }
	// // Util.print("c2g " + Util.valueOfDeep(gradient));
	// }

	@Override
	public Graph<ItemPredicate> buildGraph(PerspectiveObserver redrawer,
			Perspective popupFacet, boolean debug) {
		List<ItemPredicate> primaryFacets = Explanation.relevantFacets(popupFacet);
		Explanation nullModel = NonAlchemyModel.getInstance(primaryFacets,
				null, null, null);
		return buildGraph(redrawer, nullModel, debug);
	}

	static class CompareCount implements Comparator<ItemPredicate> {

		public int compare(ItemPredicate data1, ItemPredicate data2) {
			int result = Util.sgn(value(data1) - value(data2));
			if (result == 0)
				result = data1.compareTo(data2);// Util.sgn(id(data1)
			// -
			// id(data2));
			return result;
		}

		// private int id(Object data) {
		// return ((Perspective) data).getID();
		// }

		private double value(ItemPredicate data) {
			return data.getTotalCount();
		}
	}

	public static void testPairList(Query query, boolean printToFile) {
		testPairs(pairs, query, printToFile);
	}

	public static void test(Query query, int nCandidates, boolean printToFile) {
		if (nCandidates <= 0)
			return;
		long start = (new Date()).getTime();
		double sumKL = 0;
		int nKL = 0;
		int minCount = 0;
		SortedSet<Perspective> topPerspectives = new TreeSet<Perspective>(new CompareCount());
		for (int i = 1; i <= query.nAttributes; i++) {
			Perspective facetType = query.findPerspective(i);
			for (Iterator<Perspective> it = facetType.getChildIterator(); it.hasNext();) {
				Perspective p = it.next();
				int count = p.getTotalCount();
				if (count > minCount) {
					topPerspectives.add(p);
					if (topPerspectives.size() > nCandidates)
						topPerspectives.remove(topPerspectives.first());
					if (topPerspectives.size() >= nCandidates)
						minCount = topPerspectives.first()
								.getTotalCount();
					// Util.print("tp "+" "+count+" "+p);
				}
			}
		}
		int[] counts = new int[50];
		for (Iterator<Perspective> it = topPerspectives.iterator(); it.hasNext();) {
			Perspective p = it.next();
			Explanation result = null;
			List<ItemPredicate> primaryFacets = Explanation.relevantFacets(p);
			List<ItemPredicate> candidateFacets = Explanation.candidateFacets(primaryFacets,
					true);
			// Util.print(p + " " + p.getTotalCount() + " " + primaryFacets +
			// " "
			// + candidateFacets);
			for (Iterator<ItemPredicate> it2 = candidateFacets.iterator(); it2.hasNext();) {
				ItemPredicate candidate = it2.next();
				List<ItemPredicate> pair = new ArrayList<ItemPredicate>(2);
				pair.add(p);
				pair.add(candidate);
				Collections.sort(pair);
				// Util.print(pair);
				result = getExplanationForFacets(pair);
				if (result != null) {
					int nEdges = result.predicted.nEdges();
					counts[nEdges]++;
					Explanation nullModel = NonAlchemyModel.getInstance(pair,
							null, null, null);
					if (printToFile && nEdges > 1)
						result.printToFile(nullModel);
					boolean isTernary = result.printStats(nullModel);
					if (isTernary) {
						sumKL += result.klDivergence();
						nKL++;
					}
				}
			}
		}
		long duration = (new Date()).getTime() - start;
		// result.printGraphAndNull();
		// ((NonAlchemyModel) result).stats();
		Util.print("test duration=" + (duration / 1000));
		Util.print("nEdges distribution: " + Util.valueOfDeep(counts));
		Util.print("average KL = " + (sumKL / nKL));
	}

	private static void testPairs(int[][] pairs1, Query query,
			boolean printToFile) {
		long start = (new Date()).getTime();
		double sumKL = 0;
		int nKL = 0;
		// int nFns = 0;
		int[] counts = new int[50];
		Util.print("Tag Score" + "\t\t" + "Mut Inf" + "\t\t\t" + "Cond Corr"
				+ "\t\t" + "Weight Change" + "\t\t" + "Non-primary\t\tPrimary");
		for (int i = 0; i <
//		 2
		pairs1.length
		; i++) {
			int[] IDs = pairs1[i];
			List<ItemPredicate> pair = new ArrayList<ItemPredicate>(2);
			Perspective facet1 = query.findPerspectiveNow(IDs[0]);
			Perspective facet2 = query.findPerspectiveNow(IDs[1]);
			pair.add(facet1);
			pair.add(facet2);
			Collections.sort(pair);
			// Util.print(pair);
			// try {
			Explanation result = getExplanationForFacets(pair);
			// nFns += totalNumFuns;
			// nL += totalNumLineSearches;

			int nEdges = result.predicted.nEdges();
			counts[nEdges]++;
			if (nEdges > 1) {
				Explanation nullModel = NonAlchemyModel.getInstance(pair, null,
						null, null);
				if (printToFile) {
					result.printToFile(nullModel);
				}
				boolean isTernary = result.printStats(nullModel);
				if (isTernary) {
					sumKL += result.klDivergence();
					nKL++;
				}
			}
			// } catch (Throwable e) {
			// Util.err("Skipping pair because " + e);
			// }
		}
		long duration = (new Date()).getTime() - start;
		// result.printGraphAndNull();
		// ((NonAlchemyModel) result).stats();
		Util.print("test duration=" + (duration / 1000));
		Util.print("nEdges distribution: " + Util.valueOfDeep(counts));
		Util.print("average KL = " + (sumKL / nKL) + " for " + nKL + "/"
				+ pairs1.length + " ternary cases.");
	}

	public static void testKL(Query q, int nTriples) {
		Set<ItemPredicate> facets = new HashSet<ItemPredicate>();
		for (int i = 0; i < pairs.length; i++) {
			for (int j = 0; j < 2; j++) {
				Perspective f1 = q.findPerspectiveNow(pairs[i][j]);
				facets.add(f1);
			}
		}
		List<ItemPredicate> allFacets = new ArrayList<ItemPredicate>(facets);
		Collections.sort(allFacets);
		// Util.print("nf " + allFacets.size() + " " + allFacets.get(0));
		double sumKL = 0;
		int nKL = 0;
		int max = allFacets.size();
		int candidate = 0;
		int ratio = combinations(max, 3) / nTriples;
		long start = (new Date()).getTime();
		int[] counts = new int[50];
		for (int i = 0; i < max; i++) {
			for (int j = i + 1; j < max; j++) {
				for (int k = j + 1; k < max; k++) {
					if (candidate++ == nKL * ratio) {
						List<ItemPredicate> primary = new ArrayList<ItemPredicate>();
						primary.add(allFacets.get(i));
						primary.add(allFacets.get(j));
						primary.add(allFacets.get(k));
						Explanation result = NonAlchemyModel.getInstance(
								primary, null, null, null);
						int nEdges = result.predicted.nEdges();
						counts[nEdges]++;
						double klDivergence = result.klDivergence();
						sumKL += klDivergence;
						nKL++;
						// Util.print(klDivergence + "\t" + result);
					}
				}
			}
		}
		long duration = (new Date()).getTime() - start;
		// result.printGraphAndNull();
		// ((NonAlchemyModel) result).stats();
		Util.print("test duration=" + (duration / 1000));
		Util.print("nEdges distribution: " + Util.valueOfDeep(counts));
		Util.print("average KL = " + (sumKL / nKL) + " for " + nKL + " cases.");
	}

	private static int combinations(int n, int c) {
		int min = c;
		int max = n - c;
		if (min > max) {
			min = max;
			max = c;
		}
		int result = 1;
		for (int nprime = n; nprime > max; nprime--) {
			result *= nprime;
		}
		for (int nprime = min; nprime > 1; nprime--) {
			result /= nprime;
		}
		// Util.print("combos("+n+","+c+")="+result + " "+min+" "+max);
		return result;
	}

	// SELECT facet1,facet2 FROM loc2.pairs p order by cnt desc limit 100
	@SuppressWarnings("unused")
	private static int[][] pairs2 = { { 129, 302 } };

	// SELECT facet1,facet2 FROM loc2.pairs p order by cnt desc limit 100
	private static int[][] pairs = { { 150, 6380 }, { 286, 6380 },
			{ 150, 24859 }, { 150, 26741 }, { 6380, 24859 }, { 6380, 26741 },
			{ 286, 24859 }, { 286, 26741 }, { 297, 6380 }, { 298, 6380 },
			{ 297, 24859 }, { 297, 26741 }, { 150, 396 }, { 150, 655 },
			{ 396, 655 }, { 286, 396 }, { 286, 655 }, { 298, 396 },
			{ 298, 655 }, { 301, 6380 }, { 301, 24859 }, { 301, 26741 },
			{ 302, 6380 }, { 302, 24859 }, { 302, 26741 }, { 396, 6431 },
			{ 150, 6431 }, { 286, 6431 }, { 655, 6431 }, { 298, 6431 },
			{ 150, 416 }, { 416, 6380 }, { 407, 24859 }, { 407, 26741 },
			{ 407, 6380 }, { 286, 416 }, { 298, 416 }, { 150, 407 },
			{ 286, 407 }, { 297, 407 }, { 301, 407 }, { 302, 407 },
			{ 150, 8464 }, { 286, 8464 }, { 407, 2390 }, { 2390, 6380 },
			{ 2390, 24859 }, { 2390, 26741 }, { 298, 8464 }, { 416, 8464 },
			{ 150, 2390 }, { 286, 2390 }, { 297, 2390 }, { 301, 2390 },
			{ 302, 2390 }, { 150, 6376 }, { 289, 6380 }, { 8514, 24859 },
			{ 8514, 26741 }, { 6380, 26979 }, { 286, 8514 }, { 297, 8514 },
			{ 407, 26979 }, { 301, 8514 }, { 286, 26979 }, { 297, 26979 },
			{ 301, 26979 }, { 302, 26979 }, { 289, 24859 }, { 289, 26741 },
			{ 420, 2846 }, { 420, 24859 }, { 2846, 24859 }, { 420, 6380 },
			{ 2846, 6380 }, { 302, 8514 }, { 420, 26741 }, { 2846, 26741 },
			{ 2390, 26979 }, { 407, 8514 }, { 2390, 8514 }, { 297, 420 },
			{ 297, 2846 }, { 8462, 24859 }, { 420, 8462 }, { 2846, 8462 },
			{ 6380, 23899 }, { 6380, 24405 }, { 8462, 26741 },
			{ 23899, 24859 }, { 23899, 26741 }, { 24405, 24859 },
			{ 24405, 26741 }, { 6376, 24859 }, { 6376, 26741 }, { 298, 6376 },
			{ 297, 8462 }, { 297, 23899 }, { 297, 24405 }, { 298, 414 } };

	public double[] derivativeAt(double[] x) {
		evaluate(x);
		double[] result = new double[x.length];
		computeGradient(result);
		return result;
	}

	public int dimension() {
		return predicted.getNumEdgesPlusBiases();
	}

	public double valueAt(double[] x) {
		return evaluate(x);
	}

}